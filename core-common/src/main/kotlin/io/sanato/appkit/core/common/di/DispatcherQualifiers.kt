package io.sanato.appkit.core.common.di

import javax.inject.Qualifier

/**
 * 这几个 qualifier 只声明在 core-common(不依赖 Hilt,只依赖 javax.inject),
 * 真正 `provides CoroutineDispatcher` 的 Hilt Module 挂在 `:app`——
 * core-* 模块的构造函数只认这些 qualifier,不关心背后是不是 Hilt 在装配。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainImmediateDispatcher
