package com.cloudorz.monitor.core.data.di

import com.cloudorz.monitor.core.common.CommandResult
import com.cloudorz.monitor.core.common.PermissionManager
import com.cloudorz.monitor.core.common.PlatformDetector
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.common.ShellExecutor
import com.cloudorz.monitor.core.common.SysfsReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    fun provideShellExecutor(permissionManager: PermissionManager): ShellExecutor =
        object : ShellExecutor {
            override val mode: PrivilegeMode
                get() = permissionManager.currentMode.value

            override suspend fun execute(command: String): CommandResult =
                permissionManager.getExecutor().execute(command)

            override suspend fun executeAsRoot(command: String): CommandResult =
                permissionManager.getExecutor().executeAsRoot(command)

            override suspend fun readFile(path: String): String? =
                permissionManager.getExecutor().readFile(path)

            override suspend fun writeFile(path: String, value: String): Boolean =
                permissionManager.getExecutor().writeFile(path, value)

            override suspend fun isAvailable(): Boolean =
                permissionManager.getExecutor().isAvailable()
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
