package io.sanato.appkit.core.data.hilt

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sanato.appkit.core.data.DataStoreUserSettingsRepository
import io.sanato.appkit.core.data.UserSettingsRepository
import io.sanato.appkit.core.data.userSettingsDataStore
import javax.inject.Singleton

/**
 * 默认把 `UserSettingsRepository` 绑定到 DataStore 实现——消费方想换自己的
 * 存储，用 Gradle `exclude(group = "com.github.sanatowhite.sdk", module =
 * "core-data-hilt")` 排掉这个模块，再自己写一条 `@Binds` 就行，`:core-data`
 * 本身只暴露接口，不绑死这个实现。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideUserSettingsDataStore(application: Application): DataStore<Preferences> =
        application.userSettingsDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindUserSettingsRepository(impl: DataStoreUserSettingsRepository): UserSettingsRepository
}
