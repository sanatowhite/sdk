package io.sanato.appkit.backup.core

/**
 * 日志出口。backupkit 内部不直接调用 `android.util.Log`——那会让核心格式/编排逻辑的
 * 单测必须依赖 Robolectric 才能跑，故意保持纯 JVM 可测。
 */
public interface BackupLogger {
    public fun info(message: String)

    public fun warn(
        message: String,
        error: Throwable? = null,
    )

    public companion object {
        public val None: BackupLogger =
            object : BackupLogger {
                override fun info(message: String) {}

                override fun warn(
                    message: String,
                    error: Throwable?,
                ) {}
            }
    }
}
