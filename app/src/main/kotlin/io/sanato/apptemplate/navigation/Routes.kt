package io.sanato.apptemplate.navigation

import kotlinx.serialization.Serializable

// Navigation Compose 类型安全路由(2.9.8):`@Serializable data object` + `composable<T>`。
// 明确不用 Navigation3——另一套心智模型且非稳定。
//
// Settings/About/PrivacyPolicy/TermsOfService/Consent 在 :feature-settings,
// Feedback 在 :feature-feedback,Licenses 在 :feature-licenses——三个模块各自
// 带自己的路由类型 + NavGraphBuilder 扩展函数(settingsGraph/feedbackGraph/
// licensesGraph),这里只留 :app 自己的 Home。
@Serializable
data object Home
