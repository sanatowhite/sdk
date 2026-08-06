package io.sanato.apptemplate.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.apptemplate.about.BuildInfo
import io.sanato.apptemplate.core.data.UserSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全新安装也要走一遍 [markSeen]——否则 `lastSeenWhatsNewVersionCode` 默认值 0
 * 会让首次启动就弹一次更新日志,而全新安装的用户根本没有"上一个版本"可比较。
 * 调用方(见 `HomeScreen`)必须在展示完(或判定不需要展示)后立即调用一次。
 */
@HiltViewModel
class WhatsNewViewModel
    @Inject
    constructor(
        private val userSettingsRepository: UserSettingsRepository,
    ) : ViewModel() {
        // 可空:StateFlow 在 DataStore 真正读出第一个值之前会先发出 stateIn 的
        // initialValue——如果这里默认成 false,调用方会在真实值到达前就误判
        // "不需要展示"并抢先调用 markSeen(),把 lastSeenWhatsNewVersionCode 提前写
        // 成当前版本号,导致真实值到达后 shouldShow 永远算不出 true。null 表示
        // "还不知道",调用方必须等它变成非 null 才能下判断。
        val shouldShow: StateFlow<Boolean?> =
            userSettingsRepository.settings
                .map { shouldShowWhatsNew(it.lastSeenWhatsNewVersionCode, BuildInfo.versionCode) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        fun markSeen() {
            viewModelScope.launch { userSettingsRepository.setLastSeenWhatsNewVersionCode(BuildInfo.versionCode) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * 抽成纯函数只是为了能在单测里覆盖"从旧版本升级"这类跟 [BuildInfo.versionCode]
 * 实际取值(现在是 1)无关的场景——纯函数可以喂任意 lastSeen/current 组合,
 * 不需要真的把当前版本号推到 2 才能测出"升级"分支。
 */
internal fun shouldShowWhatsNew(
    lastSeenVersionCode: Int,
    currentVersionCode: Int,
): Boolean = lastSeenVersionCode in 1 until currentVersionCode
