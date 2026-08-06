package io.sanato.apptemplate.debugtools

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.sanato.apptemplate.core.telemetry.RingLogBuffer
import kotlinx.coroutines.launch

/**
 * 入口:应用内可拖拽把手 + `ModalNavigationDrawer(gesturesEnabled = false)`——
 * 把手本身就是唯一入口,不需要再响应侧滑手势(避免跟内容自己的手势冲突)。
 * 明确排除摇一摇(模拟器不可用、噪声大)、通知栏(33+ 要权限)、悬浮窗(需授权)
 * 这三种常见的替代入口方案。
 */
@Composable
fun DebugDrawer(
    ringLogBuffer: RingLogBuffer,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var handleOffset by remember { mutableStateOf(Offset(0f, 300f)) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                DebugDrawerContent(ringLogBuffer = ringLogBuffer)
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            IconButton(
                onClick = { scope.launch { drawerState.open() } },
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(handleOffset.x.toInt(), handleOffset.y.toInt()) }
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                handleOffset += dragAmount
                            }
                        },
            ) {
                Icon(Icons.Outlined.Build, contentDescription = "Debug menu", tint = Color.White)
            }
        }
    }
}
