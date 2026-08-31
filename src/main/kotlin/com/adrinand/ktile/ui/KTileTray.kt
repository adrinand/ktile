package com.adrinand.ktile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray

private const val TRAY_TOOLTIP = "KTile"
private const val SETTINGS_LABEL = "Settings"
private const val QUIT_LABEL = "Quit"

@Composable
fun ApplicationScope.KTileTray(
    icon: Painter,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
) {
    Tray(
        icon = icon,
        tooltip = TRAY_TOOLTIP,
        onAction = onToggle,
        menu = {
            Item(SETTINGS_LABEL, onClick = onSettings)
            Item(QUIT_LABEL, onClick = ::exitApplication)
        },
    )
}
