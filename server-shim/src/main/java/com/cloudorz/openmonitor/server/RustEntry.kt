package com.cloudorz.openmonitor.server

import android.os.IBinder

/**
 * Shizuku UserService entry point. This is the class name passed to
 * [rikka.shizuku.Shizuku.addUserService] — Shizuku's server process will
 * exec `app_process` targeting our APK, load this class, and invoke [main].
 *
 * All we do here is bootstrap the Rust server:
 *  1. [System.loadLibrary] drags `libopenmonitor_server.so` (built by
 *     `:server-rs`) into this JVM.
 *  2. [nativeMain] transfers control to `core::run_server` — it never
 *     returns under normal operation (it joins the binder thread pool).
 *  3. [nativeGetBinder] is what Shizuku pulls when routing the service
 *     IBinder back to the App process.
 *
 * The JVM baseline (~20 MB) is the price of Shizuku's API contract. If the
 * user can get libsu going, that path spawns a pure Rust process with no JVM.
 */
class RustEntry : IBinder by nativeGetBinder() {

    companion object {
        init {
            System.loadLibrary("openmonitor_server")
        }

        @JvmStatic
        external fun nativeMain()

        @JvmStatic
        external fun nativeGetBinder(): IBinder

        @JvmStatic
        fun main(args: Array<String>) {
            // Shizuku's UserService entrypoint. Does not return.
            nativeMain()
        }
    }
}
