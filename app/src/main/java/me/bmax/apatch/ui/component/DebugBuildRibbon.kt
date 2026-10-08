package me.bmax.apatch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.bmax.apatch.R

/**
 * Corner ribbon marking a development build. Only ever composed behind
 * `BuildConfig.DEBUG`, so it never appears in a release APK. It is an overlay:
 * it takes no layout space and does not consume touches.
 */
@Composable
fun DebugBuildRibbon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .rotate(45f)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 36.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.debug_build_banner),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
