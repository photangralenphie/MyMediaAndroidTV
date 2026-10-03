package com.photangralenphie.mymedia.androidtv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Shared activity state for short API and artwork requests shown by the TV status UI. */
object ApiActivity {
    var activeRequests by mutableIntStateOf(0)
        private set

    @Synchronized
    fun begin() {
        activeRequests++
    }

    @Synchronized
    fun end() {
        activeRequests = (activeRequests - 1).coerceAtLeast(0)
    }
}
