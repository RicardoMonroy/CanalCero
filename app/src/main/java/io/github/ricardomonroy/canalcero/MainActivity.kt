package io.github.ricardomonroy.canalcero

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import io.github.ricardomonroy.canalcero.ui.CanalCeroApp
import io.github.ricardomonroy.canalcero.ui.CanalCeroTheme
import io.github.ricardomonroy.canalcero.ui.TemaApp

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        malla.visible = true
    }

    override fun onStop() {
        malla.visible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val oscuro =
                when (malla.tema) {
                    TemaApp.SISTEMA -> isSystemInDarkTheme()
                    TemaApp.CLARO -> false
                    TemaApp.OSCURO -> true
                }
            // Íconos de la barra de estado acordes al tema de la app, no al del sistema.
            LaunchedEffect(oscuro) {
                val barra = if (oscuro) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barra, navigationBarStyle = barra)
            }
            CanalCeroTheme(oscuro) { CanalCeroApp() }
        }
    }
}
