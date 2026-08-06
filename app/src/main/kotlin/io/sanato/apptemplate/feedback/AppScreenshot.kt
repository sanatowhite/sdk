package io.sanato.apptemplate.feedback

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer

/**
 * 持有当前内容根节点的 [GraphicsLayer] 引用,供反馈页截图用。`MainActivity`
 * 用 `rememberGraphicsLayer()` 包住整个 `AppNavHost` 内容并在这里注册。
 *
 * ⚠️ 已知限制:[capture] 只能捕获调用它那一刻屏幕上实际显示的内容。当前接线
 * 是"设置页 -> 反馈页"这条导航路径,调用 capture() 时反馈页自己已经是当前
 * 内容,所以截图截到的是反馈页,不是"出问题的那个页面"。要做到后者需要在
 * 触发反馈的动作发生时(比如未来的"摇一摇反馈"手势)当场调用 capture()、
 * 把结果作为参数传递,而不是导航之后再截——这里先把捕获机制打通,调用时机
 * 的限制留给以后接入更好的触发方式时解决。
 */
object AppScreenshot {
    private var graphicsLayer: GraphicsLayer? = null

    internal fun register(layer: GraphicsLayer) {
        graphicsLayer = layer
    }

    suspend fun capture(): ImageBitmap? = graphicsLayer?.toImageBitmap()
}
