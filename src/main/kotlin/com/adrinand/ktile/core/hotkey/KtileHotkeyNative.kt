package com.adrinand.ktile.core.hotkey

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface KtileHotkeyNative : Library {
    @Suppress("FunctionName")
    fun ktile_hotkey_init(): Pointer

    @Suppress("FunctionName")
    fun ktile_hotkey_register(
        manager: Pointer,
        combo: String,
        callback: KtileHotkeyCallback,
    ): Int

    @Suppress("FunctionName")
    fun ktile_hotkey_shutdown(manager: Pointer)

    interface KtileHotkeyCallback : Callback {
        fun invoke()
    }

    companion object {
        val INSTANCE: KtileHotkeyNative by lazy {
            Native.load("ktile_hotkey", KtileHotkeyNative::class.java)
        }
    }
}
