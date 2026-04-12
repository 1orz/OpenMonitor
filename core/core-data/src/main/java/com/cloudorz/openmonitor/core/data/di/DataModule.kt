package com.cloudorz.openmonitor.core.data.di

import com.cloudorz.openmonitor.core.common.CommandResult
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PlatformDetector
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.common.ShellExecutor
import com.cloudorz.openmonitor.core.common.SysfsReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * Provides a delegating [ShellExecutor] that always routes to the current
     * [PermissionManager] mode. This ensures that when the user changes the
     * privilege mode at runtime, all subsequent shell calls use the new executor.
     */
    @Provides
    @Singleton
    fun provideShellExecutor(permissionManager: PermissionManager): ShellExecutor {
        val mutex = Mutex()
        return object : ShellExecutor {
            override val mode: PrivilegeMode
                get() = permissionManager.currentMode.value

            override suspend fun execute(command: String): CommandResult =
                mutex.withLock { permissionManager.getExecutor().execute(command) }

            override suspend fun executeAsRoot(command: String): CommandResult =
                mutex.withLock { permissionManager.getExecutor().executeAsRoot(command) }

            override suspend fun readFile(path: String): String? =
                mutex.withLock { permissionManager.getExecutor().readFile(path) }

            override suspend fun isAvailable(): Boolean =
                permissionManager.getExecutor().isAvailable()
        }
    }

    @Provides
    @Singleton
    fun provideSysfsReader(shellExecutor: ShellExecutor): SysfsReader =
        SysfsReader(shellExecutor)

    @Provides
    @Singleton
    fun providePlatformDetector(sysfsReader: SysfsReader): PlatformDetector =
        PlatformDetector(sysfsReader)
}
