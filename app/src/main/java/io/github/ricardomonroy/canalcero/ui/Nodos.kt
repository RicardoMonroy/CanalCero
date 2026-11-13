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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.R
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
import org.meshtastic.proto.NodeInfo

@Composable
fun Nodos(
    st: MeshState,
    malla: Malla,
    abrirChat: (Chat) -> Unit,
    verEnMapa: (Int) -> Unit,
) {
    var ficha by remember { mutableStateOf<Int?>(null) }
    ficha?.let { n -> FichaNodo(st, malla, n, { ficha = null }, abrirChat, verEnMapa) }
    val ahora = System.currentTimeMillis() / 1000
    // Tu nodo primero; luego por quién se oyó más recientemente.
    val nodos = st.nodos.values.sortedWith(compareByDescending<NodeInfo> { it.num == st.miNodo }.thenByDescending { it.last_heard })
    if (nodos.isEmpty()) {
        Vacio(R.drawable.ic_cell_tower, "Sin nodos todavía", "Aparecerán aquí los nodos que tu nodo escuche por radio.")
        return
    }
    LazyColumn {
        items(nodos, key = { it.num }) { n -> FilaNodo(st, n, ahora) { ficha = n.num } }
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
