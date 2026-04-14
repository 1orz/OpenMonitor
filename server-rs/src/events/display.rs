//! Screen on/off via `IDisplayManager.DisplayListener`.
//!
//! Replaces polling `IPowerManager.isInteractive`. Listener fires exactly when
//! the default display's state transitions between STATE_ON / STATE_OFF /
//! STATE_DOZE.

use crate::service::RuntimeHandle;

pub fn register(_runtime: RuntimeHandle) -> std::io::Result<()> {
    log::warn!("display::register is stubbed — Phase 4 TODO");
    Ok(())
}
