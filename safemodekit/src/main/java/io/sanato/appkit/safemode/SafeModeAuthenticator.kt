package io.sanato.appkit.safemode

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal object SafeModeAuthenticator {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /**
     * 设备上有指纹/人脸或锁屏密码可用。API 30 以下 androidx 不支持单独查询 DEVICE_CREDENTIAL，
     * 改用 KeyguardManager.isDeviceSecure 判断锁屏密码。
     */
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        }
        if (manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) return true
        return context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    /**
     * 失败/取消一律走 [onFailOrCancel]，调用方静默处理。单次识别失败（手指不对）不回调，系统框自己提示重试。
     *
     * API 30+ 的验证框（含锁屏密码回退）是 SystemUI 窗口，不会让当前 Activity stop；API 30 以下
     * 回退到锁屏密码时会打开系统的确认凭据 Activity，App 会被 stop，所以先登记一次外跳放行，
     * 否则在宿主设置页里开关安全模式时会被当成退后台而上锁。
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailOrCancel: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) SafeModeGate.allowExternalTrip()
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) = onFailOrCancel()
                },
            )
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(activity.getString(R.string.safemode_auth_title))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        prompt.authenticate(info)
    }
}
