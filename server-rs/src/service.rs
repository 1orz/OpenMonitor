//! `IMonitorService` server-side implementation.
//!
//! Implements the rsbinder-generated `IMonitorService` trait with the real
//! binder server stub (`BnMonitorService`).

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::sync::Mutex;

use crate::aidl_gen::com::cloudorz::openmonitor::server::{
    IMonitorCallback::IMonitorCallback,
    IMonitorService::{BnMonitorService, IMonitorService},
};
use crate::shm::SharedSnapshot;

const SERVER_VERSION: i32 = 1;

/// Shared handle to the runtime side of the server, passed to collectors so
/// they can notify listeners without having to know about the binder layer.
#[derive(Clone)]
pub struct RuntimeHandle {
    shm: Arc<SharedSnapshot>,
    callbacks: Arc<Mutex<CallbackRegistry>>,
}

impl RuntimeHandle {
    pub fn shm(&self) -> &Arc<SharedSnapshot> {
        &self.shm
    }

    pub fn notify_focus(&self, pkg: &str) {
        log::debug!("focus → {pkg}");
        if let Ok(cbs) = self.callbacks.lock() {
            cbs.dispatch_focus(pkg);
        }
    }

    pub fn notify_screen(&self, interactive: bool) {
        log::debug!("screen interactive={interactive}");
        if let Ok(cbs) = self.callbacks.lock() {
            cbs.dispatch_screen(interactive);
        }
    }
}

pub struct MonitorServiceImpl {
    shm: Arc<SharedSnapshot>,
    callbacks: Arc<Mutex<CallbackRegistry>>,
    exit_flag: Arc<AtomicBool>,
}

impl MonitorServiceImpl {
    pub fn new(shm: Arc<SharedSnapshot>) -> Self {
        Self {
            shm,
            callbacks: Arc::new(Mutex::new(CallbackRegistry::default())),
            exit_flag: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn runtime_handle(&self) -> RuntimeHandle {
        RuntimeHandle {
            shm: self.shm.clone(),
            callbacks: self.callbacks.clone(),
        }
    }

    pub fn exit_flag(&self) -> Arc<AtomicBool> {
        self.exit_flag.clone()
    }

    /// Bind the service to a real rsbinder `BnMonitorService` and return the
    /// strong binder reference.
    pub fn into_binder(self) -> rsbinder::Strong<dyn IMonitorService> {
        BnMonitorService::new_binder(self)
    }
}

impl rsbinder::Interface for MonitorServiceImpl {
    fn as_binder(&self) -> rsbinder::SIBinder {
        // This is the server side — we don't hold our own binder reference.
        // The BnMonitorService wrapper handles this; this is only called if
        // someone unwraps the adapter, which shouldn't happen in practice.
        panic!("MonitorServiceImpl::as_binder called directly — use BnMonitorService wrapper")
    }
}

impl IMonitorService for MonitorServiceImpl {
    fn r#getVersion(&self) -> rsbinder::status::Result<i32> {
        Ok(SERVER_VERSION)
    }

    fn r#getSnapshotMemory(&self) -> rsbinder::status::Result<rsbinder::ParcelFileDescriptor> {
        let dup_fd = self.shm.dup_fd().map_err(|e| {
            log::error!("dup_fd failed: {e}");
            rsbinder::Status::from(rsbinder::StatusCode::Unknown)
        })?;
        Ok(rsbinder::ParcelFileDescriptor::new(dup_fd))
    }

    fn r#registerCallback(
        &self,
        _arg_cb: &rsbinder::Strong<dyn IMonitorCallback>,
    ) -> rsbinder::status::Result<()> {
        if let Ok(mut cbs) = self.callbacks.lock() {
            cbs.register(_arg_cb.clone());
        }
        Ok(())
    }

    fn r#unregisterCallback(
        &self,
        _arg_cb: &rsbinder::Strong<dyn IMonitorCallback>,
    ) -> rsbinder::status::Result<()> {
        if let Ok(mut cbs) = self.callbacks.lock() {
            cbs.unregister(_arg_cb);
        }
        Ok(())
    }

    #[allow(non_snake_case)]
    fn r#setSamplingRate(
        &self,
        _arg_subsystem: &str,
        _arg_intervalMs: i32,
    ) -> rsbinder::status::Result<()> {
        log::info!("setSamplingRate: {_arg_subsystem} → {_arg_intervalMs}ms");
        // TODO: plumb into collectors
        Ok(())
    }

    fn r#setActiveSubsystems(&self, _arg_names: &[String]) -> rsbinder::status::Result<()> {
        log::info!("setActiveSubsystems: {_arg_names:?}");
        // TODO: plumb into collectors
        Ok(())
    }

    fn r#exit(&self) -> rsbinder::status::Result<()> {
        log::info!("exit() called — shutting down");
        self.exit_flag.store(true, Ordering::Release);
        Ok(())
    }
}

/// Set of registered AIDL callbacks.
#[derive(Default)]
struct CallbackRegistry {
    entries: Vec<rsbinder::Strong<dyn IMonitorCallback>>,
}

impl CallbackRegistry {
    fn register(&mut self, cb: rsbinder::Strong<dyn IMonitorCallback>) {
        self.entries.push(cb);
    }

    fn unregister(&mut self, cb: &rsbinder::Strong<dyn IMonitorCallback>) {
        let target = rsbinder::Interface::as_binder(cb.as_ref());
        self.entries
            .retain(|e| rsbinder::Interface::as_binder(e.as_ref()) != target);
    }

    fn dispatch_focus(&self, pkg: &str) {
        for cb in &self.entries {
            if let Err(e) = cb.r#onFocusedPackageChanged(pkg) {
                log::warn!("callback dispatch_focus failed: {e}");
            }
        }
    }

    fn dispatch_screen(&self, interactive: bool) {
        for cb in &self.entries {
            if let Err(e) = cb.r#onScreenStateChanged(interactive) {
                log::warn!("callback dispatch_screen failed: {e}");
            }
        }
    }
}

/// Enter the binder thread pool. Does not return until the process exits.
pub fn join_thread_pool() {
    // On Android, this enters the real binder thread pool.
    // On host (dev/test), park the thread.
    #[cfg(target_os = "android")]
    {
        if let Err(e) = rsbinder::ProcessState::join_thread_pool() {
            log::error!("join_thread_pool failed: {e:?}");
        }
    }
    #[cfg(not(target_os = "android"))]
    {
        log::info!("join_thread_pool: host mode — parking indefinitely");
        loop {
            std::thread::sleep(std::time::Duration::from_secs(3600));
        }
    }
}

/// Initialize and start the binder thread pool.
pub fn init_binder() {
    #[cfg(target_os = "android")]
    {
        // Android binder device path. Standard path for most devices.
        // Some devices use /dev/binderfs/binder, but /dev/binder is the
        // compat symlink present on all API 26+ devices.
        rsbinder::ProcessState::init("/dev/binder", 0);
        rsbinder::ProcessState::start_thread_pool();
    }
    #[cfg(not(target_os = "android"))]
    {
        log::info!("init_binder: host mode — skipping ProcessState init");
    }
}
