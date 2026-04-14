// IMonitorCallback.aidl
//
// Server → App event stream. All callbacks are `oneway` (fire-and-forget,
// Binder driver drops the reply). Suitable for state-change pushes, not for
// hot per-frame data — that goes through the shared-memory Snapshot.
package com.cloudorz.openmonitor.server;

interface IMonitorCallback {
    /** Top-activity package changed (event-driven via ITaskStackListener). */
    oneway void onFocusedPackageChanged(String pkg) = 1;

    /** Screen on/off change (event-driven via IDisplayManager.DisplayListener). */
    oneway void onScreenStateChanged(boolean interactive) = 2;

    /** Fatal server-side error; App should decide to restart / fallback. */
    oneway void onError(int code, String msg) = 3;
}
