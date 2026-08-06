package io.sanato.appkit.feature.settings

import kotlinx.serialization.Serializable

// Navigation Compose 类型安全路由:`@Serializable data object` + `composable<T>`。
// 消费方用 `navController.navigate(SettingsRoute)` 跳转。
@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

@Serializable
data object PrivacyPolicyRoute

@Serializable
data object TermsOfServiceRoute

@Serializable
data object ConsentRoute
