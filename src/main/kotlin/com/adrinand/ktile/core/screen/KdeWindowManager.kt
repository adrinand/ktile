package com.adrinand.ktile.core.screen

import java.awt.Rectangle
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.ktile.core.screen.KdeWindowManager")

private const val BACKEND_NAME = "kwin"
private const val WAYLAND_TOKEN = "active"
private const val SERVICE_NAME = "org.kde.KWin.Script.KTile"
private const val OBJECT_PATH = "/KTile"
private const val INTERFACE_NAME = "org.kde.KWin.Script.KTile"
private val ACTIVE_WINDOW_REGEX =
    Regex(
        "\\(\\s*(true|false)\\s*,\\s*'([^']*)'\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)",
    )

/**
 * [WindowManager] implementation for native Wayland windows on KDE Plasma.
 *
 * This backend requires the companion KWin script (`ktile.kwin`) to be
 * installed and enabled. The script exposes a D-Bus service that can read the
 * active window and move/resize it.
 */
class KdeWindowManager : WindowManager {
    /**
     * Returns `true` if the companion KWin script is available on the session
     * bus and a D-Bus client is installed.
     */
    fun isAvailable(): Boolean {
        val client = findQdbusOrGdbus() ?: return false
        val args = buildIntrospectArgs(client)
        val output = runDbusClient(client, args)
        return output != null
    }

    override fun getActiveWindowId(): WindowHandle? {
        val output = callMethod("getActiveWindow") ?: return null
        val hasWindow = parseActiveWindowFlag(output.trim()) ?: return null
        return if (hasWindow) {
            WindowHandle.Wayland(BACKEND_NAME, WAYLAND_TOKEN)
        } else {
            null
        }
    }

    override fun setWindowBounds(
        window: WindowHandle,
        bounds: Rectangle,
    ) {
        if (!isKdeHandle(window)) {
            logger.warning { "Ignoring setWindowBounds for non-KDE handle: $window" }
            return
        }
        callMethod(
            "moveResizeActiveWindow",
            listOf(
                bounds.x.toString(),
                bounds.y.toString(),
                bounds.width.toString(),
                bounds.height.toString(),
            ),
        )
    }

    override fun focusWindow(window: WindowHandle) {
        if (!isKdeHandle(window)) {
            logger.warning { "Ignoring focusWindow for non-KDE handle: $window" }
            return
        }
        callMethod("focusActiveWindow")
    }

    private fun isKdeHandle(window: WindowHandle): Boolean {
        val wayland = window as? WindowHandle.Wayland
        return wayland != null && wayland.backend == BACKEND_NAME
    }

    private fun callMethod(
        method: String,
        args: List<String> = emptyList(),
    ): String? {
        val client = findQdbusOrGdbus() ?: return null
        val command = buildMethodArgs(client, method, args)
        return runDbusClient(client, command)
    }

    private fun buildIntrospectArgs(client: String): List<String> {
        return if (client.endsWith("qdbus") || client.endsWith("/qdbus")) {
            listOf(SERVICE_NAME, OBJECT_PATH)
        } else {
            listOf("introspect", "--session", "--dest", SERVICE_NAME, "--object-path", OBJECT_PATH)
        }
    }

    private fun buildMethodArgs(
        client: String,
        method: String,
        args: List<String>,
    ): List<String> {
        return if (client.endsWith("qdbus") || client.endsWith("/qdbus")) {
            listOf(SERVICE_NAME, OBJECT_PATH, "$INTERFACE_NAME.$method") + args
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
            ) + args
        }
    }

    private fun parseActiveWindowFlag(output: String): Boolean? {
        val match = ACTIVE_WINDOW_REGEX.matchEntire(output) ?: return null
        return match.groupValues[1].toBooleanStrictOrNull()
    }
}
