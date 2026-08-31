package com.adrinand.ktile.core.screen

import java.awt.Window

interface FullscreenStrategy {
    suspend fun setFullscreen(window: Window)

    suspend fun waitForFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean = true
}
