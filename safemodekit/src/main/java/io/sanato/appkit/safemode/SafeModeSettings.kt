package io.sanato.appkit.safemode

/** 暗号按住时长的取值规则。纯逻辑，供 prefs 读写和设置页档位共用。 */
internal object SafeModeSettings {
    const val DEFAULT_HOLD_MS = 1_200L
    const val MIN_HOLD_MS = 500L
    const val MAX_HOLD_MS = 5_000L

    val HOLD_OPTIONS_MS: List<Long> = listOf(500L, 800L, 1_200L, 1_500L, 2_000L, 3_000L)

    fun coerceHoldMs(ms: Long): Long = ms.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)
}
