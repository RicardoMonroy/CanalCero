package io.github.ricardomonroy.canalcero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.Entrega
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_TEXTO
import io.github.ricardomonroy.canalcero.mesh.MeshBle
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.Mensaje
import java.text.DateFormat
import java.util.Date

@Composable
fun PantallaChat(
    st: MeshState,
    chat: Chat,
    ble: MeshBle,
    enviar: (String) -> Unit,
    volver: () -> Unit,
    enviarUbicacion: () -> Unit,
) {
    val mensajes = st.mensajes.filter { it.chat(st.miNodo) == chat }
    val lista = rememberLazyListState()
    var aBorrar by remember { mutableStateOf<Mensaje?>(null) }
    aBorrar?.let { BorrarMensaje(it, ble) { aBorrar = null } }
    LaunchedEffect(mensajes.size) { if (mensajes.isNotEmpty()) lista.animateScrollToItem(mensajes.size - 1) }

    Column(Modifier.imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = volver) { Text("‹ Chats") }
            Text(
                (if (chat is Chat.Canal) "# " else "Directo · ") + st.nombre(chat),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lista, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mensajes, key = { "${it.de}-${it.id}" }) { m ->
                val mio = m.entrega != Entrega.RECIBIDO
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mio) Alignment.End else Alignment.Start) {
                    Column(
                        Modifier
                            .widthIn(max = 300.dp)
                            .combinedClickable(onLongClick = { aBorrar = m }) {}
                            .background(
                                if (mio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp),
                            ).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (!mio && chat is Chat.Canal) {
                            Text(st.nombre(m.de), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            m.texto,
                            color = if (mio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val hora = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.hora))
                    val marca =
                        when (m.entrega) {
                            Entrega.ENVIANDO -> " · enviando…"
                            Entrega.ENTREGADO -> " · entregado"
                            Entrega.FALLO -> " · no entregado"
                            Entrega.RECIBIDO -> ""
                        }
                    Text(
                        hora + marca,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (m.entrega == Entrega.FALLO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (chat is Chat.Directo) {
            val tengo = st.miNodo?.let { st.nodos[it]?.position } != null
            TextButton(onClick = enviarUbicacion, enabled = tengo) {
                Text(if (tengo) "📍 Enviar mi ubicación exacta" else "📍 Activa \"Compartir mi ubicación\" en Mapa para enviarla")
            }
        }
        Entrada(enviar)
    }
}

@Composable
private fun Entrada(enviar: (String) -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    val bytes = texto.encodeToByteArray().size
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = texto,
            // LoRa limita el tamaño del paquete: se mide en bytes UTF-8, no en caracteres.
            onValueChange = { if (it.encodeToByteArray().size <= MAX_BYTES_TEXTO) texto = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Mensaje") },
            supportingText = { Text("$bytes/$MAX_BYTES_TEXTO") },
        )
        Button(
            onClick = {
                enviar(texto.trim())
                texto = ""
            },
            enabled = texto.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Enviar") }
    }
}
