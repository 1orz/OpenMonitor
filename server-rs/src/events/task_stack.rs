//! Foreground package tracking via `ITaskStackListener`.
//!
//! Replaces: two `dumpsys activity activities` forks per second in the Go
//! daemon. This listener is push-based — `on_task_moved_to_front` fires
//! immediately when the user switches app.
//!
//! Registration path (Phase 4):
//!   1. ServiceManager.getService("activity_task") → IActivityTaskManager binder
//!   2. Construct BnITaskStackListener wrapping `FocusTracker`.
//!   3. atm.register_task_stack_listener(listener).
//!
//! When fired:
//!   - Write pkg into shared `Mutex<String>` (cheap).
//!   - Push `IMonitorCallback.onFocusedPackageChanged(pkg)` to each registered
//!     App-side callback.

use std::sync::{Arc, Mutex};

use crate::service::RuntimeHandle;

#[allow(dead_code)]
pub struct FocusTracker {
    focused: Arc<Mutex<String>>,
    runtime: RuntimeHandle,
}

impl FocusTracker {
    pub fn new(runtime: RuntimeHandle, focused: Arc<Mutex<String>>) -> Self {
        Self { focused, runtime }
    }

    pub fn on_top_activity(&self, pkg: &str) {
        if let Ok(mut g) = self.focused.lock() {
            if g.as_str() == pkg { return; }
            *g = pkg.to_string();
        }
        self.runtime.notify_focus(pkg);
    }
}

/// Register with system_server. TODO(Phase 4): implement via generated stubs.
pub fn register(_runtime: RuntimeHandle, _focused: Arc<Mutex<String>>) -> std::io::Result<()> {
    log::warn!("task_stack::register is stubbed — Phase 4 TODO");
    Ok(())
}
