package com.cloudorz.openmonitor.core.common

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RootExecutor @Inject constructor() : ShellExecutor {

    companion object {
        private const val TAG = "RootExecutor"
    }

    override val mode: PrivilegeMode = PrivilegeMode.ROOT

    override suspend fun execute(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell() // blocks, triggers KernelSU grant dialog if needed
            if (!shell.isRoot) {
                return@withContext CommandResult.failure("Root shell not available")
            }
            val result = Shell.cmd(command).exec()
            CommandResult(
                exitCode = result.code,
                stdout = result.out.joinToString("\n"),
                stderr = result.err.joinToString("\n"),
            )
        } catch (e: Exception) {
            CommandResult.failure("Root shell execution failed: ${e.message}")
        }
    }

    override suspend fun executeAsRoot(command: String): CommandResult = execute(command)

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) return@withContext null
            val result = Shell.cmd("cat '$path'").exec()
            if (result.code == 0) result.out.joinToString("\n") else null
        } catch (e: Exception) {
            Log.d(TAG, "readFile failed: $path", e)
            null
        }
    }

    /**
     * Check if root is available by requesting a root shell via libsu.
     *
     * Shell.getShell() is a BLOCKING call that:
     * 1. Tries `su --mount-master` (Magisk mount namespace isolation)
     * 2. Falls back to `su` (KernelSU / APatch / classic root)
     * 3. Falls back to `sh` (non-root shell)
     *
     * For KernelSU, this triggers the grant dialog in KernelSU manager.
     * The user must have already allowed this app in KernelSU manager,
     * or the dialog will appear and block until the user responds.
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell() // This is the key call — triggers su binary
            shell.isRoot
        } catch (e: Exception) {
            Log.d(TAG, "isAvailable check failed", e)
            false
        }
    }
}
