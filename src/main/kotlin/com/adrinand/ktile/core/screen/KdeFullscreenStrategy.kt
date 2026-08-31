package com.adrinand.ktile.core.screen

import kotlinx.coroutines.delay
import java.awt.Window
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val logger = Logger.getLogger("com.adrinand.ktile.core.screen.KdeFullscreenStrategy")

private const val SERVICE_NAME = "org.kde.KWin.Script.KTile"
private const val OBJECT_PATH = "/KTile"
private const val INTERFACE_NAME = "org.kde.KWin.Script.KTile"
private const val LEADING_BOOLEAN_REGEX = """\(\s*(true|false)"""
private const val POLL_INTERVAL_MS = 50L
private const val MAXIMIZE_SETTLE_MS = 50L
private const val AVAILABILITY_TIMEOUT_MS = 1_000L
private const val CALL_TIMEOUT_MS = 300L

/**
 * [FullscreenStrategy] that asks the companion KDE KWin script to maximize
 * the KTile preview window.
 *
 * This avoids relying on X11 client messages for the preview window on
 * Wayland + KDE Plasma sessions.
 */
object KdeFullscreenStrategy : FullscreenStrategy {
    private val booleanRegex = Regex(LEADING_BOOLEAN_REGEX)

    fun isAvailable(): Boolean {
        val client = findQdbusOrGdbus() ?: return false
        val args = buildIntrospectArgs(client)
        return runDbusClient(client, args, timeoutMs = AVAILABILITY_TIMEOUT_MS) != null
    }

    override suspend fun setFullscreen(window: Window) {
        val output = callMethod("maximizePreview") ?: return
        if (parseLeadingBoolean(output.trim()) != true) {
            logger.warning { "KDE script did not maximize the preview" }
        }
    }

    override suspend fun waitForFullscreen(
        window: Window,
        timeoutMs: Long,
    ): Boolean {
        delay(MAXIMIZE_SETTLE_MS.milliseconds)
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (deadline.hasNotPassedNow()) {
            val output = callMethod("isPreviewMaximized") ?: return false
            if (parseLeadingBoolean(output.trim()) == true) {
                return true
            }
            delay(POLL_INTERVAL_MS.milliseconds)
        }
        return false
    }

    private fun callMethod(method: String): String? {
        val client = findQdbusOrGdbus() ?: return null
        val command = buildMethodArgs(client, method)
        return runDbusClient(client, command, timeoutMs = CALL_TIMEOUT_MS)
    }

    private fun buildIntrospectArgs(client: String): List<String> =
        if (client.endsWith("qdbus") || client.endsWith("/qdbus")) {
            listOf(SERVICE_NAME, OBJECT_PATH)
        } else {
            listOf("introspect", "--session", "--dest", SERVICE_NAME, "--object-path", OBJECT_PATH)
        }

    private fun buildMethodArgs(
        client: String,
        method: String,
    ): List<String> =
        if (client.endsWith("qdbus") || client.endsWith("/qdbus")) {
            listOf(SERVICE_NAME, OBJECT_PATH, "$INTERFACE_NAME.$method")
        } else {
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
        }

    private fun parseLeadingBoolean(output: String): Boolean? {
        val match = booleanRegex.find(output) ?: return null
        return match.groupValues[1].toBooleanStrictOrNull()
    }
}
