package io.sanato.apptemplate.debugtools

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.core.content.FileProvider
import io.sanato.apptemplate.core.telemetry.RingLogBuffer
import io.sanato.logkit.LogKit
import java.io.File

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LogKitPanel()
    }
}

/**
 * 验证日志 SDK 的四个属性,和它的公开 API 契约保持一致:
 *  (a) 并发下顺序一致——面板只负责生产压力和显示"写了多少/丢了多少/耗时多少",
 *      连续性本身必须【离线】用 `logkit-decrypt --verify-seq` 验证,面板里没有
 *      私钥,做不出真的绿勾,也不假装能做出来。
 *  (b) 5MB 总预算滚动淘汰。
 *  (c) 崩溃/ANR 持久性——复用现有 [CrashTriggers],不新写触发器。
 *  (d) 可解密性——导出 + 系统分享面板,走和 `FeedbackViewModel` 一样的
 *      FileProvider 路径。
 *
 * `LogKit.stats()` 不是 Flow,和上面 `RingLogBuffer` 同理:手动 refresh 计数器
 * 强制重新读取,不假装它会自动更新。
 */
@Composable
private fun LogKitPanel() {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val stats = remember(refreshTick) { LogKit.stats() }

    var stressRunning by remember { mutableStateOf(false) }
    var stressResult by remember { mutableStateOf<String?>(null) }

    Text("LogKit", style = MaterialTheme.typography.titleSmall)

    Text("(a) 并发顺序一致性", style = MaterialTheme.typography.labelMedium)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(4 to 1_000, 8 to 5_000, 16 to 20_000).forEach { (threads, perThread) ->
            Button(
                enabled = !stressRunning,
                onClick = {
                    stressRunning = true
                    Thread {
                        val seqBefore = LogKit.stats().nextSequence
                        val dropBefore = LogKit.droppedRecordCount()
                        val enqueueStartNanos = System.nanoTime()
                        val workers =
                            (0 until threads).map { t ->
                                Thread {
                                    repeat(perThread) { i -> LogKit.i("stress", "t=$t i=$i") }
                                }.also { it.start() }
                            }
                        workers.forEach { it.join() }
                        val enqueueMillis = (System.nanoTime() - enqueueStartNanos) / 1_000_000
                        val flushStartNanos = System.nanoTime()
                        val flushOk = LogKit.flushBlocking(10_000)
                        val flushMillis = (System.nanoTime() - flushStartNanos) / 1_000_000
                        val after = LogKit.stats()
                        stressResult =
                            "wrote ${threads * perThread}, seq +${after.nextSequence - seqBefore}, " +
                            "dropped ${after.droppedRecords - dropBefore}, enqueue ${enqueueMillis}ms, " +
                            "flush ${flushMillis}ms, flushOk=$flushOk"
                        stressRunning = false
                        refreshTick++
                    }.start()
                },
            ) { Text("$threads×$perThread") }
        }
    }
    stressResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text(
        "连续性离线验证: 导出 → logkit-decrypt --private-key <key> --in <zip> --verify-seq",
        style = MaterialTheme.typography.bodySmall,
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("(b) 5MB 总预算滚动淘汰", style = MaterialTheme.typography.labelMedium)
    val budgetFraction =
        if (stats.budgetBytes >
            0
        ) {
            (stats.totalBytes.toFloat() / stats.budgetBytes).coerceIn(0f, 1f)
        } else {
            0f
        }
    LinearProgressIndicator(progress = { budgetFraction }, modifier = Modifier.fillMaxWidth())
    Text(
        "${stats.totalBytes / 1024} KiB / ${stats.budgetBytes / 1024} KiB   files=${stats.files.size}   evicted=${stats.evictedFiles}",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Button(onClick = {
            // 一次性写入足够大的一批记录,强迫总量超预算,验证淘汰真的会跑。
            repeat(6 * 1024) { LogKit.i("fill", "x".repeat(1024)) }
            LogKit.flushBlocking(10_000)
            refreshTick++
        }) { Text("Fill 6MiB") }
        Button(onClick = {
            LogKit.purge()
            refreshTick++
        }) { Text("Purge all") }
        Button(onClick = { refreshTick++ }) { Text("Refresh") }
    }
    LazyColumn {
        items(stats.files) { file ->
            Text("${file.name}  ${file.sizeBytes / 1024} KiB", style = MaterialTheme.typography.bodySmall)
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("(c) 崩溃 / ANR 持久性", style = MaterialTheme.typography.labelMedium)
    Button(
        onClick = {
            repeat(100) { LogKit.i("durability", "pre-crash #$it") }
            // 刻意不手动 flush——这才是在测 fatal 路径的自动 flush。
            CrashTriggers.triggerCrash()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Log 100 then crash") }
    Button(
        onClick = {
            LogKit.w("durability", "about to block main thread")
            CrashTriggers.triggerAnr()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Trigger ANR (blocks 15s)") }
    Text(
        "重启后回到本面板:文件列表应多出一个文件;离线解密应能看到 100 条 " +
            "pre-crash 记录加一条含完整堆栈的 FATAL;ANR 场景应能看到 exitInfo + 主线程栈。",
        style = MaterialTheme.typography.bodySmall,
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("(d) 可解密性", style = MaterialTheme.typography.labelMedium)
    Text("keyId=${stats.keyId}  formatVersion=${stats.formatVersion}", style = MaterialTheme.typography.bodySmall)
    Button(
        onClick = {
            val file = File(context.cacheDir, "logkit-export-${System.currentTimeMillis()}.zip")
            if (LogKit.export(file)) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Export & share") }
}
