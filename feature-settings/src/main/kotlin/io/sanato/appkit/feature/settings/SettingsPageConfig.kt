package io.sanato.appkit.feature.settings

import androidx.annotation.RawRes

/**
 * 设置页的行为配置——消费方不需要改我们的代码,构造一个实例传进去就行。
 */
data class SettingsPageConfig(
    /**
     * 支持的语言标签列表,`null` 元素代表"跟随系统"。整体传 `null` 会隐藏语言行——
     * 消费方的 Activity 不是 `AppCompatActivity` 时必须这样(语言切换基于
     * `AppCompatDelegate`)。标签的展示名用 `Locale.forLanguageTag(tag).getDisplayName(...)`
     * 生成,不需要额外配置。
     */
    val supportedLanguageTags: List<String?>? = listOf(null),
)

/**
 * 标准页面需要的内容资源——都是可选的,不提供就自动隐藏对应入口:
 * - `privacyPolicyRawRes` / `termsOfServiceRawRes` 为 `null` 时,设置页不显示
 *   对应的行,`PrivacyPolicyRoute`/`TermsOfServiceRoute` 也不应该被导航到。
 * - `changelogRawRes` 为 `null` 时,关于页不显示更新日志段落,What's New 弹窗
 *   逻辑也不会触发。
 *
 * 资源格式沿用 `ChangelogReader`(见该文件)和纯文本/Markdown(见 `MarkdownDocument`)。
 */
data class StandardPagesContent(
    @RawRes val privacyPolicyRawRes: Int? = null,
    @RawRes val termsOfServiceRawRes: Int? = null,
    @RawRes val changelogRawRes: Int? = null,
)
