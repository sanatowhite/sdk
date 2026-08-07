package io.sanato.appkit.smoke

import io.sanato.appkit.core.common.di.DefaultDispatcher
import io.sanato.appkit.core.common.di.IoDispatcher
import io.sanato.appkit.core.common.di.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * qualifier 本身声明在 `:core-common`(零 Hilt 依赖),真正 `provides
 * CoroutineDispatcher` 的 `@Module` 在 `:core-common-hilt`——这个类同时验证
 * 两件事:qualifier 类型解析(`api`/`implementation` 判定)+ Hilt 绑定真的能
 * 从发布出去的 `core-common-hilt` AAR 里聚合到。
 */
@Singleton
class DispatcherSmoke
    @Inject
    constructor(
        @IoDispatcher val io: CoroutineDispatcher,
        @DefaultDispatcher val default: CoroutineDispatcher,
        @MainImmediateDispatcher val mainImmediate: CoroutineDispatcher,
    )
