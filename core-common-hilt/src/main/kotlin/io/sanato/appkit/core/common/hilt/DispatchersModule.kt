package io.sanato.appkit.core.common.hilt

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sanato.appkit.core.common.di.DefaultDispatcher
import io.sanato.appkit.core.common.di.IoDispatcher
import io.sanato.appkit.core.common.di.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * `:core-common` 声明的三个 dispatcher qualifier 一直没有对应的 provider——
 * 发布出去后这会变成一个真 bug:消费方拿到 `@IoDispatcher` 却没人能满足它,
 * 只能自己抄一遍。补上默认实现,不冲突:qualifier 是我们自己的 FQN
 * （`io.sanato.appkit.core.common.di.IoDispatcher`），消费方自己的 dispatcher
 * module 用的是他们自己的 qualifier,不会撞。
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainImmediateDispatcher
    fun provideMainImmediateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}
