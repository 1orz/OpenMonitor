//! openmonitor-server library surface.
//!
//! The crate is still exposed as a library so host-side unit tests can drive
//! the frame codec and (once landed) APK signature parser without touching
//! Android.
//!
//! Runtime entry is `core::run_server`.

pub mod apk_sign;
pub mod auth;
pub mod collectors;
pub mod core;
pub mod fg;
pub mod fps;
pub mod ipc;
pub mod logging;
pub mod snapshot;
pub mod subproc;
