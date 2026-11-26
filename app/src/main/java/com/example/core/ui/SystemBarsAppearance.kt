package com.example.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun SystemBarsAppearance(
    lightStatusBars: Boolean,
    lightNavigationBars: Boolean = lightStatusBars
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.resolveActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}

private tailrec fun Context.resolveActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.resolveActivity()
    else -> null
}
