package com.kvyii.maelle

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvyii.maelle.data.AppTheme
import com.kvyii.maelle.ui.MaelleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate; swaps the splash theme out for
        // the app theme once the first frame is ready.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MaelleApplication).container
        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle(AppTheme.System)
            MaelleTheme(theme) {
                MaelleApp(container)
            }
        }
    }
}

/** "Claire" — a calm light theme with a soft violet accent. */
private val ClaireColors = lightColorScheme(
    primary = Color(0xFF6D4FA3),
    onPrimary = Color.White,
    secondary = Color(0xFF17847A),
    onSecondary = Color.White,
    background = Color(0xFFFDFBF7),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDFBF7),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFEFEAE2),
    onSurfaceVariant = Color(0xFF4A4458),
)

/** "Obscur" — pure-black OLED with hacker magenta and teal highlights. */
private val ObscurColors = darkColorScheme(
    primary = Color(0xFFFF50FF),
    onPrimary = Color(0xFF20002A),
    secondary = Color(0xFF00E5C7),
    onSecondary = Color(0xFF00201B),
    tertiary = Color(0xFF00E5C7),
    background = Color.Black,
    onBackground = Color(0xFFE4E0EC),
    surface = Color.Black,
    onSurface = Color(0xFFE4E0EC),
    surfaceVariant = Color(0xFF15121D),
    onSurfaceVariant = Color(0xFFA9A3B8),
    surfaceContainer = Color(0xFF0C0A12),
    surfaceContainerLow = Color(0xFF080710),
    surfaceContainerHigh = Color(0xFF141020),
    outline = Color(0xFF3C3550),
)

@Composable
fun MaelleTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val colors = when (theme) {
        AppTheme.Claire -> ClaireColors
        AppTheme.Obscur -> ObscurColors
        AppTheme.System -> {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
