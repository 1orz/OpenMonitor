//! Event-driven subscriptions to system services, via hidden AIDL (rsbinder
//! generated stubs living in `crate::aidl_gen`).
//!
//! Each submodule registers exactly one listener with exactly one service.
//! On event we update the collector-visible shared state AND push the event
//! out to registered IMonitorCallback clients (oneway).
//!
//! Zero polling — these replace the old `dumpsys activity` / `dumpsys display`
//! forks on the data path.

pub mod battery_props;
pub mod display;
pub mod sf_dump;
pub mod task_stack;
