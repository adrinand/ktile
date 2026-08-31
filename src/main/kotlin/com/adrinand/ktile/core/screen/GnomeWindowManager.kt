package com.adrinand.ktile.core.screen

import java.awt.Rectangle
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.ktile.core.screen.GnomeWindowManager")

private const val BACKEND_NAME = "gnome-shell"
private const val WAYLAND_TOKEN = "captured"
private const val SERVICE_NAME = "org.gnome.Shell.Extensions.KTile"
private const val OBJECT_PATH = "/org/gnome/Shell/Extensions/KTile"
private const val INTERFACE_NAME = "org.gnome.Shell.Extensions.KTile"
private val LEADING_BOOLEAN_REGEX = Regex("""\(\s*(true|false)""")

/**
 * [WindowManager] implementation for native Wayland windows on GNOME.
 *
 * This backend requires the companion GNOME Shell extension
 * (`ktile@adrinand`) to be installed and enabled. The extension stores a
 * reference to the captured active window and can move/resize it on demand.
 */
class GnomeWindowManager : WindowManager {
    /**
     * Returns `true` if the companion GNOME Shell extension is available on
     * the session bus and a D-Bus client is installed.
     */
    fun isAvailable(): Boolean {
        val client = findGdbus() ?: return false
        val output =
            runDbusClient(
                client,
                listOf("introspect", "--session", "--dest", SERVICE_NAME, "--object-path", OBJECT_PATH),
            )
        return output != null
    }

    override fun getActiveWindowId(): WindowHandle? {
        val output = callMethod("CaptureActiveWindow") ?: return null
        val success = parseLeadingBoolean(output.trim()) ?: return null
        return if (success) {
            WindowHandle.Wayland(BACKEND_NAME, WAYLAND_TOKEN)
        } else {
            null
        }
    }

    override fun setWindowBounds(
        window: WindowHandle,
        bounds: Rectangle,
    ) {
        if (!isGnomeHandle(window)) {
            logger.warning { "Ignoring setWindowBounds for non-GNOME handle: $window" }
            return
        }
        callMethod(
            "MoveResizeCapturedWindow",
            listOf(
                bounds.x.toString(),
                bounds.y.toString(),
                bounds.width.toString(),
                bounds.height.toString(),
            ),
        )
    }

    override fun focusWindow(window: WindowHandle) {
        if (!isGnomeHandle(window)) {
            logger.warning { "Ignoring focusWindow for non-GNOME handle: $window" }
            return
        }
        callMethod("FocusCapturedWindow")
    }

    private fun isGnomeHandle(window: WindowHandle): Boolean {
        val wayland = window as? WindowHandle.Wayland
        return wayland != null && wayland.backend == BACKEND_NAME
    }

    private fun callMethod(
        method: String,
        args: List<String> = emptyList(),
    ): String? {
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
            ) + args
        return runDbusClient(client, command)
    }

    private fun parseLeadingBoolean(output: String): Boolean? {
        val match = LEADING_BOOLEAN_REGEX.find(output) ?: return null
        return match.groupValues[1].toBooleanStrictOrNull()
    }
}
