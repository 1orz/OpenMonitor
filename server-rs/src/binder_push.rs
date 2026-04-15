//! Reverse binder publication for the libsu launch path.
//!
//! When the Rust server is exec'd as a bare root process, the App has no
//! direct way to obtain its binder — there's no ServiceConnection, no Shizuku
//! bridge.
//!
//! **Strategy 1 (preferred): ServiceManager**
//! Register the server binder in ServiceManager under a well-known name.
//! The App polls `ServiceManager.getService("openmonitor.server")` after
//! launching the root binary. This requires root/system UID (which we have)
//! and an appropriate SELinux context.
//!
//! **Strategy 2 (fallback): ContentProvider.call**
//! If ServiceManager registration fails (e.g. SELinux), fall back to
//! `IActivityManager.getContentProviderExternal()` → `provider.call(setBinder)`.
//! This requires hidden AIDL stubs from Phase 4.

use crate::aidl_gen::com::cloudorz::openmonitor::server::IMonitorService::IMonitorService;

/// Well-known service name in ServiceManager for the App to discover.
pub const SERVICE_NAME: &str = "openmonitor.server";

/// Publish the service binder so the App can discover it.
///
/// Tries ServiceManager.addService first (zero AIDL dependency).
/// Falls back to ContentProvider push if available.
pub fn publish_to_app(
    binder: &rsbinder::Strong<dyn IMonitorService>,
    _app_package: Option<&str>,
) -> std::io::Result<()> {
    let service_binder = rsbinder::Interface::as_binder(binder.as_ref());

    // Strategy 1: Register in ServiceManager.
    #[cfg(target_os = "android")]
    {
        match rsbinder::hub::add_service(SERVICE_NAME, service_binder.clone()) {
            Ok(()) => {
                log::info!("binder_push: registered as '{SERVICE_NAME}' in ServiceManager");
                return Ok(());
            }
            Err(e) => {
                // May fail due to SELinux policy on some vendor ROMs.
                log::warn!(
                    "binder_push: ServiceManager.addService failed: {e:?} — \
                     App will need to use Shizuku path or Phase 4 ContentProvider fallback"
                );
            }
        }
    }

    // Strategy 2: ContentProvider.call via IActivityManager (requires Phase 4 AIDL).
    // TODO(Phase 4/6): implement once IActivityManager stubs are generated.
    //
    // let am = rsbinder::hub::get_service("activity").ok_or_else(|| ...)?;
    // let holder = am.get_content_provider_external(
    //     &format!("{}.binderProvider", pkg), 0, &token,
    // )?;
    // holder.provider.call("setBinder", null, Bundle { "binder" = service_binder })?;
    // am.remove_content_provider_external(...)?;

    #[cfg(not(target_os = "android"))]
    {
        let _ = service_binder;
        log::info!("binder_push: host mode — skipping publication");
    }

    Ok(())
}
