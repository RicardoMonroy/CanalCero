package io.github.ricardomonroy.canalcero.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import io.github.ricardomonroy.canalcero.mesh.TipoEmergencia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import io.github.ricardomonroy.canalcero.mesh.noLeidos

@Composable
fun Chats(
    st: MeshState,
    malla: Malla,
    abrir: (Chat) -> Unit,
) {
    var opciones by remember { mutableStateOf<Chat?>(null) }
    opciones?.let { OpcionesChat(st, it, malla.ble) { opciones = null } }

    val pendientes = st.noLeidos(malla.leidos)
    val ultimos = st.mensajes.groupBy { it.chat(st.miNodo) }.mapValues { it.value.last() }
    // Con mensajes: el más reciente arriba. Canales sin mensajes al final, en su orden.
    val chats =
        (ultimos.keys.sortedByDescending { ultimos[it]!!.hora } + st.canales.keys.sorted().map { Chat.Canal(it) }).distinct()

    var sos by remember { mutableStateOf(false) }
    if (sos) DestinoSos(st, malla) { sos = false }
    if (chats.isEmpty()) {
        Vacio(R.drawable.ic_chat, "Aún no hay conversaciones", "Cuando tu nodo termine de conectarse verás aquí sus canales. Para escribirle a alguien, tócalo en Nodos.")
        return
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(chats, key = { it.toString() }) { c ->
            val ultimo = ultimos[c]
            val n = pendientes[c] ?: 0
            Row(
                Modifier.fillMaxWidth().combinedClickable(onLongClick = { opciones = c }) { abrir(c) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarChat(st, c)
                Column(Modifier.weight(1f)) {
                    Text(st.nombre(c), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val vista =
                        when {
                            ultimo == null -> if (c is Chat.Canal && c.indice == 0) "Canal público · cualquiera cerca lo lee" else "Sin mensajes todavía"
                            c is Chat.Canal && ultimo.de != st.miNodo -> "${st.nombre(ultimo.de)}: ${ultimo.texto}"
                            ultimo.de == st.miNodo -> "Tú: ${ultimo.texto}"
                            else -> ultimo.texto
                        }
                    Text(
                        vista,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (n > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ultimo?.let {
                        Text(horaCorta(it.hora), style = MaterialTheme.typography.labelSmall, color = if (n > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (n > 0) Insignia(n)
                }
            }
        }
    }
    ExtendedFloatingActionButton(
        onClick = { sos = true },
        containerColor = RojoSos,
        contentColor = Color.White,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    ) { Text("🆘  SOS") }
    }
}

/** SOS desde la lista: elegir a quién (privado por defecto; el público llega a cualquiera cerca que pueda ayudar). */
@Composable
private fun DestinoSos(
    st: MeshState,
    malla: Malla,
    cerrar: () -> Unit,
) {
    val opciones = st.canales.keys.sortedWith(compareBy { if (it == 0) 99 else it }).map { Chat.Canal(it) }
    var elegido by remember { mutableStateOf(opciones.firstOrNull()) }
    AlertDialog(
        onDismissRequest = cerrar,
        icon = { Text("🆘", style = MaterialTheme.typography.headlineMedium) },
        title = { Text("¿Enviar SOS?") },
        text = {
            Column {
                Text("Suena como alarma a quien lo reciba y tu ubicación exacta se comparte en vivo 1 hora.")
                opciones.forEach { c ->
                    Row(Modifier.fillMaxWidth().selectable(selected = elegido == c, onClick = { elegido = c }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = elegido == c, onClick = null)
                        Text("  " + st.nombre(c) + if (c.indice == 0) " (público: cualquiera cerca)" else "")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                elegido?.let { malla.emergencia(it, TipoEmergencia.SOS) }
                cerrar()
            }, enabled = elegido != null) { Text("Enviar SOS", color = RojoSos) }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
