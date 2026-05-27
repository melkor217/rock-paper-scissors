package com.rpsonline.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpsonline.app.data.monitoring.FirebaseConnectionMonitor
import com.rpsonline.app.data.preferences.AppThemeStyle
import com.rpsonline.app.data.preferences.ThemePreferences
import com.rpsonline.app.navigation.RpsNavGraph
import com.rpsonline.app.ui.components.AppearanceMenuButton
import com.rpsonline.app.ui.components.FirebasePingMeter
import com.rpsonline.app.ui.theme.RpsTheme

@Composable
fun RpsApp() {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    var themeStyle by remember { mutableStateOf(themePreferences.get()) }

    val scope = rememberCoroutineScope()
    val connectionMonitor = remember { FirebaseConnectionMonitor(context) }
    val connectionStatus by connectionMonitor.status.collectAsStateWithLifecycle()

    LifecycleResumeEffect(connectionMonitor) {
        connectionMonitor.start(scope)
        onPauseOrDispose { connectionMonitor.stop() }
    }
    DisposableEffect(Unit) {
        onDispose { connectionMonitor.stop() }
    }

    RpsTheme(style = themeStyle) {
        Box(modifier = Modifier.fillMaxSize()) {
            RpsNavGraph()
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FirebasePingMeter(status = connectionStatus)
                AppearanceMenuButton(
                    currentStyle = themeStyle,
                    onStyleSelected = { style ->
                        themeStyle = style
                        themePreferences.set(style)
                    },
                )
            }
        }
    }
}
