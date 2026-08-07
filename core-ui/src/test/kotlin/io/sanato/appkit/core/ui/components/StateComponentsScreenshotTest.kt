package io.sanato.appkit.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.sanato.appkit.core.ui.theme.AppTemplateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 基线只在 CI 录制(打标签触发 workflow,见 Phase 10),本地跑这些测试默认是
 * "对比模式"——如果没有 baseline 图片,Roborazzi 会直接生成一份而不是失败,
 * 第一次跑之后把 src/test/screenshots 目录下生成的图片提交进库就是 baseline。
 *
 * `dynamicColor = false`——截图必须在所有设备上确定性一致,动态取色会随
 * 系统壁纸变化,截图测试必须锁定到品牌 seed color。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class StateComponentsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingState() {
        composeRule.setContent {
            AppTemplateTheme(dynamicColor = false) {
                LoadingState()
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/LoadingState.png")
    }

    @Test
    fun emptyState() {
        composeRule.setContent {
            AppTemplateTheme(dynamicColor = false) {
                EmptyState(message = "Nothing here")
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/EmptyState.png")
    }

    @Test
    fun errorState() {
        composeRule.setContent {
            AppTemplateTheme(dynamicColor = false) {
                ErrorState(message = "Something went wrong", onRetry = {})
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/ErrorState.png")
    }
}
