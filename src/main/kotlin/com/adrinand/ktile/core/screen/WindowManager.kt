package com.adrinand.ktile.core.screen

import java.awt.Rectangle

/**
 * Abstraction over the OS windowing layer used to read the active/focused window
 * and change its bounds.
 */
interface WindowManager {
    /**
     * Returns a platform-specific handle for the currently active window,
     * or `null` if it cannot be determined.
     */
    fun getActiveWindowId(): WindowHandle?

    /**
     * Moves and resizes [window] to [bounds] in screen coordinates.
     */
    fun setWindowBounds(
        window: WindowHandle,
        bounds: Rectangle,
    )

    /**
     * Requests that [window] become the active/focused window.
     */
    fun focusWindow(window: WindowHandle)
}
