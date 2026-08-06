package io.sanato.apptemplate.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "io.sanato.apptemplate"

/**
 * 产物（app/src/release/generated/baselineProfiles 下的 txt 文件）是构建输入,必须
 * 提交进版本库,不是可再生的构建输出——`automaticGenerationDuringBuild = false`
 * (见 app/build.gradle.kts)。本模板骨架页面没有复杂的滚动/分页流程,这里只覆盖
 * 冷启动这一条最有价值的路径;真实项目里应该把用户实际最常走的几条路径
 * (比如"打开 -> 进设置页"这类)也录进来。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
        }
}
