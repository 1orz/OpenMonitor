package com.cloudorz.openmonitor.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for application-wide dependencies.
 * ShellExecutor, SysfsReader, PlatformDetector are provided by DataModule in core-data.
 * PermissionManager is provided via @Inject constructor.
 * Database and DAOs are provided by DatabaseModule in core-database.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
