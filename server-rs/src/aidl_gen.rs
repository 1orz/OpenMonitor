//! Generated rsbinder stubs from `build.rs` → `rsbinder-aidl`.
//!
//! Provides the IMonitorService and IMonitorCallback traits, plus the
//! BnMonitorService / BpMonitorService / BnMonitorCallback / BpMonitorCallback
//! binder types.
//!
//! Usage:
//!   use crate::aidl_gen::com::cloudorz::openmonitor::server::IMonitorService::*;
//!   use crate::aidl_gen::com::cloudorz::openmonitor::server::IMonitorCallback::*;

include!(concat!(env!("OUT_DIR"), "/rsbinder_generated_aidl.rs"));
