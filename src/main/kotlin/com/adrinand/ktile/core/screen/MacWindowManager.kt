package com.adrinand.ktile.core.screen

import java.awt.Rectangle
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.ktile.core.screen.MacWindowManager")

/**
 * [WindowManager] implementation for macOS using the Accessibility API.
 *
 * The implementation captures the frontmost application's process ID and
 * operates on that application's focused window. macOS requires the user to
 * grant Accessibility permissions to KTile before these calls succeed.
 */
class MacWindowManager : WindowManager {
    private val app = ApplicationServices.INSTANCE

    override fun getActiveWindowId(): WindowHandle? {
        val systemWide = app.AXUIElementCreateSystemWide()
        return try {
            if (systemWide.pointer == null) {
                logger.warning { "Failed to create system-wide accessibility element" }
                null
            } else {
                buildMacHandle(systemWide)
            }
        } finally {
            release(systemWide)
        }
    }

    override fun setWindowBounds(
        window: WindowHandle,
        bounds: Rectangle,
    ) {
        val macWindow = window as? WindowHandle.Mac
        if (macWindow == null) {
            logger.warning { "Ignoring setWindowBounds for non-macOS handle: $window" }
            return
        }
        val appElement = app.AXUIElementCreateApplication(macWindow.pid.toInt())
        if (appElement.pointer == null) {
            logger.warning { "Failed to create accessibility element for pid ${macWindow.pid}" }
            return
        }
        try {
            val targetWindow = focusedWindow(app, appElement) ?: return
            try {
                setPosition(app, targetWindow, bounds.x.toDouble(), bounds.y.toDouble())
                setSize(app, targetWindow, bounds.width.toDouble(), bounds.height.toDouble())
            } finally {
                release(targetWindow)
            }
        } finally {
            release(appElement)
        }
    }

    override fun focusWindow(window: WindowHandle) {
        val macWindow = window as? WindowHandle.Mac ?: return
        val appElement = app.AXUIElementCreateApplication(macWindow.pid.toInt())
        if (appElement.pointer == null) {
            return
        }
        try {
            val targetWindow = focusedWindow(app, appElement) ?: return
            try {
                app.AXUIElementPerformAction(targetWindow, kAXRaiseAction)
            } finally {
                release(targetWindow)
            }
        } finally {
            release(appElement)
        }
    }

    private fun buildMacHandle(systemWide: AXUIElementRef): WindowHandle? {
        val focusedAppPointer = copyAttribute(app, systemWide, kAXFocusedApplicationAttribute) ?: return null
        val focusedApp = AXUIElementRef(focusedAppPointer.pointer)
        try {
            val pid = readPid(app, focusedApp) ?: return null
            val title = readWindowTitle(app, focusedApp)
            return WindowHandle.Mac(pid = pid, title = title ?: "")
        } finally {
            release(focusedApp)
        }
    }
}
