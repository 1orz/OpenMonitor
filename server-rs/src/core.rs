//! Shared server lifecycle: pick socket path → spawn sampler + fps + fg →
//! serve IPC.
//!
//! The old binder-based lifecycle (init rsbinder ProcessState, allocate
//! ashmem, reverse-publish via ContentProvider) is gone. Every launch path
//! (libsu / Shizuku / ADB) now arrives at the same `run_server` entry, which
//! just binds a UDS and serves JSON frames.

use std::path::PathBuf;
use std::sync::atomic::AtomicBool;
use std::sync::Arc;

use crate::auth::AuthConfig;
use crate::ipc;
use crate::snapshot::SnapshotStore;

/// Which path brought us up. Used only for telemetry and auth-mode decisions.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LaunchMode {
    /// libsu exec'd us as root.
    Libsu,
    /// Shizuku ShellUserService exec'd us as shell (uid 2000) or root.
    Shizuku,
    /// Operator ran the binary by hand via `adb shell`. No cert pinning —
    /// anyone on the device can connect (daemon wasn't our call).
    Adb,
}

#[derive(Debug, Clone, Default)]
pub struct ServerConfig {
    /// Directory for the UDS, log files, sentinel path file. App's private
    /// `filesDir` when reachable, otherwise `/data/local/tmp/openmonitor`.
    pub data_dir: Option<PathBuf>,
    /// App package (only set by the launcher; used for future reverse lookup).
    pub app_package: Option<String>,
    /// Free-form `--mode` value; resolved into `LaunchMode` before we get here.
    pub mode_hint: String,
}

/// Runs the server. Returns only on graceful shutdown or fatal error.
pub fn run_server(mode: LaunchMode, config: ServerConfig) -> Result<(), ServerError> {
    log::info!("run_server mode={mode:?} config={config:?}");

    // 1. Resolve the socket path.
    let data_dir = config
        .data_dir
        .clone()
        .unwrap_or_else(|| PathBuf::from("/data/local/tmp/openmonitor"));
    let sock_path = ipc::socket_path_in(&data_dir);

    // 2. Announce the resolved path on stdout's first line so the launcher
    //    (which captures ExecResult.stdout) can connect without guessing.
    println!("openmonitor-server: socket={}", sock_path.display());
    // Also drop a sentinel so a late-arriving client can discover the path.
    if let Err(e) = std::fs::write(data_dir.join("sock.path"), format!("{}\n", sock_path.display())) {
        log::warn!("failed to write sentinel sock.path: {e}");
    }

    // 3. Build shared state.
    let store = SnapshotStore::new();
    let exit_flag: Arc<AtomicBool> = Arc::new(AtomicBool::new(false));

    // 4. Spawn collectors.
    crate::collectors::start_sampler(store.clone(), exit_flag.clone());
    crate::fps::start(store.clone(), exit_flag.clone());
    crate::fg::start(store.clone(), exit_flag.clone());

    // 5. Build auth policy. ADB mode is explicitly permissive (no app to pin).
    let auth = match mode {
        LaunchMode::Adb => AuthConfig::permissive(),
        _ => AuthConfig::from_build(),
    };

    // 6. Serve. Blocks until `exit_flag` trips.
    ipc::serve(&sock_path, store, auth, exit_flag).map_err(ServerError::Ipc)?;

    log::info!("run_server: clean shutdown");
    Ok(())
}

#[derive(Debug)]
pub enum ServerError {
    Ipc(std::io::Error),
}

impl std::error::Error for ServerError {}
impl std::fmt::Display for ServerError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ServerError::Ipc(e) => write!(f, "ipc failed: {e}"),
        }
    }
}
