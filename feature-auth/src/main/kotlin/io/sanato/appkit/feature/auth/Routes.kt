package io.sanato.appkit.feature.auth

import kotlinx.serialization.Serializable

// Navigation Compose 类型安全路由,同 :feature-settings 的 Routes.kt。
//
// ⚠️ 路由参数会被序列化进回退栈的 Bundle 并跨进程死亡恢复;同时 :app 的
// AppNavHost 会把 destination.route 送进 LogKit 和 telemetry.screenView。
// 类型安全路由的 destination.route 是【参数模板】不是参数值,所以模板名安全,
// 但参数【值】仍然会落进 SavedState。因此这里只传已脱敏的展示串
// (phoneNumberMasked),真实手机号/邮箱一律留在各自 ViewModel 的内存态里。
//
// verificationId 例外地直接传字符串:它不是长期凭证,只是 Firebase 短时效
// (默认 60s)验证会话的句柄,进程死亡后 Firebase 自己的验证会话也已失效,
// PhoneCodeViewModel 必须处理"这个 id 已经不认得了"的情况(见该文件 KDoc)。

/** 嵌套图的根。登录成功后 `popUpTo(AuthGraphRoute) { inclusive = true }` 一次清干净。 */
@Serializable
data object AuthGraphRoute

@Serializable
data object SignInRoute

@Serializable
data object SignUpRoute

@Serializable
data object ForgotPasswordRoute

@Serializable
data object PhoneNumberRoute

@Serializable
data class PhoneCodeRoute(
    val verificationId: String,
    val phoneNumberMasked: String,
)

/** 已登录态的账号页——刻意不放进 [AuthGraphRoute] 嵌套图内,那个图在登录成功后会被整体 pop 掉。 */
@Serializable
data object AccountRoute
