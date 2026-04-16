//! Standalone daemon binary. One of three launchers invokes it:
//!
//!   root via libsu    :  su -c "$bin --mode root    --data-dir $filesDir --app-package $pkg"
//!   Shizuku exec      :  shell -c "$bin --mode shizuku --data-dir $filesDir --app-package $pkg"
//!   operator via adb  :  adb shell $bin --mode adb
//!
//! The binary double-forks into the background, binds a UDS under
//! `--data-dir/openmonitor.sock`, and serves length-prefixed JSON frames.

use std::process::ExitCode;

use openmonitor_server::core::{LaunchMode, ServerConfig};

fn main() -> ExitCode {
    openmonitor_server::logging::init("openmonitor-server");

    let args = parse_args(std::env::args().skip(1));

    // UID whitelist: root (0) and shell (2000) are the only allowed launchers.
    let uid = unsafe { libc::getuid() };
    if uid != 0 && uid != 2000 {
        eprintln!("openmonitor-server: refused — uid {uid} not in [0, 2000]");
        return ExitCode::from(2);
    }

    log::info!("openmonitor-server starting: uid={uid} data_dir={:?}", args.data_dir);

    if let Err(e) = daemonize() {
        log::error!("daemonize failed: {e}");
        return ExitCode::from(3);
    }

    match openmonitor_server::core::run_server(args.launch_mode(), args.into_config()) {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            log::error!("server exited with error: {e}");
            ExitCode::from(1)
        }
    }
}

struct Args {
    mode: String,
    data_dir: Option<std::path::PathBuf>,
    app_package: Option<String>,
}

impl Args {
    fn launch_mode(&self) -> LaunchMode {
        match self.mode.as_str() {
            "root" | "libsu" => LaunchMode::Libsu,
            "shizuku" | "shell" => LaunchMode::Shizuku,
            "adb" => LaunchMode::Adb,
            _ => {
                // Uid-based fallback: root → Libsu, shell → Adb.
                let uid = unsafe { libc::getuid() };
                if uid == 0 { LaunchMode::Libsu } else { LaunchMode::Adb }
            }
        }
    }

    fn into_config(self) -> ServerConfig {
        ServerConfig {
            data_dir: self.data_dir,
            app_package: self.app_package,
            mode_hint: self.mode,
        }
    }
}

fn parse_args<I: Iterator<Item = String>>(mut args: I) -> Args {
    let mut mode = "root".to_string();
    let mut data_dir: Option<std::path::PathBuf> = None;
    let mut app_package: Option<String> = None;

    while let Some(a) = args.next() {
        match a.as_str() {
            "--mode" => if let Some(v) = args.next() { mode = v; },
            "--data-dir" => if let Some(v) = args.next() { data_dir = Some(v.into()); },
            "--app-package" => if let Some(v) = args.next() { app_package = Some(v); },
            _ => eprintln!("openmonitor-server: ignoring unknown arg {a}"),
        }
    }

    // Shell-launched modes default data_dir to /data/local/tmp/openmonitor —
    // the app's filesDir isn't writable by uid 2000.
    if data_dir.is_none() && (mode == "adb" || mode == "shell" || mode == "shizuku") {
        data_dir = Some(std::path::PathBuf::from("/data/local/tmp/openmonitor"));
    }

    Args { mode, data_dir, app_package }
}

/// POSIX double-fork daemonize. Keeps stderr open so Rust panics still land
/// somewhere grep-able (logcat covers the normal log path via `logging::init`).
fn daemonize() -> std::io::Result<()> {
    use nix::unistd::{fork, setsid, ForkResult};

    match unsafe { fork() }.map_err(io_err)? {
        ForkResult::Parent { .. } => std::process::exit(0),
        ForkResult::Child => {}
    }

    setsid().map_err(io_err)?;

    match unsafe { fork() }.map_err(io_err)? {
        ForkResult::Parent { .. } => std::process::exit(0),
        ForkResult::Child => {}
    }

    std::env::set_current_dir("/").ok();

    if let Ok(null) = std::fs::OpenOptions::new().read(true).open("/dev/null") {
        use std::os::unix::io::AsRawFd;
        unsafe { libc::dup2(null.as_raw_fd(), 0); }
    }

    Ok(())
}

fn io_err<E: std::fmt::Display>(e: E) -> std::io::Error {
    std::io::Error::other(e.to_string())
}
