package io.sanato.apptemplate.debugtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.sanato.apptemplate.core.telemetry.RingLogBuffer

@Composable
internal fun DebugDrawerContent(ringLogBuffer: RingLogBuffer) {
    val context = LocalContext.current
    val flagStore = remember { DebugFlagStore(context) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Debug Drawer", style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Feature flags (local override)", style = MaterialTheme.typography.titleSmall)
        DebugFlagStore.KNOWN_FLAGS.forEach { key ->
            var enabled by remember { mutableStateOf(flagStore.isEnabled(key)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(key)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        flagStore.setEnabled(key, it)
                    },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Crash / ANR / OOM triggers", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { CrashTriggers.triggerCrash() }, modifier = Modifier.fillMaxWidth()) {
            Text("Trigger crash")
        }
        Button(onClick = { CrashTriggers.triggerAnr() }, modifier = Modifier.fillMaxWidth()) {
            Text("Trigger ANR (blocks 15s)")
        }
        Button(onClick = { CrashTriggers.triggerOom() }, modifier = Modifier.fillMaxWidth()) {
            Text("Trigger OOM")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Startup / jank / network log (last 200 lines)", style = MaterialTheme.typography.titleSmall)
        // `RingLogBuffer` 不是 Flow,是持续被写入的可变容器——`remember` 只会在
        // 抽屉第一次打开时拍一次快照,之后永远不刷新。用一个手动的 refresh
        // 计数器强制重新读取,而不是假装它会自动更新。
        var refreshTick by remember { mutableIntStateOf(0) }
        val lines = remember(refreshTick) { ringLogBuffer.snapshot() }
        Button(onClick = { refreshTick++ }, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh")
        }
        LazyColumn {
            items(lines.takeLast(50)) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
