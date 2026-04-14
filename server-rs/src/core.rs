//! Shared server lifecycle: init → register service → enter binder loop.
//!
//! The two launch paths (libsu binary, Shizuku UserService shim) both arrive
//! here once they've done path-specific setup (UID check / JVM init).

use std::path::PathBuf;
use std::sync::Arc;

use crate::service::MonitorServiceImpl;
use crate::shm::SharedSnapshot;

/// Which path brought us up. Affects how we publish the binder back to the App.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LaunchMode {
    /// libsu exec'd us directly. We reverse-push via ContentProvider.call.
    Libsu,
    /// Shizuku UserService started us through app_process + JVM. Shizuku itself
    /// collects our returned IBinder and forwards it to the App — nothing for
    /// us to push.
    Shizuku,
}

#[derive(Debug, Clone, Default)]
pub struct ServerConfig {
    /// App's private data dir (writable by root/shell), used for log files.
    pub data_dir: Option<PathBuf>,
    /// App package for reverse provider resolution (libsu path).
    pub app_package: Option<String>,
    /// Free-form string set by the launcher, used only for telemetry.
    pub mode_hint: String,
}

/// Runs the server. Returns only on graceful shutdown or fatal error.
pub fn run_server(mode: LaunchMode, config: ServerConfig) -> Result<(), ServerError> {
    log::info!("run_server mode={mode:?} config={config:?}");

    // 1. Allocate the shared-memory Snapshot region.
    let shm = Arc::new(SharedSnapshot::create().map_err(ServerError::Shm)?);

    // 2. Build service impl.
    let service = MonitorServiceImpl::new(shm.clone());

    // 3. Start the sampling threads (collectors loop every 500 ms).
    crate::collectors::start_all(shm.clone(), service.runtime_handle());

    // 4. Register the binder service.
    let binder = service.into_binder().map_err(ServerError::Binder)?;

    // 5. Publish to the App.
    match mode {
        LaunchMode::Libsu => {
            // libsu path: reverse push via ContentProvider.
            crate::binder_push::publish_to_app(&binder, config.app_package.as_deref())
                .map_err(ServerError::Binder)?;
        }
        LaunchMode::Shizuku => {
            // Shizuku does the push for us — the binder is returned from JNI.
            // Nothing to do here.
        }
    }

    // 6. Join binder thread pool. Does not return until exit() is called.
    crate::service::join_thread_pool();

    log::info!("run_server: clean shutdown");
    Ok(())
}

#[derive(Debug)]
pub enum ServerError {
    Shm(std::io::Error),
    Binder(std::io::Error),
}

impl std::error::Error for ServerError {}
impl std::fmt::Display for ServerError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ServerError::Shm(e) => write!(f, "shared memory init failed: {e}"),
            ServerError::Binder(e) => write!(f, "binder init failed: {e}"),
        }
    }
}
