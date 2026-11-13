package io.github.ricardomonroy.canalcero.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun Icono(
    @DrawableRes id: Int,
    descripcion: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) = Icon(painterResource(id), descripcion, modifier, tint)

// Fondos de avatar que se leen bien en claro y oscuro (texto oscuro encima).
private val TONOS =
    listOf(
        Color(0xFFFFD8C2),
        Color(0xFFCFE3F7),
        Color(0xFFD5EDC8),
        Color(0xFFF2D5E9),
        Color(0xFFE6DCF9),
        Color(0xFFF7E7B4),
        Color(0xFFC8ECE4),
    )

/** Color fijo por nodo: el mismo amigo siempre se ve igual. */
fun tonoDe(num: Int): Color = TONOS[Math.floorMod(num, TONOS.size)]

@Composable
fun Avatar(
    texto: String,
    fondo: Color,
    tamano: Dp = 44.dp,
    tinta: Color = Grafito,
    @DrawableRes icono: Int? = null,
) = Box(
    Modifier.size(tamano).clip(CircleShape).background(fondo),
    contentAlignment = Alignment.Center,
) {
    if (icono != null) {
        Icono(icono, null, Modifier.size(tamano / 2), tinta)
    } else {
        Text(
            texto.take(4),
            color = tinta,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (texto.length <= 2) 16.sp else 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Cuatro barras, como la señal del teléfono. [nivel] 0–4. */
@Composable
fun BarrasSenal(
    nivel: Int,
    modifier: Modifier = Modifier,
) {
    val activo = if (nivel <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val inactivo = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier.semantics { contentDescription = "Señal $nivel de 4" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        (1..4).forEach { i ->
            Box(Modifier.width(4.dp).height((4 + i * 3).dp).clip(RoundedCornerShape(1.dp)).background(if (i <= nivel) activo else inactivo))
        }
    }
}

/** Título de sección en listas de ajustes. */
@Composable
fun Seccion(texto: String) =
    Text(
        texto.uppercase(),
        Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )

/** Pantalla vacía con ícono y explicación, en lugar de una lista sin nada. */
@Composable
fun Vacio(
    @DrawableRes icono: Int,
    titulo: String,
    detalle: String,
) = Column(
    Modifier.fillMaxWidth().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Icono(icono, null, Modifier.size(48.dp), MaterialTheme.colorScheme.outline)
    Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

fun hace(seg: Long): String =
    when {
        seg < 60 -> "ahora"
        seg < 3600 -> "hace ${seg / 60} min"
        seg < 86400 -> "hace ${seg / 3600} h"
        else -> "hace ${seg / 86400} d"
    }

/** Hora para listas: "12:40" hoy, "ayer", o la fecha corta. */
fun horaCorta(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val hoy = Calendar.getInstance()
    val dias = (hoy.get(Calendar.YEAR) - c.get(Calendar.YEAR)) * 366 + hoy.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR)
    return when (dias) {
        0 -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
        1 -> "ayer"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ms))
    }
}

/** Separador de día en la conversación. */
fun dia(ms: Long): String =
    when (horaCorta(ms)) {
        "ayer" -> "Ayer"
        else -> if (horaCorta(ms).contains(':')) "Hoy" else DateFormat.getDateInstance(DateFormat.LONG).format(Date(ms))
    }
