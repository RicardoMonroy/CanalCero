package io.github.ricardomonroy.canalcero.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.R
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.clave
import io.github.ricardomonroy.canalcero.mesh.Entrega
import io.github.ricardomonroy.canalcero.mesh.PaseDeLista
import io.github.ricardomonroy.canalcero.mesh.TipoEmergencia
import io.github.ricardomonroy.canalcero.mesh.paseDeLista
import io.github.ricardomonroy.canalcero.mesh.tipoEmergencia
import io.github.ricardomonroy.canalcero.mesh.urgente
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
                    if (tipoEmergencia(m.texto) == TipoEmergencia.PASE_DE_LISTA) {
                        TarjetaPaseDeLista(st, st.paseDeLista(m)) { malla.emergencia(chat, it) }
                    } else {
                        Burbuja(st, m, mostrarNombre, { aBorrar = m }, verEnMapa)
                    }
                }
            }
        }
        // Ubicación exacta: solo por directo o canal privado, nunca en el público.
        val admiteUbicacion = chat is Chat.Directo || (chat is Chat.Canal && chat.indice > 0)
        // Las de "enviar ahora" (30 s) no se anuncian como en vivo.
        val hasta = malla.enVivo[chat.clave()]?.takeIf { it - System.currentTimeMillis() > 60_000 }
        if (hasta != null) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icono(R.drawable.ic_location_on, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "  Ubicación en vivo hasta ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(hasta))}",
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TextButton(onClick = { malla.compartirEnVivo(chat, null) }) { Text("Detener") }
            }
        }
        if (chat !is Chat.Canal || chat.indice > 0) AccionesEmergencia(chat) { malla.emergencia(chat, it) }
        Entrada(
            enviar = { ble.enviarTexto(it, chat) },
            ubicacion = if (admiteUbicacion) MenuUbicacion(malla, chat, enVivo = hasta != null) else null,
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
    val tipo = tipoEmergencia(m.texto)
    // Verde y rojo reservados para emergencia: se reconocen de un vistazo.
    val fondo =
        when {
            tipo == TipoEmergencia.BIEN -> VerdeOk
            tipo?.urgente() == true -> RojoSos
            mio -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val tinta =
        when {
            tipo == TipoEmergencia.BIEN -> Grafito
            tipo?.urgente() == true -> Color.White
            mio -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        }
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
            if (mostrarNombre) Text(st.nombre(m.de), style = MaterialTheme.typography.labelLarge, color = if (tipo != null) tinta else MaterialTheme.colorScheme.primary)
            if (esUbicacion) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icono(R.drawable.ic_location_on, null, tint = tinta)
                    Text(if (mio) "Enviaste tu ubicación exacta" else "Te compartió su ubicación", color = tinta)
                }
                if (!mio) TextButton(onClick = { verEnMapa(m.de) }) { Text("Ver en el mapa", color = MaterialTheme.colorScheme.primary) }
            } else {
                Text(m.texto, color = tinta, style = if (tipo != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                if (!mio && tipo?.urgente() == true && st.nodos[m.de]?.position != null) {
                    TextButton(onClick = { verEnMapa(m.de) }) { Text("Ver en el mapa", color = tinta) }
                }
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

/** Botón de ubicación con sus opciones; devuelve el composable que se dibuja junto al campo de texto. */
private fun MenuUbicacion(
    malla: Malla,
    chat: Chat,
    enVivo: Boolean,
): @Composable () -> Unit =
    {
        var abierto by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        val tienePermiso = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.any { it }) abierto = true }
        Box {
            IconButton(onClick = { if (tienePermiso) abierto = true else pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                Icono(R.drawable.ic_my_location, "Compartir mi ubicación", tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                Text("Ubicación exacta, solo aquí", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                // "Una vez" = una sesión en vivo de 1 min: reutiliza el mismo camino (GPS si hace falta) y se apaga sola.
                DropdownMenuItem(text = { Text("Enviar ahora") }, onClick = {
                    abierto = false
                    malla.compartirEnVivo(chat, 0)
                })
                listOf(15 to "En vivo 15 min", 60 to "En vivo 1 hora", 240 to "En vivo 4 horas").forEach { (min, etiqueta) ->
                    DropdownMenuItem(text = { Text(etiqueta) }, onClick = {
                        abierto = false
                        malla.compartirEnVivo(chat, min)
                    })
                }
                if (enVivo) {
                    DropdownMenuItem(text = { Text("Detener en vivo", color = MaterialTheme.colorScheme.error) }, onClick = {
                        abierto = false
                        malla.compartirEnVivo(chat, null)
                    })
                }
            }
        }
    }

@Composable
private fun Entrada(
    enviar: (String) -> Unit,
    ubicacion: (@Composable () -> Unit)?,
) {
    var texto by rememberSaveable { mutableStateOf("") }
    val bytes = texto.encodeToByteArray().size
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ubicacion?.invoke()
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

/** Botones rápidos arriba del campo de texto. SOS pide confirmación: no debe salir por accidente. */
@Composable
private fun AccionesEmergencia(
    chat: Chat,
    enviar: (TipoEmergencia) -> Unit,
) {
    var confirmarSos by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { enviar(TipoEmergencia.BIEN) }, label = { Text("✅ Estoy bien") })
        if (chat is Chat.Canal) AssistChip(onClick = { enviar(TipoEmergencia.PASE_DE_LISTA) }, label = { Text("📋 Pase de lista") })
        AssistChip(onClick = { enviar(TipoEmergencia.AYUDA) }, label = { Text("⚠️ Necesito ayuda") })
        AssistChip(
            onClick = { confirmarSos = true },
            label = { Text("🆘 SOS") },
            colors = AssistChipDefaults.assistChipColors(containerColor = RojoSos, labelColor = Color.White),
            border = null,
        )
    }
    if (confirmarSos) {
        DialogoSos({ confirmarSos = false }) {
            confirmarSos = false
            enviar(TipoEmergencia.SOS)
        }
    }
}

@Composable
fun DialogoSos(
    cancelar: () -> Unit,
    confirmar: () -> Unit,
) = AlertDialog(
    onDismissRequest = cancelar,
    icon = { Text("🆘", style = MaterialTheme.typography.headlineMedium) },
    title = { Text("¿Enviar SOS?") },
    text = { Text("Se avisa con alarma a todos en esta conversación y tu ubicación exacta se comparte en vivo durante 1 hora.") },
    confirmButton = {
        TextButton(onClick = confirmar) { Text("Enviar SOS", color = RojoSos) }
    },
    dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar") } },
)

/** El pase de lista se dibuja como tarjeta: quién respondió, cómo está y quién falta. */
@Composable
private fun TarjetaPaseDeLista(
    st: MeshState,
    p: PaseDeLista,
    responder: (TipoEmergencia) -> Unit,
) {
    val mio = p.inicio.entrega != Entrega.RECIBIDO
    val yaRespondi = st.miNodo in p.respuestas.keys || st.mensajes.any { it.entrega != Entrega.RECIBIDO && it.hora > p.inicio.hora && it.chat(st.miNodo) == p.inicio.chat(st.miNodo) && tipoEmergencia(it.texto).let { t -> t != null && t != TipoEmergencia.PASE_DE_LISTA } }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("📋 Pase de lista", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                (if (mio) "Lo pediste tú" else "Pedido por ${st.nombre(p.inicio.de)}") + " · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(p.inicio.hora)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            p.respuestas.values.sortedBy { it.hora }.forEach { r ->
                val t = tipoEmergencia(r.texto)
                Text(
                    "${t?.prefijo ?: ""} ${st.nombre(r.de)} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.hora))}",
                    color = if (t?.urgente() == true) RojoSos else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (p.faltan.isNotEmpty()) {
                Text("Sin respuesta: " + p.faltan.joinToString(", ") { st.nombre(it) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (p.respuestas.isEmpty() && p.faltan.isEmpty()) {
                Text("Esperando respuestas…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (!mio && !yaRespondi) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { responder(TipoEmergencia.BIEN) }, colors = ButtonDefaults.buttonColors(containerColor = VerdeOk, contentColor = Grafito)) { Text("Estoy bien") }
                    OutlinedButton(onClick = { responder(TipoEmergencia.AYUDA) }) { Text("Necesito ayuda") }
                }
            }
        }
    }
}
