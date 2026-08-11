package io.sanato.apptemplate.navigation

import kotlinx.serialization.Serializable

// Navigation Compose 类型安全路由(2.9.8):`@Serializable data object` + `composable<T>`。
// 明确不用 Navigation3——另一套心智模型且非稳定。
//
// Settings/About/PrivacyPolicy/TermsOfService/Consent 在 :feature-settings,
// Feedback 在 :feature-feedback,Licenses 在 :feature-licenses,Auth 系一整套在
// :feature-auth——这些模块各自带自己的路由类型 + NavGraphBuilder 扩展函数
// (settingsGraph/feedbackGraph/licensesGraph/authGraph),这里只留 :app 自己的
// 路由:Home,以及不值得为它单独发一个 SDK 模块的 :core-net WebSocket demo 页。
@Serializable
data object Home

/** `:core-net` WebSocket 长连接能力的可玩 demo——纯 `:app` 内代码,不是发布的 SDK 一部分。 */
@Serializable
data object WebSocketDemoRoute

/** `:downloadkit` 断点续传下载能力的可玩 demo——纯 `:app` 内代码,不是发布的 SDK 一部分。 */
@Serializable
data object DownloadDemoRoute
