package com.adrinand.ktile.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.adrinand.ktile.viewmodel.SettingsViewModel

@Composable
fun App(settingsViewModel: SettingsViewModel) {
    MaterialTheme {
        LayoutPreviewScreen(settingsViewModel)
    }
}
