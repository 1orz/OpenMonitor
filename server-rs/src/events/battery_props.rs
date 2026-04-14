//! Battery current fallback via `IBatteryPropertiesRegistrar.getProperty`.
//!
//! Used when /sys/class/power_supply/battery/current_now is locked out (e.g.
//! HyperOS tightened SELinux). Precision drops from µA to mA but is still
//! better than parsing dumpsys.
//!
//! Service name resolution (historical quirk):
//!   API ≤ 28: "batteryproperties"
//!   API ≥ 29: may still be "batteryproperties"; "battery_properties" on some
//!             AOSP branches. We look up both at startup, remember the hit.

use crate::service::RuntimeHandle;

#[allow(dead_code)]
pub const BATTERY_PROPERTY_CURRENT_NOW: i32 = 2;

/// Register the fallback. Pulls once at startup to find the service name;
/// subsequent reads go through the cached handle.
pub fn init(_runtime: RuntimeHandle) -> std::io::Result<()> {
    log::warn!("battery_props::init is stubbed — Phase 4 TODO");
    Ok(())
}

/// One-shot query. Returns current in mA, or None if unavailable.
pub fn read_current_ma() -> Option<i32> {
    // TODO(Phase 4):
    //   reg = get_service("batteryproperties") or get_service("battery_properties")
    //   let mut prop = BatteryProperty::default();
    //   reg.get_property(BATTERY_PROPERTY_CURRENT_NOW, &mut prop)?;
    //   Some((prop.value_long as i64 / 1000) as i32)
    None
}
