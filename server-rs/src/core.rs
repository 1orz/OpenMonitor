//! Shared server lifecycle: init → register service → enter binder loop.
//!
//! The two launch paths (libsu binary, Shizuku UserService shim) both arrive
//! here once they've done path-specific setup (UID check / JVM init).

use std::path::PathBuf;
use std::sync::Arc;

use crate::aidl_gen::com::cloudorz::openmonitor::server::IMonitorService::IMonitorService;
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

    // 1. Initialize the binder subsystem.
    crate::service::init_binder();

    // 2. Allocate the shared-memory Snapshot region.
    //    Libsu: file-backed shm so the app can mmap directly (avoids SELinux
    //           service_manager restrictions that block ServiceManager discovery).
    //    Shizuku: ashmem transferred via binder ParcelFileDescriptor.
    //
    //    The launch_mode value is written into the header so the app can
    //    show "started via root/shizuku/adb" without any side channel.
    let launch_mode_id = resolve_launch_mode(mode, &config);
    let shm = Arc::new(match mode {
        LaunchMode::Libsu => {
            let dir = config.data_dir.as_ref().ok_or_else(|| {
                ServerError::Shm(std::io::Error::new(
                    std::io::ErrorKind::InvalidInput,
                    "data_dir required for libsu mode",
                ))
            })?;
            SharedSnapshot::create_file(&dir.join("server/snapshot.shm"), launch_mode_id)
                .map_err(ServerError::Shm)?
        }
        LaunchMode::Shizuku => SharedSnapshot::create(launch_mode_id).map_err(ServerError::Shm)?,
    });

    // 3. Build service impl.
    let service = MonitorServiceImpl::new(shm.clone());
    let exit_flag = service.exit_flag();

    // 4. Start the sampling threads (collectors loop every 500 ms).
    crate::collectors::start_all(shm.clone(), service.runtime_handle());

    // 5. Create the binder service object.
    let binder: rsbinder::Strong<dyn IMonitorService> = service.into_binder();

    // 6. Publish to the App.
    match mode {
        LaunchMode::Libsu => {
            // libsu path: reverse push via ContentProvider.
            crate::binder_push::publish_to_app(&binder, config.app_package.as_deref())
                .map_err(ServerError::Binder)?;
        }
        LaunchMode::Shizuku => {
            // Shizuku does the push for us — the binder is returned from JNI.
            // Stash it for nativeGetBinder().
            #[cfg(target_os = "android")]
            crate::jni_entry::store_binder(binder.clone());
        }
    }

    // Keep the binder alive by holding the Strong reference.
    let _keep_alive = binder;

    // 7. Join binder thread pool. Does not return until exit() is called.
    crate::service::join_thread_pool();

    if exit_flag.load(std::sync::atomic::Ordering::Acquire) {
        log::info!("run_server: exit() was called — clean shutdown");
    }
    Ok(())
}

/// Map the internal `LaunchMode` + `mode_hint` to the on-wire `launch_mode`
/// value stored in the shm header. We distinguish ADB from ROOT by the
/// `--mode` cli arg so the app can tell whether the server was started via
/// libsu (root) or by the user running the binary under `adb shell`.
fn resolve_launch_mode(mode: LaunchMode, config: &ServerConfig) -> u32 {
    match mode {
        LaunchMode::Shizuku => crate::shm::LAUNCH_MODE_SHIZUKU,
        LaunchMode::Libsu => match config.mode_hint.as_str() {
            "adb" | "shell" => crate::shm::LAUNCH_MODE_ADB,
            "shizuku" => crate::shm::LAUNCH_MODE_SHIZUKU,
            "root" => crate::shm::LAUNCH_MODE_LIBSU_ROOT,
            _ => {
                // Fall back to uid heuristic: 0 = root, otherwise treat as adb.
                let uid = unsafe { libc::getuid() };
                if uid == 0 {
                    crate::shm::LAUNCH_MODE_LIBSU_ROOT
                } else {
                    crate::shm::LAUNCH_MODE_ADB
                }
            }
        },
    }
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
