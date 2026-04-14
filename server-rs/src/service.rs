//! `IMonitorService` server-side implementation.
//!
//! This file wires the generated rsbinder stub (`BnIMonitorService`) to our
//! domain logic. The generated stub lives in `src/aidl_gen.rs`; until Phase 0
//! locks down the exact rsbinder-aidl API shape we stub the binder surface
//! with a trait alias so the rest of the crate compiles.
//!
//! TODO(Phase 0): replace `DummyBinder` with the real generated type.

use std::sync::Arc;
use std::sync::Mutex;

use crate::shm::SharedSnapshot;

/// Opaque binder handle we pass around. The libsu and Shizuku paths both turn
/// this into whatever representation rsbinder expects (`Strong<dyn IBinder>`,
/// `SpIBinder`, etc.).
pub struct MonitorBinder {
    inner: Arc<dyn std::any::Any + Send + Sync>,
}

impl MonitorBinder {
    pub fn new(svc: Arc<MonitorServiceImpl>) -> Self {
        Self { inner: svc }
    }

    /// # Safety
    /// Caller must know which concrete type this binder was built from.
    pub unsafe fn as_service(&self) -> Option<&MonitorServiceImpl> {
        self.inner.downcast_ref::<MonitorServiceImpl>()
    }
}

/// Shared handle to the runtime side of the server, passed to collectors so
/// they can notify listeners without having to know about the binder layer.
#[derive(Clone)]
pub struct RuntimeHandle {
    shm: Arc<SharedSnapshot>,
    callbacks: Arc<Mutex<CallbackRegistry>>,
}

impl RuntimeHandle {
    pub fn shm(&self) -> &Arc<SharedSnapshot> { &self.shm }

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
}

impl MonitorServiceImpl {
    pub fn new(shm: Arc<SharedSnapshot>) -> Self {
        Self {
            shm,
            callbacks: Arc::new(Mutex::new(CallbackRegistry::default())),
        }
    }

    pub fn runtime_handle(&self) -> RuntimeHandle {
        RuntimeHandle {
            shm: self.shm.clone(),
            callbacks: self.callbacks.clone(),
        }
    }

    /// Bind the service to a binder object and return it.
    ///
    /// This is where `BnIMonitorService::new_binder(self)` goes once the
    /// generated stub is in place.
    pub fn into_binder(self) -> std::io::Result<MonitorBinder> {
        let arc = Arc::new(self);
        // TODO(Phase 0): wrap in rsbinder BnIMonitorService and return a
        // strong binder. For now we hold the Arc so the SHM stays mapped.
        Ok(MonitorBinder::new(arc))
    }
}

/// Set of registered AIDL callbacks. Each callback is keyed by the binder's
/// stable id so `unregister` can find it again.
#[derive(Default)]
struct CallbackRegistry {
    // TODO(Phase 4): keyed map of IMonitorCallback stubs.
    entries: Vec<DummyCallback>,
}

impl CallbackRegistry {
    fn dispatch_focus(&self, pkg: &str) {
        for cb in &self.entries {
            cb.on_focus(pkg);
        }
    }
    fn dispatch_screen(&self, interactive: bool) {
        for cb in &self.entries {
            cb.on_screen(interactive);
        }
    }
}

struct DummyCallback;
impl DummyCallback {
    fn on_focus(&self, _pkg: &str) {}
    fn on_screen(&self, _interactive: bool) {}
}

/// Enter the binder thread pool. Does not return until `exit()` is called on
/// the service binder.
pub fn join_thread_pool() {
    // TODO(Phase 0): rsbinder::ProcessState::join_thread_pool().
    // For the scaffold, park indefinitely so the binary doesn't exit.
    use std::thread;
    use std::time::Duration;
    loop {
        thread::sleep(Duration::from_secs(3600));
    }
}
