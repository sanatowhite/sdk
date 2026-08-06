package io.sanato.apptemplate.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 语言设置不进 DataStore——`AppCompatDelegate` 自己在系统层持久化(pre-T 靠
 * `AppLocalesMetadataHolderService`,appcompat 1.7.1 自动通过 manifest merge
 * 注册,这里不需要手动声明)。这就是 :core-data 的 `UserSettings` 里没有语言
 * 字段的原因。
 *
 * ⚠️ pre-T(API < 33)调用 `setApplicationLocales` 会触发 Activity 重建——
 * 依赖这个类的页面如果有滚动位置等瞬态 UI 状态,要用 `rememberSaveable` 而不是
 * `remember`,且这条行为只能在真机(API 24/25 优先)上手测,Robolectric 测不出。
 */
object LocaleManager {
    /** `null` 表示跟随系统语言。 */
    fun setAppLocale(languageTag: String?) {
        val locales =
            if (languageTag == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** `null` 表示当前跟随系统语言。 */
    fun currentAppLocaleTag(): String? =
        AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.toLanguageTags()
}
