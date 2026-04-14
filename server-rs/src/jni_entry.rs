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

use std::ffi::c_void;
use std::sync::Mutex;

use crate::aidl_gen::com::cloudorz::openmonitor::server::IMonitorService::IMonitorService;
use crate::core::{LaunchMode, ServerConfig};

pub type JNIEnv = c_void;
pub type JClass = *mut c_void;
pub type JObject = *mut c_void;

static BINDER: Mutex<Option<rsbinder::Strong<dyn IMonitorService>>> = Mutex::new(None);

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
    //
    // Once rsbinder exposes the raw AIBinder pointer, this becomes:
    //   let binder = BINDER.lock().ok()?.as_ref()?.as_binder();
    //   let raw = binder.as_raw();  // *mut AIBinder
    //   let env = JNIEnv::from_raw(_env);
    //   env.call_static_method("android/os/Binder", "toJavaBinder", ...)
    std::ptr::null_mut()
}

/// Stash the binder so `nativeGetBinder` can hand it back.
pub(crate) fn store_binder(binder: rsbinder::Strong<dyn IMonitorService>) {
    if let Ok(mut slot) = BINDER.lock() {
        *slot = Some(binder);
    }
}
