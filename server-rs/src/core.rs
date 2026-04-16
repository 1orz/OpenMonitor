//! Shared server lifecycle: spawn sampler + fps → serve TCP.
//! TCP `bind()` on the fixed port acts as a natural singleton guard.

use std::sync::atomic::AtomicBool;
use std::sync::Arc;

use crate::ipc;
use crate::snapshot::SnapshotStore;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LaunchMode {
    Libsu,
    Shizuku,
    Adb,
}

#[derive(Debug, Clone, Default)]
pub struct ServerConfig {
    pub data_dir: Option<std::path::PathBuf>,
    pub app_package: Option<String>,
    pub mode_hint: String,
}

pub fn run_server(mode: LaunchMode, config: ServerConfig) -> Result<(), ServerError> {
    log::info!("run_server mode={mode:?} config={config:?}");

    let store = SnapshotStore::new();
    let exit_flag: Arc<AtomicBool> = Arc::new(AtomicBool::new(false));

    crate::collectors::start_sampler(store.clone(), exit_flag.clone());
    crate::fps::start(store.clone(), exit_flag.clone());

    let port = ipc::DEFAULT_PORT;
    println!("openmonitor-server: tcp=127.0.0.1:{port}");

    ipc::serve(port, store, exit_flag).map_err(ServerError::Ipc)?;

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
