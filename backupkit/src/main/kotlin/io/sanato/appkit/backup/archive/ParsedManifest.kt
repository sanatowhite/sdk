package io.sanato.appkit.backup.archive

import io.sanato.appkit.backup.core.BackupRecord
import io.sanato.appkit.backup.core.ManifestHeader

/** manifest.json 解析后的内存表示，供 [ArchiveReader] 交付给宿主。 */
internal class ParsedManifest(
    val header: ManifestHeader,
    /** 全包媒体索引：新格式来自 manifest 顶层 `mediaNames`；legacy 格式为空列表
     * （legacy manifest 不携带这个字段，白名单交叉校验相应跳过，见 ArchiveReader）。 */
    val mediaNames: List<String>,
    val records: List<BackupRecord>,
)
