package io.github.ricardomonroy.canalcero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.mesh.Estado
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_CANAL
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_CORTO
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_LARGO
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.Paso
import io.github.ricardomonroy.canalcero.mesh.canalPrivado
import io.github.ricardomonroy.canalcero.mesh.idNodo
import io.github.ricardomonroy.canalcero.mesh.paraMexico
import io.github.ricardomonroy.canalcero.mesh.pasosPendientes
import io.github.ricardomonroy.canalcero.mesh.urlCanal
import kotlinx.coroutines.launch
import org.meshtastic.proto.Config
import org.meshtastic.proto.ChannelSettings

/**
 * Asistente para un nodo recién sacado de la caja. Los pasos salen del estado del nodo (no de un contador),
 * así sobrevive al reinicio que provoca configurar la región: al reconectar, el paso hecho ya no aparece.
 */
@Composable
fun Asistente(
    st: MeshState,
    malla: Malla,
) {
    val ble = malla.ble
    val alcance = rememberCoroutineScope()
    var omitidos by rememberSaveable { mutableStateOf(listOf<String>()) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<String?>(null) }
    var porUnirse by remember { mutableStateOf<ChannelSettings?>(null) }
    val (bateriaLibre, pedirBateria) = recordarBateria()
    val terminar = { malla.terminarAsistente(st.miNodo?.let(::idNodo)) }

    fun ejecutar(accion: suspend () -> Unit) {
        ocupado = true
        error = null
        alcance.launch {
            runCatching { accion() }.onFailure { error = "No se pudo: ${it.message}" }
            ocupado = false
        }
    }

    val pasos = (st.pasosPendientes() + if (bateriaLibre) emptyList() else listOf(Paso.BATERIA)).filter { it.name !in omitidos }
    val omitir = { p: Paso -> omitidos = omitidos + p.name }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Configura tu nodo", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = terminar) { Text("Salir") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val paso = pasos.firstOrNull()
        when {
            st.estado != Estado.LISTO -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Esperando a tu nodo… Si acabas de configurar la región, se está reiniciando y la app se reconecta sola.")
                }
            }
            paso == null -> {
                Text("✅ Listo", style = MaterialTheme.typography.titleLarge)
                Text("Tu nodo ya puede transmitir. Escribe en Chats, mira a tu grupo en Mapa y, si hace falta, usa el botón SOS. Todo esto se puede cambiar después en Ajustes.")
                Button(onClick = terminar, Modifier.fillMaxWidth()) { Text("Empezar") }
            }
            else -> {
                Text("Faltan ${pasos.size} paso" + if (pasos.size > 1) "s" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (paso) {
                    Paso.REGION -> {
                        Titulo("Región de radio")
                        Text("Tu nodo viene sin región y así no puede transmitir. En México se usa 915 MHz (región US), la misma de la red pública. El nodo se reinicia al guardarla.")
                        Button(onClick = { ejecutar { ble.configurar(Config(lora = st.lora.paraMexico())) } }, Modifier.fillMaxWidth(), enabled = !ocupado) {
                            Text("Configurar para México (915 MHz)")
                        }
                    }
                    Paso.NOMBRE -> {
                        Titulo("Tu nombre")
                        Text("Así te verán los demás. El nombre corto, de hasta 4 letras, aparece en el mapa.")
                        var largo by rememberSaveable { mutableStateOf("") }
                        var corto by rememberSaveable { mutableStateOf("") }
                        CampoBytes("Nombre (ej. Ricardo Moto)", largo, MAX_BYTES_NOMBRE_LARGO) { largo = it }
                        CampoBytes("Nombre corto (ej. RM)", corto, MAX_BYTES_NOMBRE_CORTO) { corto = it }
                        Button(
                            onClick = { ejecutar { ble.renombrar(largo.trim(), corto.trim()) } },
                            Modifier.fillMaxWidth(),
                            enabled = !ocupado && largo.isNotBlank() && corto.isNotBlank(),
                        ) { Text("Guardar nombre") }
                        Omitir { omitir(paso) }
                    }
                    Paso.PRIVACIDAD -> {
                        Titulo("Tu ubicación en el canal público")
                        Text("Tal como viene, tu nodo envía tu zona aproximada (±km) a cualquiera con Meshtastic cerca. Te recomiendo compartirla solo en tus canales privados.")
                        Button(onClick = { ejecutar { ble.ocultarUbicacionPublica() } }, Modifier.fillMaxWidth(), enabled = !ocupado) { Text("Ocultarla del canal público") }
                        Omitir("Dejarla así") { omitir(paso) }
                    }
                    Paso.CANAL -> {
                        Titulo("Canal privado para tu grupo")
                        Text("Un canal cifrado para tu familia o amigos. Una persona lo crea y los demás escanean su QR.")
                        var nombre by rememberSaveable { mutableStateOf("Familia") }
                        CampoBytes("Nombre del canal", nombre, MAX_BYTES_NOMBRE_CANAL) { nombre = it }
                        Button(
                            onClick = {
                                val s = canalPrivado(nombre.trim())
                                ejecutar {
                                    ble.agregarCanal(s)
                                    qr = urlCanal(s, st.lora)
                                }
                            },
                            Modifier.fillMaxWidth(),
                            enabled = !ocupado && nombre.isNotBlank(),
                        ) { Text("Crear canal y mostrar QR") }
                        Text("¿Alguien ya lo creó?", style = MaterialTheme.typography.titleSmall)
                        Unirse(alInterpretar = { porUnirse = it }, alError = { error = it })
                        Omitir { omitir(paso) }
                    }
                    Paso.BATERIA -> {
                        Titulo("Que funcione con la pantalla apagada")
                        Text("Android puede dormir CanalCero para ahorrar batería y perderías mensajes y SOS. Quita la restricción solo para esta app.")
                        Button(onClick = pedirBateria, Modifier.fillMaxWidth()) { Text("Quitar ahorro de batería") }
                        Omitir { omitir(paso) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    qr?.let { url -> DialogoQr(url) { qr = null } }
    porUnirse?.let { s ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { porUnirse = null },
            title = { Text("¿Unirte a \"${s.name}\"?") },
            text = { Text("Tu nodo podrá leer y enviar mensajes en este canal.") },
            confirmButton = {
                Button(onClick = {
                    porUnirse = null
                    ejecutar { ble.agregarCanal(s) }
                }) { Text("Unirme") }
            },
            dismissButton = { TextButton(onClick = { porUnirse = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Titulo(texto: String) = Text(texto, style = MaterialTheme.typography.titleLarge)

@Composable
private fun Omitir(
    texto: String = "Omitir por ahora",
    omitir: () -> Unit,
) = OutlinedButton(onClick = omitir, Modifier.fillMaxWidth()) { Text(texto) }
