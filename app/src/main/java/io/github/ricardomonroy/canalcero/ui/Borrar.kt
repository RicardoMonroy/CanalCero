package io.github.ricardomonroy.canalcero.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.MeshBle
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.Mensaje
import kotlinx.coroutines.launch

private const val SOLO_AQUI = "Se borra solo en este teléfono. Quien ya lo recibió lo conserva: Meshtastic no permite borrar a distancia."

/** Opciones de una conversación: borrar su historial y, si es un canal privado, salir de él. */
@Composable
fun OpcionesChat(
    st: MeshState,
    chat: Chat,
    ble: MeshBle,
    cerrar: () -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var confirmar by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val nombre = st.nombre(chat)
    val privado = chat is Chat.Canal && chat.indice > 0

    when (confirmar) {
        null ->
            AlertDialog(
                onDismissRequest = cerrar,
                title = { Text((if (chat is Chat.Canal) "# " else "") + nombre) },
                text = {
                    Column {
                        TextButton(onClick = { confirmar = "chat" }) { Text("Borrar conversación") }
                        if (privado) TextButton(onClick = { confirmar = "canal" }) { Text("Salir del canal", color = MaterialTheme.colorScheme.error) }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
            )

        "chat" ->
            Confirmar("¿Borrar la conversación con $nombre?", SOLO_AQUI, "Borrar", cerrar) {
                ble.borrarChat(chat)
                cerrar()
            }

        "canal" ->
            Confirmar(
                "¿Salir de \"$nombre\"?",
                "Tu nodo deja de recibir este canal y se borra su conversación aquí. Los demás miembros lo siguen usando con la misma llave: " +
                    "para cerrarlo de verdad, crea uno nuevo y compártelo solo con quien quieras.",
                "Salir",
                cerrar,
            ) {
                alcance.launch {
                    runCatching { ble.salirDeCanal((chat as Chat.Canal).indice) }
                        .onSuccess { cerrar() }
                        .onFailure {
                            error = "No se pudo: ${it.message}"
                            confirmar = null
                        }
                }
            }
    }
}

@Composable
fun BorrarMensaje(
    m: Mensaje,
    ble: MeshBle,
    cerrar: () -> Unit,
) = Confirmar("¿Borrar este mensaje?", SOLO_AQUI, "Borrar", cerrar) {
    ble.borrarMensaje(m)
    cerrar()
}

@Composable
private fun Confirmar(
    titulo: String,
    detalle: String,
    accion: String,
    cancelar: () -> Unit,
    aceptar: () -> Unit,
) = AlertDialog(
    onDismissRequest = cancelar,
    title = { Text(titulo) },
    text = { Text(detalle) },
    confirmButton = { TextButton(onClick = aceptar) { Text(accion, color = MaterialTheme.colorScheme.error) } },
    dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar") } },
)
