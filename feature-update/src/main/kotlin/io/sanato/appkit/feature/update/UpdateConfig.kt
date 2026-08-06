package io.sanato.appkit.feature.update

import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton

/** 更新检查的配置来源——`configUrl` 指向消费方自己的更新配置 JSON,schema 见
 * `:updatechecker` 的 `UpdateConfigParser`/`UpdateInfo`。
 */
data class UpdateConfig(
    val configUrl: String,
)

/**
 * fork 出去的项目必须提供这个可选绑定,换成自己的更新配置 JSON 地址——不提供就
 * 停留在下面这个占位地址上(`checkForUpdate()` 不会崩,只会稳定拿到
 * `UpdateResult.Error`,和当前占位行为完全一致):
 *
 * ```
 * @Module @InstallIn(SingletonComponent::class)
 * object MyUpdateConfigOverrideModule {
 *     @Provides fun override() = UpdateConfigOverride("https://raw.githubusercontent.com/OWNER/REPO/main/update.json")
 * }
 * ```
 */
data class UpdateConfigOverride(
    val configUrl: String,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateConfigOverrideModule {
    @BindsOptionalOf
    abstract fun updateConfigOverride(): UpdateConfigOverride
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateConfigModule {
    // 指向"你自己的静态发行仓库"(比如 sanatowhite/version_check 那种轻量应用
    // 商店)——bootstrap.sh 不会自动处理这个值,它猜不到 fork 者的真实发行地址。
    private const val PLACEHOLDER_CONFIG_URL = "https://raw.githubusercontent.com/OWNER/REPO/main/update.json"

    @Provides
    @Singleton
    fun provideUpdateConfig(override: Optional<UpdateConfigOverride>): UpdateConfig =
        UpdateConfig(configUrl = override.map { it.configUrl }.orElse(PLACEHOLDER_CONFIG_URL))
}
