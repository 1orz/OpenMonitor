// Mirror of core-server-api/src/main/aidl/com/cloudorz/openmonitor/server/IMonitorService.aidl
// KEEP IN SYNC. Transaction codes are pinned with `= N` for cross-language compat.
package com.cloudorz.openmonitor.server;

import android.os.ParcelFileDescriptor;
import com.cloudorz.openmonitor.server.IMonitorCallback;

interface IMonitorService {
    int getVersion() = 1;
    ParcelFileDescriptor getSnapshotMemory() = 2;
    void registerCallback(IMonitorCallback cb) = 3;
    void unregisterCallback(IMonitorCallback cb) = 4;
    void setSamplingRate(String subsystem, int intervalMs) = 5;
    oneway void setActiveSubsystems(in String[] names) = 6;
    void exit() = 100;
}
