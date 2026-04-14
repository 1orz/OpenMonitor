//! Reverse binder publication for the libsu launch path.
//!
//! When the Rust server is exec'd as a bare root process, the App has no
//! direct way to obtain its binder — there's no ServiceConnection, no Shizuku
//! bridge. We borrow the BatteryRecorder trick:
//!
//!   1. Server: ServiceManager.getService("activity") → IActivityManager.
//!   2. Server: am.getContentProviderExternal(
//!         "<applicationId>.binderProvider", user=0, token=gen_uuid()
//!      ) → IContentProvider.
//!   3. Server: provider.call("setBinder", arg=null, extras=Bundle{
//!         "binder" → <our IMonitorService binder>
//!      }).
//!   4. App: BinderProvider.call("setBinder", ...) sees the binder in extras
//!      and hands it to MonitorClient.
//!   5. Server: am.removeContentProviderExternal(...) to release the hold.
//!
//! `getContentProviderExternal` requires SYSTEM / ROOT uid — exactly the uids
//! we've asserted in `main.rs`.

use crate::service::MonitorBinder;

/// Publish the service binder so the App picks it up through BinderProvider.
pub fn publish_to_app(_binder: &MonitorBinder, app_package: Option<&str>) -> std::io::Result<()> {
    let pkg = app_package.unwrap_or("com.cloudorz.openmonitor");
    log::info!("binder_push: pushing to {pkg} via ContentProvider.call");

    // TODO(Phase 6): actual rsbinder wiring.
    //
    // let am: Strong<dyn IActivityManager> =
    //     rsbinder::get_service("activity").ok_or_else(|| ...)?;
    // let token = new_ibinder_token();
    // let holder = am.get_content_provider_external(
    //     &format!("{pkg}.binderProvider"),
    //     /* user */ 0,
    //     &token,
    // )?.ok_or_else(|| ...)?;
    // let provider = holder.provider.ok_or_else(|| ...)?;
    //
    // let mut extras = Bundle::new();
    // extras.put_binder("binder", binder.as_ibinder());
    //
    // provider.call("setBinder", /*arg=*/ None, Some(&extras))?;
    // am.remove_content_provider_external(
    //     &format!("{pkg}.binderProvider"),
    //     &token,
    // )?;

    log::warn!("binder_push::publish_to_app is stubbed — Phase 6 TODO");
    Ok(())
}
