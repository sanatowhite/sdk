package io.sanato.apptemplate.navigation

import kotlinx.serialization.Serializable

// Navigation Compose 类型安全路由(2.9.8):`@Serializable data object` + `composable<T>`。
// 明确不用 Navigation3——另一套心智模型且非稳定。
@Serializable
data object Home

@Serializable
data object Settings

@Serializable
data object About

@Serializable
data object Licenses

@Serializable
data object PrivacyPolicy

@Serializable
data object TermsOfService

@Serializable
data object Consent
