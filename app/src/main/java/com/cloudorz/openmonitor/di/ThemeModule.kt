package com.cloudorz.openmonitor.di

import com.cloudorz.openmonitor.data.repository.ThemeSettingsRepository
import com.cloudorz.openmonitor.data.repository.ThemeSettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeModule {
    @Binds
    abstract fun bindThemeSettingsRepository(
        impl: ThemeSettingsRepositoryImpl,
    ): ThemeSettingsRepository
}
