package com.cloudorz.monitor

import android.app.Application
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MonitorApp : Application() {

    companion object {
        init {
            // Configure libsu Shell.Builder before any Shell usage.
            // This sets up the su binary lookup and shell flags.
            // KernelSU / Magisk / APatch all provide a `su` binary —
            // Shell.Builder will try `su --mount-master` first, then `su`, then `sh`.
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(15) // 15 second timeout for su grant dialog
            )
        }
    }
}
