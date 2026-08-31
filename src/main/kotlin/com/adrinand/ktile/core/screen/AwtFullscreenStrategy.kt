package com.adrinand.ktile.core.screen

import java.awt.Window

object AwtFullscreenStrategy : FullscreenStrategy {
    override suspend fun setFullscreen(window: Window) {
        try {
            window.graphicsConfiguration.device.fullScreenWindow = window
        } catch (_: Exception) {
            println("FullscreenHelper: AWT fullscreen failed")
        }
    }
}
