package io.sanato.appkit.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val USER_SETTINGS_DATASTORE_NAME = "user_settings"

/**
 * 顶层 `val` + `preferencesDataStore` 委托——保证每个进程只有一个 DataStore 实例。
 * `:app` 的 Hilt Module 里 `@Provides fun DataStore<Preferences> = context.userSettingsDataStore`
 * 就够了,不需要在这里再手写单例管理。
 */
val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_SETTINGS_DATASTORE_NAME,
)
