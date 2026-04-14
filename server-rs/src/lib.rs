//! openmonitor-server library surface.
//!
//! This crate has two roles:
//!  1. **bin target** (`src/main.rs`): standalone executable for the libsu
//!     launch path — Rust binary exec'd by root shell.
//!  2. **cdylib target** (this file): shared library for the Shizuku launch
//!     path — Kotlin `RustEntry` shim calls `System.loadLibrary("openmonitor_server")`
//!     and then `nativeMain()` / `nativeGetBinder()` via JNI.
//!
//! Both paths converge on `core::run_server()` after launch-specific setup.

pub mod aidl_gen;
pub mod collectors;
pub mod core;
pub mod events;
pub mod logging;
pub mod service;
pub mod shm;

// Public JNI entry points, used only by the Shizuku shim.
#[cfg(target_os = "android")]
pub mod jni_entry;

// Reverse content-provider binder push, used only by the libsu path.
pub mod binder_push;
