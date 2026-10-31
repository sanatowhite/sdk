package io.sanato.appkit.safemode

/** 暗号按住时长、退后台上锁宽限期的取值规则。纯逻辑，供 prefs 读写和设置页档位共用。 */
internal object SafeModeSettings {
    const val DEFAULT_HOLD_MS = 800L
    const val MIN_HOLD_MS = 300L
    const val MAX_HOLD_MS = 5_000L

    val HOLD_OPTIONS_MS: List<Long> = listOf(300L, 500L, 800L, 1_200L, 1_500L, 2_000L, 3_000L)

    fun coerceHoldMs(ms: Long): Long = ms.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)

    /** 退到后台后多久重新上锁。0 = 立即（与 1.3.0 及之前的行为一致）。 */
    const val DEFAULT_GRACE_MS = 0L
    const val MAX_GRACE_MS = 30 * 60 * 1000L

    val GRACE_OPTIONS_MS: List<Long> =
        listOf(0L, 30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L, MAX_GRACE_MS)

    fun coerceGraceMs(ms: Long): Long = ms.coerceIn(0L, MAX_GRACE_MS)

    /** 宽限期的展示拆分：整分钟按分钟，否则按秒；0 表示「立即」。 */
    fun graceLabelParts(ms: Long): GraceLabel {
        val coerced = coerceGraceMs(ms)
        return when {
            coerced == 0L -> GraceLabel.Immediately
            coerced % 60_000L == 0L -> GraceLabel.Minutes(coerced / 60_000L)
            else -> GraceLabel.Seconds((coerced + 999L) / 1_000L)
        }
    }

    sealed class GraceLabel {
        object Immediately : GraceLabel()

        data class Seconds(
            val value: Long,
        ) : GraceLabel()

        data class Minutes(
            val value: Long,
        ) : GraceLabel()
    }
}
