package me.bmax.apatch.ui.screen.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultimediaSettingsContent(
    snackBarHost: SnackbarHostState,
    flat: Boolean = false,
    highlightKey: String? = null,
) {
    MultimediaMusicSection(snackBarHost = snackBarHost, flat = flat, highlightKey = highlightKey)
    MultimediaSoundSection(snackBarHost = snackBarHost, flat = flat, highlightKey = highlightKey)
}
