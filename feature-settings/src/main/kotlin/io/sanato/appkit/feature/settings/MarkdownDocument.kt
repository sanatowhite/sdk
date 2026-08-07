package io.sanato.appkit.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 极简 markdown 渲染:只处理 `# `/`## ` 标题和 `**bold**`——隐私政策/用户协议
 * 这类文档不需要表格/图片/代码块,不为此引入 WebView 或完整 markdown 库。
 */
@Composable
fun MarkdownDocument(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val lines = remember(markdown) { markdown.lines() }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lines) { line ->
            when {
                line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium)
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.titleLarge)
                line.isBlank() -> Unit
                else -> Text(parseInlineBold(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun parseInlineBold(line: String): AnnotatedString =
    buildAnnotatedString {
        var remaining = line
        while (remaining.isNotEmpty()) {
            val start = remaining.indexOf("**")
            if (start == -1) {
                append(remaining)
                break
            }
            append(remaining.substring(0, start))
            val end = remaining.indexOf("**", start + 2)
            if (end == -1) {
                append(remaining.substring(start))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(remaining.substring(start + 2, end))
            }
            remaining = remaining.substring(end + 2)
        }
    }
