package io.sanato.appkit.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.common.AppBuildInfo
import javax.inject.Inject

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        val buildInfo: AppBuildInfo,
    ) : ViewModel()
