package io.github.ricardomonroy.canalcero.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.R
import io.github.ricardomonroy.canalcero.mesh.CERCA_SEG
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.distancia
import io.github.ricardomonroy.canalcero.mesh.esAproximada
import io.github.ricardomonroy.canalcero.mesh.grados
import io.github.ricardomonroy.canalcero.mesh.idNodo
import io.github.ricardomonroy.canalcero.mesh.nivelSenal
import io.github.ricardomonroy.canalcero.mesh.puntoCardinal
import io.github.ricardomonroy.canalcero.mesh.rumbo
import io.github.ricardomonroy.canalcero.mesh.textoDistancia
import kotlinx.coroutines.delay
import org.meshtastic.proto.NodeInfo

@Composable
fun Nodos(
    st: MeshState,
    malla: Malla,
    abrirChat: (Chat) -> Unit,
    verEnMapa: (Int) -> Unit,
) {
    var ficha by remember { mutableStateOf<Int?>(null) }
    var soloCerca by rememberSaveable { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    ficha?.let { n -> FichaNodo(st, malla, n, { ficha = null }, abrirChat, verEnMapa) }
    val ahora = System.currentTimeMillis() / 1000

    val yo = st.miNodo?.let { st.nodos[it] }
    val otros = st.nodos.values.filter { it.num != st.miNodo && (!soloCerca || (it.last_heard > 0 && ahora - it.last_heard <= CERCA_SEG)) }
    // Directos = nos oyen sin repetidor (hops_away 0). Sin dato de saltos, se cuentan como "por repetidor".
    val directos = otros.filter { it.hops_away == 0 }.sortedByDescending { it.last_heard }
    val lejanos = otros.filter { it.hops_away != 0 }.sortedWith(compareBy<NodeInfo> { it.hops_away ?: 99 }.thenByDescending { it.last_heard })

    LazyColumn {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    val falta = malla.ble.buscarNodos()
                    aviso =
                        if (falta > 0) {
                            "Ya buscaste hace poco. Espera ${(falta + 59) / 60} min para no saturar el canal."
                        } else {
                            "Preguntando por el canal Principal… los nodos que te oigan aparecerán en unos segundos."
                        }
                }, Modifier.fillMaxWidth()) {
                    Icono(R.drawable.ic_bluetooth_searching, null)
                    Text("  Buscar nodos cercanos")
                }
                aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !soloCerca, onClick = { soloCerca = false }, label = { Text("Todos (${st.nodos.keys.count { it != st.miNodo }})") })
                    FilterChip(selected = soloCerca, onClick = { soloCerca = true }, label = { Text("Oídos en la última hora") })
                }
            }
        }
        yo?.let { n -> item(key = n.num) { FilaNodo(st, n, ahora) { ficha = n.num } } }
        if (otros.isEmpty()) {
            item {
                Vacio(
                    R.drawable.ic_cell_tower,
                    if (soloCerca) "Nadie en la última hora" else "Sin otros nodos todavía",
                    "Toca Buscar nodos cercanos o espera a que se anuncien (lo hacen cada ~3 h).",
                )
            }
        }
        if (directos.isNotEmpty()) {
            item { Seccion("Directos · te oyen sin repetidor") }
            items(directos, key = { it.num }) { n -> FilaNodo(st, n, ahora) { ficha = n.num } }
        }
        if (lejanos.isNotEmpty()) {
            item { Seccion("Por repetidor") }
            items(lejanos, key = { it.num }) { n -> FilaNodo(st, n, ahora) { ficha = n.num } }
        }
    }
}

@Composable
private fun FilaNodo(
    st: MeshState,
    n: NodeInfo,
    ahora: Long,
    tocar: () -> Unit,
) {
    val mio = n.num == st.miNodo
    Row(
        Modifier.fillMaxWidth().clickable(onClick = tocar).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(corto(st, n.num), if (mio) MaterialTheme.colorScheme.primary else tonoDe(n.num), tinta = if (mio) MaterialTheme.colorScheme.onPrimary else Grafito)
        Column(Modifier.weight(1f)) {
            Text(st.nombre(n.num) + if (mio) " (tú)" else "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(resumen(st, n, ahora), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (!mio && n.last_heard > 0) BarrasSenal(nivelSenal(n.snr))
    }
}

private fun corto(
    st: MeshState,
    num: Int,
) = st.nodos[num]?.user?.short_name?.ifBlank { null } ?: idNodo(num).takeLast(4)

private fun resumen(
    st: MeshState,
    n: NodeInfo,
    ahora: Long,
): String {
    if (n.num == st.miNodo) return "Tu nodo · ${idNodo(n.num)}"
    val desde = st.miNodo?.let { st.nodos[it]?.position.grados() }
    val donde = n.position.grados()
    return listOfNotNull(
        if (n.last_heard > 0) hace(ahora - n.last_heard) else "nunca oído",
        n.hops_away?.let { if (it == 0) "directo" else "$it saltos" },
        if (desde != null && donde != null) {
            (if (n.position!!.esAproximada()) "≈ " else "") + textoDistancia(distancia(desde, donde)) + " " + puntoCardinal(rumbo(desde, donde))
        } else {
            null
        },
    ).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FichaNodo(
    st: MeshState,
    malla: Malla,
    num: Int,
    cerrar: () -> Unit,
    abrirChat: (Chat) -> Unit,
    verEnMapa: (Int) -> Unit,
) {
    val n = st.nodos[num] ?: return
    val mio = num == st.miNodo
    val ahora = System.currentTimeMillis() / 1000
    ModalBottomSheet(onDismissRequest = cerrar) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(corto(st, num), tonoDe(num), 56.dp)
                Column {
                    Text(st.nombre(num), style = MaterialTheme.typography.titleLarge)
                    Text(idNodo(num), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (n.last_heard > 0) Dato("Última vez", hace(ahora - n.last_heard))
            n.hops_away?.let { Dato("Ruta", if (it == 0) "Directo, sin repetidores" else "$it saltos") }
            if (!mio && n.snr != 0f) Dato("Señal", "%.1f dB".format(n.snr))
            n.user?.hw_model?.let { Dato("Equipo", it.name.replace('_', ' ')) }
            if (!mio) {
                Traceroute(st, malla, num)
                FilledTonalButton(onClick = {
                    cerrar()
                    abrirChat(Chat.Directo(num))
                }, Modifier.fillMaxWidth()) {
                    Icono(R.drawable.ic_chat, null)
                    Text("  Enviar mensaje")
                }
                if (n.position.grados() != null) {
                    OutlinedButton(onClick = {
                        cerrar()
                        verEnMapa(num)
                    }, Modifier.fillMaxWidth()) {
                        Icono(R.drawable.ic_map, null)
                        Text("  Ver en el mapa")
                    }
                }
                OutlinedButton(onClick = { malla.alternarGrupo(num) }, Modifier.fillMaxWidth()) {
                    Icono(R.drawable.ic_group, null)
                    Text(if (num in malla.grupo) "  Quitar de mi grupo de ruta" else "  Agregar a mi grupo de ruta")
                }
            }
        }
    }
}

/** "Probar ruta": por qué nodos pasa un mensaje hasta [num] y con qué señal se oye cada salto. */
@Composable
private fun Traceroute(
    st: MeshState,
    malla: Malla,
    num: Int,
) {
    var enviada by remember { mutableStateOf<Long?>(null) }
    val t = st.trazas[num]
    // Respuesta llega después de enviar: solo cuenta si es más nueva que la petición.
    val nueva = t != null && (enviada == null || t.hora >= enviada!!)
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enviada) {
        while (enviada != null && System.currentTimeMillis() - enviada!! < 70_000) {
            delay(1_000)
            ahora = System.currentTimeMillis()
        }
    }
    Seccion("Ruta")
    when {
        t != null && nueva -> {
            SaltosRuta(st, "Ida", t.ida, t.snrIda)
            if (t.vuelta.size > 1 && t.snrVuelta.isNotEmpty()) SaltosRuta(st, "Vuelta", t.vuelta, t.snrVuelta)
            Text("Probada ${horaCorta(t.hora)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        enviada != null && ahora - enviada!! < 60_000 -> Text("Esperando respuesta… (hasta 1 min)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        enviada != null -> Text("Sin respuesta: está fuera de alcance o apagado.", color = MaterialTheme.colorScheme.error)
    }
    OutlinedButton(onClick = {
        enviada = System.currentTimeMillis()
        malla.ble.trazarRuta(num)
    }, Modifier.fillMaxWidth()) {
        Icono(R.drawable.ic_route, null)
        Text("  Probar ruta")
    }
}

@Composable
private fun SaltosRuta(
    st: MeshState,
    titulo: String,
    nodos: List<Int>,
    snr: List<Float?>,
) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        titulo + if (nodos.size <= 2) " · directo" else " · ${nodos.size - 2} repetidor" + if (nodos.size > 3) "es" else "",
        style = MaterialTheme.typography.labelLarge,
    )
    nodos.forEachIndexed { i, n ->
        Text(
            buildString {
                append(if (i == 0) "● " else "↓ ")
                append(st.nombre(n))
                // snr[i-1] = cómo oyó el nodo i al anterior.
                if (i > 0) append(snr.getOrNull(i - 1)?.let { "   (señal %.1f dB)".format(it) } ?: "   (señal ?)")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
