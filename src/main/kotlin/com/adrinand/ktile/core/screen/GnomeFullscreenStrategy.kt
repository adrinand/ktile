package com.adrinand.ktile.core.screen

import kotlinx.coroutines.delay
import java.awt.Window
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val logger = Logger.getLogger("com.adrinand.ktile.core.screen.GnomeFullscreenStrategy")

private const val SERVICE_NAME = "org.gnome.Shell.Extensions.KTile"
private const val OBJECT_PATH = "/org/gnome/Shell/Extensions/KTile"
private const val INTERFACE_NAME = "org.gnome.Shell.Extensions.KTile"
private const val LEADING_BOOLEAN_REGEX = """\(\s*(true|false)"""
private const val POLL_INTERVAL_MS = 50L
private const val MAXIMIZE_SETTLE_MS = 50L
private const val AVAILABILITY_TIMEOUT_MS = 1_000L
private const val CALL_TIMEOUT_MS = 300L

/**
 * [FullscreenStrategy] that asks the companion GNOME Shell extension to
 * maximize the KTile preview window.
 *
 * This avoids relying on X11 client messages for the preview window on
 * Wayland + GNOME sessions.
 */
object GnomeFullscreenStrategy : FullscreenStrategy {
    private val booleanRegex = Regex(LEADING_BOOLEAN_REGEX)

    fun isAvailable(): Boolean {
        val client = findGdbus() ?: return false
        val output =
            runDbusClient(
                client,
                listOf("introspect", "--session", "--dest", SERVICE_NAME, "--object-path", OBJECT_PATH),
                timeoutMs = AVAILABILITY_TIMEOUT_MS,
            )
        return output != null
    }

    override suspend fun setFullscreen(window: Window) {
        val output = callMethod("MaximizePreview") ?: return
        if (parseLeadingBoolean(output.trim()) != true) {
            logger.warning { "GNOME extension did not maximize the preview" }
        }
    }

    override suspend fun waitForFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean {
        delay(MAXIMIZE_SETTLE_MS.milliseconds)
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (deadline.hasNotPassedNow()) {
            val output = callMethod("IsPreviewMaximized") ?: return false
            if (parseLeadingBoolean(output.trim()) == true) {
                return true
            }
            delay(POLL_INTERVAL_MS.milliseconds)
        }
        return false
    }

    private fun callMethod(method: String): String? {
        val client = findGdbus() ?: return null
        val command =
            listOf(
                "call",
                "--session",
                "--dest",
                SERVICE_NAME,
                "--object-path",
                OBJECT_PATH,
                "--method",
                "$INTERFACE_NAME.$method",
            )
        return runDbusClient(client, command, timeoutMs = CALL_TIMEOUT_MS)
    }

    private fun parseLeadingBoolean(output: String): Boolean? {
        val match = booleanRegex.find(output) ?: return null
        return match.groupValues[1].toBooleanStrictOrNull()
    }
}
