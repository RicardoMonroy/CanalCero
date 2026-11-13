package io.github.ricardomonroy.canalcero.ui

import androidx.compose.foundation.combinedClickable
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

    if (chats.isEmpty()) {
        Vacio(R.drawable.ic_chat, "Aún no hay conversaciones", "Cuando tu nodo termine de conectarse verás aquí sus canales. Para escribirle a alguien, tócalo en Nodos.")
        return
    }
    LazyColumn {
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
}
