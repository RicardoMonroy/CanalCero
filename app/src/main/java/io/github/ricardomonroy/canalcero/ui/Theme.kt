package io.github.ricardomonroy.canalcero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NaranjaSenal = Color(0xFFF26B1D)
val Grafito = Color(0xFF1C1F23)
val Arena = Color(0xFFF4EFE6)

// Reservados: solo para "Estoy bien" y SOS.
val VerdeOk = Color(0xFF2FB36B)
val RojoSos = Color(0xFFE5383B)

// Tema oscuro fijo: pantallas OLED gastan menos batería en un apagón.
private val Esquema =
    darkColorScheme(
        primary = NaranjaSenal,
        onPrimary = Grafito,
        background = Grafito,
        onBackground = Arena,
        surface = Grafito,
        onSurface = Arena,
        error = RojoSos,
    )

@Composable
fun CanalCeroTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Esquema, content = content)
