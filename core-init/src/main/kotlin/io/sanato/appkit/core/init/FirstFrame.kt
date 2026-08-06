package io.sanato.appkit.core.init

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 首帧绘制完成后回调一次——从 `AppTemplateApp.scheduleDeferredInitAfterFirstFrame()`
 * 抽出的通用原语，不依赖 [AppInitializers]，Hilt 消费方和非 Hilt 消费方都能直接用。
 *
 * 刻意【不】用 `androidx.startup`：它会注册自己的 `ContentProvider`，污染这个
 * 类本身要测量的冷启动窗口。
 */
object FirstFrame {
    /**
     * "首帧后"用第一个 Activity 窗口的 draw 回调判定,不是简单在 `onCreate` 里
     * `postDelayed` 一个固定时间——那样要么太早(首帧还没画完)要么太晚(瞎猜的
     * 延迟)。摘监听统一走 `Handler.post`(而不是在 `onDraw` 内直接
     * `removeOnDrawListener`),避免低版本上的时序问题。内部的 `AtomicBoolean`
     * 防止摘除完成前的多次 `onDraw` 回调重复触发 [action]。
     */
    fun onFirstDraw(
        application: Application,
        action: () -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val hasScheduled = AtomicBoolean(false)

        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    val decorView = activity.window.decorView
                    val listener =
                        object : ViewTreeObserver.OnDrawListener {
                            override fun onDraw() {
                                if (hasScheduled.compareAndSet(false, true)) {
                                    handler.post(action)
                                }
                                handler.post {
                                    if (decorView.viewTreeObserver.isAlive) {
                                        decorView.viewTreeObserver.removeOnDrawListener(this)
                                    }
                                }
                            }
                        }
                    decorView.viewTreeObserver.addOnDrawListener(listener)
                    application.unregisterActivityLifecycleCallbacks(this)
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
