package io.sanato.logkit

/**
 * 五个级别,冻结——公开 API 只增不减这条铁律下,给 enum 加常量会破坏消费方
 * 已有的穷尽 `when`,所以这五个常量永远不会再变。
 */
public enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ;

    internal val wireValue: Int
        get() = ordinal

    internal val logcatPriority: Int
        get() =
            when (this) {
                VERBOSE -> android.util.Log.VERBOSE
                DEBUG -> android.util.Log.DEBUG
                INFO -> android.util.Log.INFO
                WARN -> android.util.Log.WARN
                ERROR -> android.util.Log.ERROR
            }

    internal companion object {
        fun fromWireValue(value: Int): LogLevel = entries.getOrElse(value) { DEBUG }
    }
}
