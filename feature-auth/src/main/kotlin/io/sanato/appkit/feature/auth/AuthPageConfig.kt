package io.sanato.appkit.feature.auth

/**
 * "意图",不是"结果"——屏幕真正显示哪些按钮由
 * [AuthRepository.availableProviders][io.sanato.appkit.core.auth.AuthRepository.availableProviders]
 * 和这份配置求交后得出(比如 GMS 缺失时 Google 按钮即使 `googleEnabled = true`
 * 也不显示)。同 `:feature-settings` 的 `SettingsPageConfig`。
 */
data class AuthPageConfig(
    val emailPasswordEnabled: Boolean = true,
    val googleEnabled: Boolean = true,
    val appleEnabled: Boolean = true,
    val phoneEnabled: Boolean = true,
)
