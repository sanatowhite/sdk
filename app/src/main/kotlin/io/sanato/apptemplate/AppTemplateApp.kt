package io.sanato.apptemplate

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import dagger.hilt.android.HiltAndroidApp
import io.sanato.apptemplate.init.AppInitializers
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltAndroidApp
class AppTemplateApp : Application() {
    @Inject
    lateinit var appInitializers: AppInitializers

    override fun onCreate() {
        super.onCreate()
        appInitializers.runEager(this)
        scheduleDeferredInitAfterFirstFrame()
    }

    /**
     * "首帧后"用第一个 Activity 窗口的 draw 回调判定,不是简单在 onCreate 里
     * postDelayed 一个固定时间——那样要么太早(首帧还没画完)要么太晚(瞎猜的
     * 延迟)。摘监听统一走 `Handler.post`(而不是在 onDraw 内直接
     * removeOnDrawListener),避免低版本上的时序问题。`hasScheduled` 防止
     * 摘除完成前的多次 onDraw 回调重复触发 deferred 初始化。
     */
    private fun scheduleDeferredInitAfterFirstFrame() {
        val handler = Handler(Looper.getMainLooper())
        val hasScheduled = AtomicBoolean(false)

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    val decorView = activity.window.decorView
                    val listener =
                        object : ViewTreeObserver.OnDrawListener {
                            override fun onDraw() {
                                if (hasScheduled.compareAndSet(false, true)) {
                                    handler.post { appInitializers.runDeferred(this@AppTemplateApp) }
                                }
                                handler.post {
                                    if (decorView.viewTreeObserver.isAlive) {
                                        decorView.viewTreeObserver.removeOnDrawListener(this)
                                    }
                                }
                            }
                        }
                    decorView.viewTreeObserver.addOnDrawListener(listener)
                    unregisterActivityLifecycleCallbacks(this)
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
