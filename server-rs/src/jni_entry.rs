//! JNI entry points for the Shizuku UserService launch path.
//!
//! The Kotlin shim `RustEntry` does:
//!
//!   init { System.loadLibrary("openmonitor_server") }
//!   @JvmStatic external fun nativeMain()
//!   @JvmStatic external fun nativeGetBinder(): IBinder
//!
//! Our job here is to:
//!   - Implement `nativeMain()` so control can land in `core::run_server`.
//!   - Implement `nativeGetBinder()` so Shizuku can forward the binder back
//!     to the App process (via its own ContentProvider bridge).
//!
//! These are `#[no_mangle] pub extern "C"` functions with the JNI-mangled
//! symbol names the JVM's `System.loadLibrary` lookup will resolve.

use std::ffi::c_void;
use std::sync::Mutex;

use crate::core::{LaunchMode, ServerConfig};
use crate::service::MonitorBinder;

// JNI types we care about. Kept minimal so we don't pull in the `jni` crate
// for this single file — only raw pointer handles cross the boundary.
pub type JNIEnv = c_void;
pub type JClass = *mut c_void;
pub type JObject = *mut c_void;

static BINDER: Mutex<Option<MonitorBinder>> = Mutex::new(None);

/// Shizuku calls `RustEntry.main(args)` which calls `nativeMain()`. We run
/// the server on this thread — it's fine to hold it since Shizuku's
/// UserService is its own process.
///
/// Symbol: `Java_com_cloudorz_openmonitor_server_RustEntry_nativeMain`
#[no_mangle]
pub extern "C" fn Java_com_cloudorz_openmonitor_server_RustEntry_nativeMain(
    _env: *mut JNIEnv,
    _cls: JClass,
) {
    crate::logging::init("openmonitor-server");
    log::info!("nativeMain: entering Rust server");

    let cfg = ServerConfig {
        mode_hint: "shizuku".into(),
        ..Default::default()
    };

    if let Err(e) = crate::core::run_server(LaunchMode::Shizuku, cfg) {
        log::error!("run_server failed: {e}");
    }
}

/// After `nativeMain` enters the binder loop on another thread (or we build
/// the binder eagerly), the Kotlin shim queries us for the IBinder to return
/// from `IBinder by nativeGetBinder()`.
///
/// Symbol: `Java_com_cloudorz_openmonitor_server_RustEntry_nativeGetBinder`
#[no_mangle]
pub extern "C" fn Java_com_cloudorz_openmonitor_server_RustEntry_nativeGetBinder(
    _env: *mut JNIEnv,
    _cls: JClass,
) -> JObject {
    // TODO(Phase 1): return a jobject wrapping the rsbinder AIBinder*.
    // Requires JNI env to call `AIBinder_toJavaBinder(env, binder)`.
    std::ptr::null_mut()
}

/// Stash the binder so `nativeGetBinder` can hand it back.
#[allow(dead_code)]
pub(crate) fn store_binder(binder: MonitorBinder) {
    if let Ok(mut slot) = BINDER.lock() {
        *slot = Some(binder);
    }
}
