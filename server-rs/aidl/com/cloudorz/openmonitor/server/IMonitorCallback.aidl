// Mirror of core-server-api/src/main/aidl/com/cloudorz/openmonitor/server/IMonitorCallback.aidl
// KEEP IN SYNC.
package com.cloudorz.openmonitor.server;

interface IMonitorCallback {
    oneway void onFocusedPackageChanged(String pkg) = 1;
    oneway void onScreenStateChanged(boolean interactive) = 2;
    oneway void onError(int code, String msg) = 3;
}
