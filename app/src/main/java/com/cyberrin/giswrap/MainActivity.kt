package com.cyberrin.giswrap

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cyberrin.giswrap.ui.navigation.GisWrapApp
import com.cyberrin.giswrap.widget.WidgetRefreshWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        requestFastestDisplayMode()

        WidgetRefreshWorker.schedule(applicationContext)

        setContent { GisWrapApp() }
    }

    private fun requestFastestDisplayMode() {
        val screen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            // The pre-30 route to the same object.
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return

        val current = screen.mode?.toScreenMode() ?: return
        val fastest = fastestModeLike(
            modes = screen.supportedModes.orEmpty().map { it.toScreenMode() },
            current = current,
        ) ?: return

        window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.id }
    }
}

private fun Display.Mode.toScreenMode() =
    ScreenMode(modeId, physicalWidth, physicalHeight, refreshRate)

data class ScreenMode(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

fun fastestModeLike(modes: List<ScreenMode>, current: ScreenMode): ScreenMode? =
    modes
        .filter { it.width == current.width && it.height == current.height }
        .maxByOrNull { it.refreshRate }

        ?.takeIf { it.refreshRate > current.refreshRate + 0.5f }
