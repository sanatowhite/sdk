package io.sanato.appkit.feature.licenses

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import io.sanato.appkit.core.ui.components.AppScaffold

/**
 * 展示 AboutLibraries 插件离线生成的开源许可清单。
 *
 * 前置条件(这个模块不做,消费方自己在 app 模块做):apply
 * `com.mikepenz.aboutlibraries.plugin`(建议 `offlineMode = true`,构建期不联网
 * 抓 license),插件会在编译期生成一个 raw 资源(默认 `R.raw.aboutlibraries`)——
 * 把那个资源 id 传给 [librariesRawRes]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    @RawRes librariesRawRes: Int,
    onNavigateBack: () -> Unit,
) {
    val libraries by produceLibraries(librariesRawRes)

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appkit_licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LibrariesContainer(libraries = libraries, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
