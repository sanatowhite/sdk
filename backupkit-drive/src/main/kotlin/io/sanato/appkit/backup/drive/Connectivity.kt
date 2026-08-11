package io.sanato.appkit.backup.drive

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 判断当前是否有可用网络。刻意不依赖 `:core-net` 的 `NetworkMonitor`——那个类完全够用，
 * 但 `:core-net` 整个模块用 `api()` 传递了 OkHttp/Retrofit/kotlinx-serialization，这个模块
 * 一直刻意不带那些依赖（见 README「为什么这个模块可以带三方依赖」），为了一次连通性判断
 * 拉一整套 HTTP 客户端栈得不偿失。逻辑和 `NetworkMonitor.hasInternetCapability` 一致，
 * 纯 `ConnectivityManager` API，minSdk 24 起可用。
 */
internal fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * [DriveBackupStore] 请求路径在确认无网络时抛出，取代裸 `UnknownHostException`/
 * `ConnectException`——调用方（宿主 app）可以按类型精确识别"这是断网,不是授权或服务端问题",
 * 展示"检查网络连接"而不是笼统的失败文案。
 */
public class DriveNoConnectivityException :
    java.io.IOException("Google Drive request failed: no network connectivity")
