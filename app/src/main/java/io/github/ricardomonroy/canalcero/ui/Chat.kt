package io.github.ricardomonroy.canalcero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.R
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.Entrega
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_TEXTO
import io.github.ricardomonroy.canalcero.mesh.MeshBle
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.Mensaje
import io.github.ricardomonroy.canalcero.mesh.TEXTO_UBICACION_ENVIADA
import io.github.ricardomonroy.canalcero.mesh.TEXTO_UBICACION_RECIBIDA
import java.text.DateFormat
import java.util.Date

/** Conversación. La barra superior (nombre del chat) la pone App. */
@Composable
fun PantallaChat(
    st: MeshState,
    chat: Chat,
    ble: MeshBle,
    malla: Malla,
    verEnMapa: (Int) -> Unit,
) {
    val mensajes = st.mensajes.filter { it.chat(st.miNodo) == chat }
    val lista = rememberLazyListState()
    var aBorrar by remember { mutableStateOf<Mensaje?>(null) }
    aBorrar?.let { BorrarMensaje(it, ble) { aBorrar = null } }
    LaunchedEffect(mensajes.size) {
        malla.marcarLeido(chat)
        if (mensajes.isNotEmpty()) lista.animateScrollToItem(mensajes.size - 1)
    }

    Column(Modifier.imePadding()) {
        if (mensajes.isEmpty()) {
            Box(Modifier.weight(1f)) {
                Vacio(
                    R.drawable.ic_chat,
                    "Escribe el primer mensaje",
                    if (chat is Chat.Canal && chat.indice == 0) {
                        "Este es el canal público: cualquiera con Meshtastic cerca lo puede leer."
                    } else {
                        "Solo lo leen quienes estén en esta conversación."
                    },
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = lista,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(mensajes.size, key = { "${mensajes[it].de}-${mensajes[it].id}" }) { i ->
                    val m = mensajes[i]
                    val previo = mensajes.getOrNull(i - 1)
                    if (previo == null || dia(previo.hora) != dia(m.hora)) SeparadorDia(dia(m.hora))
                    // El nombre solo cuando cambia quien habla, como en WhatsApp.
                    val mostrarNombre = chat is Chat.Canal && m.entrega == Entrega.RECIBIDO && (previo == null || previo.de != m.de)
                    Burbuja(st, m, mostrarNombre, { aBorrar = m }, verEnMapa)
                }
            }
        }
        Entrada(
            enviar = { ble.enviarTexto(it, chat) },
            ubicacion =
                (chat as? Chat.Directo)?.let { d ->
                    if (st.miNodo?.let { st.nodos[it]?.position } != null) ({ ble.enviarPosicionA(d.nodo) }) else null
                },
            esDirecto = chat is Chat.Directo,
        )
    }
}

@Composable
private fun SeparadorDia(texto: String) =
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            texto,
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

@Composable
private fun Burbuja(
    st: MeshState,
    m: Mensaje,
    mostrarNombre: Boolean,
    borrar: () -> Unit,
    verEnMapa: (Int) -> Unit,
) {
    val mio = m.entrega != Entrega.RECIBIDO
    val esUbicacion = m.texto == TEXTO_UBICACION_RECIBIDA || m.texto == TEXTO_UBICACION_ENVIADA
    val fondo = if (mio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val tinta = if (mio) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val forma = if (mio) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

    Column(Modifier.fillMaxWidth().padding(top = if (mostrarNombre) 6.dp else 0.dp), horizontalAlignment = if (mio) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(forma)
                .background(fondo)
                .combinedClickable(onLongClick = borrar) {}
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (mostrarNombre) Text(st.nombre(m.de), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (esUbicacion) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icono(R.drawable.ic_location_on, null, tint = tinta)
                    Text(if (mio) "Enviaste tu ubicación exacta" else "Te compartió su ubicación", color = tinta)
                }
                if (!mio) TextButton(onClick = { verEnMapa(m.de) }) { Text("Ver en el mapa", color = MaterialTheme.colorScheme.primary) }
            } else {
                Text(m.texto, color = tinta, style = MaterialTheme.typography.bodyLarge)
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.hora)), style = MaterialTheme.typography.labelSmall, color = tinta.copy(alpha = 0.7f))
                if (mio) Palomitas(m.entrega, tinta)
            }
        }
        if (m.entrega == Entrega.FALLO) {
            Text("No se confirmó la entrega", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** ✓ enviando · ✓✓ entregado · ⚠ sin confirmación. */
@Composable
private fun Palomitas(
    e: Entrega,
    tinta: Color,
) = when (e) {
    Entrega.ENVIANDO -> Icono(R.drawable.ic_done, "Enviando", Modifier.size(16.dp), tinta.copy(alpha = 0.6f))
    Entrega.ENTREGADO -> Icono(R.drawable.ic_done_all, "Entregado", Modifier.size(16.dp), tinta)
    Entrega.FALLO -> Icono(R.drawable.ic_error, "No entregado", Modifier.size(16.dp), MaterialTheme.colorScheme.error)
    Entrega.RECIBIDO -> Unit
}

@Composable
private fun Entrada(
    enviar: (String) -> Unit,
    ubicacion: (() -> Unit)?,
    esDirecto: Boolean,
) {
    var texto by rememberSaveable { mutableStateOf("") }
    val bytes = texto.encodeToByteArray().size
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (esDirecto) {
            // Ubicación exacta solo por directo; deshabilitado si no se comparte la ubicación (no hay posición).
            IconButton(onClick = { ubicacion?.invoke() }, enabled = ubicacion != null) {
                Icono(
                    R.drawable.ic_my_location,
                    "Enviar mi ubicación exacta",
                    tint = if (ubicacion != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        TextField(
            value = texto,
            // LoRa limita el paquete: se mide en bytes UTF-8, no en caracteres.
            onValueChange = { if (it.encodeToByteArray().size <= MAX_BYTES_TEXTO) texto = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Mensaje") },
            shape = RoundedCornerShape(24.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            supportingText = if (bytes > MAX_BYTES_TEXTO * 3 / 4) ({ Text("$bytes/$MAX_BYTES_TEXTO") }) else null,
            maxLines = 4,
        )
        FilledIconButton(
            onClick = {
                enviar(texto.trim())
                texto = ""
            },
            enabled = texto.isNotBlank(),
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Icono(R.drawable.ic_send, "Enviar", tint = MaterialTheme.colorScheme.onPrimary) }
    }
}
