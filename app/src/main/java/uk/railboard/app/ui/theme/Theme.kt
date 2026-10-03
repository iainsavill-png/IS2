package uk.railboard.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Navy = Color(0xFF1D3557)
private val Amber = Color(0xFFF4A261)

/** Status colours, tuned for legibility on both light and dark surfaces. */
object StatusColors {
    val onTime = Color(0xFF2E9E5B)
    val late = Color(0xFFE08A00)
    val cancelled = Color(0xFFD64545)
}

@Composable
fun RailBoardTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> darkColorScheme(primary = Color(0xFF9DB8E0), secondary = Amber)
        else -> lightColorScheme(primary = Navy, secondary = Amber)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
