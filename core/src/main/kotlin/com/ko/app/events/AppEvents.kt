package com.ko.app.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppEvents {
    private val _screenshotsScanned = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val screenshotsScanned = _screenshotsScanned.asSharedFlow()

    fun notifyScreenshotsScanned() {
        _screenshotsScanned.tryEmit(Unit)
    }
}

