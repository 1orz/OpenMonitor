//! Standalone binary entry point for the libsu launch path.
//!
//! Invocation:
//!   $ /data/.../openmonitor-server --mode root --data-dir /data/.../files
//!
//! The libsu shell exec's this binary running as uid=0 (ROOT) or uid=2000
//! (shell/Shizuku-exec). The binary self-daemonizes, publishes its binder to
//! the App via ContentProvider.call, and enters the binder thread pool.

use std::process::ExitCode;

fn main() -> ExitCode {
    openmonitor_server::logging::init("openmonitor-server");

    let args = parse_args(std::env::args().skip(1));

    // UID whitelist: we only accept exec as root (0) or shell (2000).
    // A normal app process (uid ≥ 10000) has no business running this binary.
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

    match openmonitor_server::core::run_server(openmonitor_server::core::LaunchMode::Libsu, args.into_config()) {
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
    fn into_config(self) -> openmonitor_server::core::ServerConfig {
        openmonitor_server::core::ServerConfig {
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
    Args { mode, data_dir, app_package }
}

/// Minimal POSIX daemonization: fork, setsid, redirect stdio to /dev/null (or
/// $data_dir/server.log once available), chdir to /. We keep stderr open for
/// Rust panics so they land somewhere grep-able.
fn daemonize() -> std::io::Result<()> {
    use nix::unistd::{fork, setsid, ForkResult};

    // First fork: parent exits, child continues.
    match unsafe { fork() }.map_err(io_err)? {
        ForkResult::Parent { .. } => std::process::exit(0),
        ForkResult::Child => {}
    }

    setsid().map_err(io_err)?;

    // Second fork: ensures we can never reacquire a controlling terminal.
    match unsafe { fork() }.map_err(io_err)? {
        ForkResult::Parent { .. } => std::process::exit(0),
        ForkResult::Child => {}
    }

    std::env::set_current_dir("/").ok();

    // Redirect stdin from /dev/null. Keep stdout/stderr pointing at whatever
    // the caller (libsu shell) gave us — logcat via android_log still works
    // independently.
    if let Ok(null) = std::fs::OpenOptions::new().read(true).open("/dev/null") {
        use std::os::unix::io::AsRawFd;
        unsafe { libc::dup2(null.as_raw_fd(), 0); }
    }

    Ok(())
}

fn io_err<E: std::fmt::Display>(e: E) -> std::io::Error {
    std::io::Error::new(std::io::ErrorKind::Other, e.to_string())
}
