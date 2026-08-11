package io.sanato.appkit.smoke

import io.sanato.appkit.auth.net.hilt.Authenticated
import io.sanato.appkit.core.auth.AuthError
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.AuthTokenProvider
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * 逐个引用 `:core-auth`/`:auth-net-hilt` 的公开入口——这两个模块最容易判定错误的
 * `api`/`implementation` 边界分别是:`AuthRepository`/`AuthTokenProvider`(由
 * `:auth-firebase` 的 `FirebaseAuthModule` 提供绑定,消费方必须能像这里一样只
 * 依赖 `:core-auth` 的接口类型)、`@Authenticated OkHttpClient`(由
 * `:auth-net-hilt` 提供,验证这条限定符绑定确实解析得到)。任何一条判定错误,
 * 这个文件就会出现 unresolved reference,编译直接失败。
 *
 * 只做编译期验证:不调用任何 `AuthRepository` 的 suspend 方法(签邮箱/Google/
 * Apple/手机号全部不触发),`AuthError` 只用来验证 sealed 层级本身可解析。
 * `:auth-firebase` 的 `FirebaseAuthRepository` 在没有真实 `google-services.json`
 * 的进程里构造签名不会崩(`FirebaseAuth.getInstance()` 是 lazy,不在构造期
 * 调用——见其 KDoc),而 consumer-smoke 只跑 `assembleDebug`,从不启动,所以
 * 这里甚至不需要验证运行期行为。
 */
class AuthSmoke
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val authTokenProvider: AuthTokenProvider,
        @Authenticated private val authenticatedClient: OkHttpClient,
    ) {
        fun currentAuthState() = authRepository.authState.value

        fun cachedToken(): String? = authTokenProvider.cachedIdToken()

        fun describeError(error: AuthError): String = error.message ?: error::class.java.simpleName

        fun client(): OkHttpClient = authenticatedClient
    }
