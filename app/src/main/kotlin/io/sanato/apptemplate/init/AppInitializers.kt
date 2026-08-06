package io.sanato.apptemplate.init

import android.app.Application
import javax.inject.Inject

class AppInitializers
    @Inject
    constructor(
        @Eager private val eagerInitializers: Set<@JvmSuppressWildcards AppInitializer>,
        @Deferred private val deferredInitializers: Set<@JvmSuppressWildcards AppInitializer>,
    ) {
        fun runEager(application: Application) {
            eagerInitializers.forEach { it.init(application) }
        }

        fun runDeferred(application: Application) {
            deferredInitializers.forEach { it.init(application) }
        }
    }
