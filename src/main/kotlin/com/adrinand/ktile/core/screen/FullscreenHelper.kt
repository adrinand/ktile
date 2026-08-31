package com.adrinand.ktile.core.screen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Window

object FullscreenHelper {
    const val WINDOW_TITLE = "KTile Preview"

    private var strategy: FullscreenStrategy? = null

    private fun resolveStrategy(): FullscreenStrategy {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") -> AwtFullscreenStrategy
            isWaylandSession() && isGnomeDesktop() && GnomeFullscreenStrategy.isAvailable() -> GnomeFullscreenStrategy
            isWaylandSession() && isKdeDesktop() && KdeFullscreenStrategy.isAvailable() -> KdeFullscreenStrategy
            else -> X11FullscreenStrategy
        }
    }

    private fun isWaylandSession(): Boolean = System.getenv("XDG_SESSION_TYPE")?.lowercase() == "wayland"

    private fun isGnomeDesktop(): Boolean = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase()?.contains("gnome") == true

    private fun isKdeDesktop(): Boolean = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase()?.contains("kde") == true

    suspend fun enterFullscreen(
        window: Window,
        timeoutMs: Long,
    ) {
        withContext(Dispatchers.IO) {
            val current = strategy ?: resolveStrategy().also { strategy = it }
            current.setFullscreen(window)
            current.waitForFullscreen(window, timeoutMs)
        }
    }
}
