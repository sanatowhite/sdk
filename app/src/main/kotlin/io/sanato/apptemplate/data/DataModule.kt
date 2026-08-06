package io.sanato.apptemplate.data

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sanato.apptemplate.core.data.DataStoreUserSettingsRepository
import io.sanato.apptemplate.core.data.UserSettingsRepository
import io.sanato.apptemplate.core.data.userSettingsDataStore
import javax.inject.Singleton

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
