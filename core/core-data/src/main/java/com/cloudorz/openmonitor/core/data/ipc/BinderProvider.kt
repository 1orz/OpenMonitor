package com.cloudorz.openmonitor.core.data.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import com.elvishew.xlog.XLog
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Reverse binder bridge for the libsu launch path.
 *
 * The Rust server (running as uid 0) calls
 * `ActivityManager.getContentProviderExternal(authority)` → `provider.call(...)`
 * with the server's IBinder tucked inside the extras Bundle. The Binder driver
 * transports the IBinder reference across process boundaries transparently;
 * on this side we just pull it back out and hand it to [MonitorClient].
 *
 * Security:
 *  - Only uid 0 (root) or 2000 (shell/Shizuku-exec) are allowed to push a
 *    binder. Any other caller gets a silent reject.
 *  - `setBinder` is the only method this provider honors; `query`/`insert`/
 *    `update`/`delete` are all stubbed.
 */
class BinderProvider : ContentProvider() {

    companion object {
        private const val TAG = "BinderProvider"
        const val METHOD_SET_BINDER = "setBinder"
        const val EXTRA_BINDER = "binder"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BinderProviderEntryPoint {
        fun monitorClient(): MonitorClient
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_SET_BINDER) return null

        val uid = Binder.getCallingUid()
        if (uid != 0 && uid != Process.SHELL_UID) {
            XLog.tag(TAG).e("rejecting setBinder from uid=$uid")
            return null
        }

        val binder: IBinder? = extras?.getBinder(EXTRA_BINDER)
        if (binder == null) {
            XLog.tag(TAG).e("setBinder: no '$EXTRA_BINDER' in extras")
            return null
        }

        val ctx = context ?: return null
        val ep = EntryPoints.get(ctx.applicationContext, BinderProviderEntryPoint::class.java)
        ep.monitorClient().onBinderReceived(binder)
        XLog.tag(TAG).i("received server binder from uid=$uid")
        return Bundle.EMPTY
    }

    // --- unused ContentProvider surface ---
    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?,
    ): Int = 0
}
