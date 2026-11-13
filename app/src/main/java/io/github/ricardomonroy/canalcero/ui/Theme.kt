package io.github.ricardomonroy.canalcero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta de marca (docs/VISION.md).
val NaranjaSenal = Color(0xFFF26B1D)
val Grafito = Color(0xFF1C1F23)
val Arena = Color(0xFFF4EFE6)

// Reservados: solo para "Estoy bien"/conectado y SOS/errores.
val VerdeOk = Color(0xFF2FB36B)
val RojoSos = Color(0xFFE5383B)
val Ambar = Color(0xFFE0A100)

/** Cómo decide la app entre claro y oscuro. El mapa tiene su propio interruptor. */
enum class TemaApp(
    val nombre: String,
) {
    SISTEMA("Según el sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro"),
}

// En claro el naranja de marca no llega a 4.5:1 sobre blanco: el texto usa un naranja más oscuro.
private val Claro =
    lightColorScheme(
        primary = Color(0xFFB0470C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCA),
        onPrimaryContainer = Color(0xFF3A1300),
        secondary = Color(0xFF6B5D50),
        secondaryContainer = Color(0xFFF6DFCF),
        onSecondaryContainer = Color(0xFF2A1A0E),
        tertiary = VerdeOk,
        background = Color(0xFFFBF8F3),
        onBackground = Grafito,
        surface = Color(0xFFFBF8F3),
        onSurface = Grafito,
        surfaceVariant = Color(0xFFEFE9DF),
        onSurfaceVariant = Color(0xFF5A5650),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7F3EC),
        surfaceContainer = Color(0xFFF3EEE6),
        surfaceContainerHigh = Color(0xFFEDE7DD),
        surfaceContainerHighest = Color(0xFFE7E0D5),
        outline = Color(0xFF8A847B),
        outlineVariant = Color(0xFFD6CFC4),
        error = Color(0xFFBA1A1A),
    )

private val Oscuro =
    darkColorScheme(
        primary = NaranjaSenal,
        onPrimary = Grafito,
        primaryContainer = Color(0xFF5A2A0C),
        onPrimaryContainer = Color(0xFFFFDBCA),
        secondary = Color(0xFFD8C2B2),
        secondaryContainer = Color(0xFF4A3326),
        onSecondaryContainer = Color(0xFFF6DFCF),
        tertiary = VerdeOk,
        background = Color(0xFF16181B),
        onBackground = Arena,
        surface = Color(0xFF16181B),
        onSurface = Arena,
        surfaceVariant = Color(0xFF262A2F),
        onSurfaceVariant = Color(0xFFC9C3BA),
        surfaceContainerLowest = Color(0xFF111316),
        surfaceContainerLow = Color(0xFF1A1D20),
        surfaceContainer = Color(0xFF1E2125),
        surfaceContainerHigh = Color(0xFF262A2F),
        surfaceContainerHighest = Color(0xFF31353A),
        outline = Color(0xFF8A847B),
        outlineVariant = Color(0xFF3A3E43),
        error = Color(0xFFFFB4AB),
    )

private val Tipografia =
    Typography().run {
        copy(
            titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }

@Composable
fun CanalCeroTheme(
    oscuro: Boolean,
    content: @Composable () -> Unit,
) = MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipografia, content = content)
