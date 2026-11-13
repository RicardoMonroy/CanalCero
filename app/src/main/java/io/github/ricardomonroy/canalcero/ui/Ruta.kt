package io.github.ricardomonroy.canalcero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.mesh.Alerta
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.alertas
import io.github.ricardomonroy.canalcero.mesh.companeros
import io.github.ricardomonroy.canalcero.mesh.puntoCardinal
import io.github.ricardomonroy.canalcero.mesh.textoDistancia
import kotlinx.coroutines.delay

@Composable
fun Ruta(
    st: MeshState,
    malla: Malla,
) {
    // Refresca "hace X min" aunque no lleguen paquetes.
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            ahora = System.currentTimeMillis() / 1000
        }
    }
    val lista = companeros(st, ahora)
    val activas = alertas(lista, malla.grupo, malla.umbralKm * 1000.0)

    Column(Modifier.padding(vertical = 8.dp)) {
        if (!malla.compartirUbicacion) {
            Text("Activa \"Compartir mi ubicación\" en Mapa para ver distancias.", color = MaterialTheme.colorScheme.error)
        }
        Text("Marca a tu grupo: te aviso si alguien se aleja o pierde señal.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Avisar a más de", style = MaterialTheme.typography.bodySmall)
            listOf(1, 2, 5, 10).forEach { km ->
                FilterChip(selected = malla.umbralKm == km, onClick = { malla.cambiarUmbral(km) }, label = { Text("$km km") })
            }
        }
    }
    if (lista.isEmpty()) Text("No se ha oído a ningún nodo en las últimas 2 horas.")
    LazyColumn {
        items(lista, key = { it.num }) { c ->
            val alerta = activas[c.num]
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.num in malla.grupo, onCheckedChange = { malla.alternarGrupo(c.num) })
                Column(Modifier.weight(1f)) {
                    Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                    val hace = c.haceSeg?.let { if (it < 60) "ahora" else "hace ${it / 60} min" }
                    Text(
                        when (alerta) {
                            Alerta.SIN_SENAL -> "Sin señal · $hace"
                            Alerta.LEJOS -> "Se está alejando · $hace"
                            null -> hace.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alerta != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (c.distancia != null && c.rumbo != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Flecha absoluta (0° = norte), no relativa a hacia dónde apunta el teléfono.
                            Text("↑", Modifier.rotate(c.rumbo.toFloat()), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text(" " + puntoCardinal(c.rumbo))
                        }
                        Text((if (c.aproximada) "≈ " else "") + textoDistancia(c.distancia), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Text("sin posición", Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
}
