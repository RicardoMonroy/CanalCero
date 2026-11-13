package io.github.ricardomonroy.canalcero.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.R
import io.github.ricardomonroy.canalcero.mesh.Alerta
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.alcanceDirecto
import io.github.ricardomonroy.canalcero.mesh.alertas
import io.github.ricardomonroy.canalcero.mesh.csvAlcance
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

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!malla.usaUbicacion) {
            Text("Activa \"Compartir ubicación\" en Mapa para ver distancias.", color = MaterialTheme.colorScheme.error)
        }
        PruebaAlcance(malla)
        Text("Marca a tu grupo: te aviso si alguien se aleja o pierde señal.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Avisar a más de", style = MaterialTheme.typography.bodySmall)
            listOf(1, 2, 5, 10).forEach { km ->
                FilterChip(selected = malla.umbralKm == km, onClick = { malla.cambiarUmbral(km) }, label = { Text("$km km") })
            }
        }
    }
    if (lista.isEmpty()) {
        Vacio(R.drawable.ic_route, "Nadie cerca todavía", "Aquí verás a quién oye tu nodo en las últimas 2 horas, con su distancia y dirección.")
        return
    }
    LazyColumn {
        items(lista, key = { it.num }) { c ->
            val alerta = activas[c.num]
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = c.num in malla.grupo, onCheckedChange = { malla.alternarGrupo(c.num) })
                Avatar(st.nodos[c.num]?.user?.short_name?.ifBlank { null } ?: c.nombre.take(2), tonoDe(c.num), 40.dp)
                Spacer(Modifier.width(12.dp))
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
        }
    }
}

/** Registra señal, saltos y distancia de cada paquete recibido mientras te alejas; se exporta como CSV. */
@Composable
private fun PruebaAlcance(malla: Malla) {
    val ctx = LocalContext.current
    val ms = malla.muestras
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Prueba de alcance", style = MaterialTheme.typography.titleMedium)
            if (!malla.alcanceActivo && ms.isEmpty()) {
                Text(
                    "Deja un nodo fijo y aléjate con el otro. Se registra cada paquete que llega: señal, saltos y distancia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val directo = alcanceDirecto(ms)
                Text(
                    "${ms.size} paquetes" + (directo?.let { " · alcance directo máx. ${textoDistancia(it)}" } ?: "") + if (malla.alcanceActivo) " · registrando…" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ms.takeLast(3).reversed().forEach { m ->
                    Text(
                        "${m.nombre}: ${m.rssi} dBm · SNR %.1f".format(m.snr) +
                            (m.saltos?.let { if (it == 0) " · directo" else " · $it saltos" } ?: "") +
                            (m.distancia?.let { " · " + (if (m.aproximada) "≈ " else "") + textoDistancia(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { malla.alternarAlcance(!malla.alcanceActivo) }) { Text(if (malla.alcanceActivo) "Detener" else "Iniciar") }
                if (ms.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_SUBJECT, "Prueba de alcance CanalCero").putExtra(Intent.EXTRA_TEXT, csvAlcance(ms))
                        ctx.startActivity(Intent.createChooser(i, "Exportar CSV"))
                    }) { Text("Exportar CSV") }
                }
            }
        }
    }
}
