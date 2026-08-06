package io.sanato.appkit.smoke

import io.sanato.appkit.core.data.ThemeMode
import io.sanato.appkit.core.data.UserSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * `UserSettingsRepository` 是接口(`:core-data`),`DataStoreUserSettingsRepository`
 * 的 `@Binds` 绑定在 `:core-data-hilt`——注入到这里就是验证这条绑定链在发布出去
 * 的坐标上也成立,不需要消费方自己写任何 `@Module`。
 */
class DataSmoke
    @Inject
    constructor(
        repository: UserSettingsRepository,
    ) {
        val themeMode: Flow<ThemeMode> = repository.settings.map { it.themeMode }
    }
