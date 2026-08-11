package io.sanato.appkit.backup.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * [DriveTokenProvider] 的默认实现，基于 GMS Authorization API。这是本模块唯一直接
 * 使用 `com.google.android.gms.*` 类型的地方——全部停留在方法体内部，公开签名
 * （构造函数、[authorize]/[handleConsentResult]/[asTokenProvider] 的参数与返回类型）
 * 里不出现任何 GMS 类型。
 *
 * token 只缓存在内存（TTL 50 分钟，比 Google 实际的 1 小时提前刷新），从不落盘——
 * 持久授权由 GMS 自己在系统层维护。
 */
public class GmsDriveAuthorizer(
    private val context: Context,
    private val scope: String = "https://www.googleapis.com/auth/drive.file",
) {
    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedTokenExpiryMillis: Long = 0L

    /** 发起一次授权。已有有效静默 token 时直接返回；否则可能返回 needsConsent，宿主 Activity 需要发起 `startIntentSenderForResult`。 */
    public suspend fun authorize(): DriveAuthResult {
        // 根因排查记录：断网时不做这层前置检查，`Identity.getAuthorizationClient` 仍然会
        // 返回 hasResolution=true（本地判断，不需要网络），宿主弹出同意界面后 GMS 内部换
        // token 才失败——用户会看到同意弹窗一闪而过、体验成"授权失败"，实际是断网。提前拦
        // 一道，直接不发起这次注定失败的授权流程。
        if (!isNetworkAvailable(context)) {
            return DriveAuthResult.noConnectivity()
        }
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(scope))).build()
        return try {
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            if (result.hasResolution()) {
                val sender = result.pendingIntent?.intentSender
                if (sender != null) {
                    DriveAuthResult.consentRequired(sender)
                } else {
                    DriveAuthResult.failure("authorization needs consent but no PendingIntent was returned")
                }
            } else {
                val token = result.accessToken
                if (token == null) {
                    DriveAuthResult.failure("authorize() returned no access token")
                } else {
                    cacheToken(token)
                    DriveAuthResult.success(token)
                }
            }
        } catch (e: Exception) {
            DriveAuthResult.failure(e.message ?: "authorize() failed: ${e::class.simpleName}")
        }
    }

    /** 宿主 Activity 在 `onActivityResult`/Activity Result API 回调里，把同意流程返回的 [data] 传进来完成授权。 */
    public fun handleConsentResult(data: Intent?): DriveAuthResult =
        try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token == null) {
                DriveAuthResult.failure("consent result has no access token")
            } else {
                cacheToken(token)
                DriveAuthResult.success(token)
            }
        } catch (e: Exception) {
            DriveAuthResult.failure(e.message ?: "handleConsentResult() failed: ${e::class.simpleName}")
        }

    /** 401 自愈：清掉本地缓存 + 系统层的静默 token，下一次 [authorize] 会重新走一遍流程。 */
    public suspend fun clearCachedToken() {
        val token = cachedToken ?: return
        cachedToken = null
        cachedTokenExpiryMillis = 0L
        withContext(Dispatchers.IO) {
            runCatching { GoogleAuthUtil.clearToken(context, token) }
        }
    }

    /**
     * 转成 [DriveTokenProvider]：有有效缓存直接用，否则静默尝试 [authorize]。
     * ⚠️ 若返回 needsConsent（用户从未同意过、或同意已被撤销），这里会抛异常而不是
     * 弹出同意界面——UI 层的同意流程必须由宿主 Activity 主动调用 [authorize] 发起，
     * 这个 provider 只负责"已经同意过"之后的静默续期。
     */
    public fun asTokenProvider(): DriveTokenProvider =
        DriveTokenProvider {
            val cached = cachedToken
            if (cached != null && System.currentTimeMillis() < cachedTokenExpiryMillis) {
                return@DriveTokenProvider cached
            }
            val result = authorize()
            result.accessToken
                ?: error("Google Drive not authorized: ${result.errorMessage ?: "user consent required"}")
        }

    private fun cacheToken(token: String) {
        cachedToken = token
        cachedTokenExpiryMillis = System.currentTimeMillis() + TOKEN_TTL_MILLIS
    }

    private companion object {
        const val TOKEN_TTL_MILLIS = 50 * 60 * 1000L
    }
}
