package io.github.ricardomonroy.canalcero.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_CANAL
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_CORTO
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_LARGO
import io.github.ricardomonroy.canalcero.mesh.MeshBle
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.canalPrivado
import io.github.ricardomonroy.canalcero.mesh.leerUrlCanal
import io.github.ricardomonroy.canalcero.mesh.nombreCanal
import io.github.ricardomonroy.canalcero.mesh.urlCanal
import kotlinx.coroutines.launch
import org.meshtastic.proto.ChannelSettings

@Composable
fun Ajustes(
    st: MeshState,
    ble: MeshBle,
) {
    val alcance = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<String?>(null) }
    var porUnirse by remember { mutableStateOf<ChannelSettings?>(null) }

    // Las operaciones de admin pueden reiniciar el nodo: el error se muestra, no se oculta.
    fun ejecutar(
        listo: String,
        accion: suspend () -> Unit,
    ) {
        ocupado = true
        aviso = null
        alcance.launch {
            aviso = runCatching { accion() }.fold({ listo }, { "No se pudo: ${it.message}" })
            ocupado = false
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        aviso?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        // --- Nombre ---
        val yo = st.miNodo?.let { st.nodos[it]?.user }
        var largo by rememberSaveable(yo?.long_name) { mutableStateOf(yo?.long_name.orEmpty()) }
        var corto by rememberSaveable(yo?.short_name) { mutableStateOf(yo?.short_name.orEmpty()) }
        Text("Nombre de tu nodo", style = MaterialTheme.typography.titleMedium)
        CampoBytes("Nombre", largo, MAX_BYTES_NOMBRE_LARGO) { largo = it }
        CampoBytes("Nombre corto (en el mapa)", corto, MAX_BYTES_NOMBRE_CORTO) { corto = it }
        Button(
            onClick = { ejecutar("Nombre guardado") { ble.renombrar(largo.trim(), corto.trim()) } },
            enabled = !ocupado && largo.isNotBlank() && corto.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar nombre") }

        HorizontalDivider()

        // --- Canales ---
        Text("Canales", style = MaterialTheme.typography.titleMedium)
        st.canales.values.sortedBy { it.index }.forEach { c ->
            val s = c.settings
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("# " + nombreCanal(c))
                    val prec = s?.module_settings?.position_precision ?: 0
                    Text(
                        (if (c.index == 0) "Público" else "Privado") + " · ubicación " +
                            when (prec) {
                                0 -> "no se comparte"
                                32 -> "exacta"
                                else -> "aproximada"
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (c.index > 0 && s != null) TextButton(onClick = { qr = urlCanal(s, st.lora) }) { Text("Compartir") }
            }
        }

        var nuevo by rememberSaveable { mutableStateOf("") }
        CampoBytes("Nombre del canal privado", nuevo, MAX_BYTES_NOMBRE_CANAL) { nuevo = it }
        Button(
            onClick = {
                val s = canalPrivado(nuevo.trim())
                ejecutar("Canal \"${s.name}\" creado. Compártelo con el QR.") {
                    ble.agregarCanal(s)
                    qr = urlCanal(s, st.lora)
                }
                nuevo = ""
            },
            enabled = !ocupado && nuevo.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Crear canal privado") }

        Unirse(alInterpretar = { porUnirse = it }, alError = { aviso = it })
        Text(
            "Al crear o unirte a un canal privado, tu ubicación deja de enviarse por el canal público y va exacta solo por el privado.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    qr?.let { url -> DialogoQr(url) { qr = null } }
    porUnirse?.let { s ->
        AlertDialog(
            onDismissRequest = { porUnirse = null },
            title = { Text("¿Unirte a \"${s.name}\"?") },
            text = { Text("Tu nodo podrá leer y enviar mensajes en este canal.") },
            confirmButton = {
                Button(onClick = {
                    porUnirse = null
                    ejecutar("Te uniste a \"${s.name}\"") { ble.agregarCanal(s) }
                }) { Text("Unirme") }
            },
            dismissButton = { TextButton(onClick = { porUnirse = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Unirse(
    alInterpretar: (ChannelSettings) -> Unit,
    alError: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var enlace by rememberSaveable { mutableStateOf("") }
    fun usar(texto: String) = leerUrlCanal(texto)?.let(alInterpretar) ?: alError("Ese código no es un canal de Meshtastic")

    OutlinedButton(
        onClick = {
            // Escáner de Google Play Services: no necesita permiso de cámara.
            val opciones = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning
                .getClient(ctx, opciones)
                .startScan()
                .addOnSuccessListener { b -> b.rawValue?.let(::usar) }
                .addOnFailureListener { alError("No se pudo abrir el escáner: ${it.message}") }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Unirme escaneando un QR") }
    Row {
        OutlinedTextField(enlace, { enlace = it }, Modifier.weight(1f), placeholder = { Text("O pega el enlace") }, singleLine = true)
        TextButton(onClick = {
            usar(enlace)
            enlace = ""
        }, enabled = enlace.isNotBlank()) { Text("Unirme") }
    }
}

@Composable
private fun DialogoQr(
    url: String,
    cerrar: () -> Unit,
) {
    val ctx = LocalContext.current
    val imagen = remember(url) { qrBitmap(url).asImageBitmap() }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Compartir canal") },
        text = {
            Column {
                Image(imagen, "Código QR del canal", Modifier.size(260.dp))
                Text(
                    "Quien escanee este código puede leer todo el canal. Compártelo solo en persona o por un chat de confianza.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = cerrar) { Text("Cerrar") } },
        dismissButton = {
            TextButton(onClick = {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                ctx.startActivity(Intent.createChooser(i, "Compartir enlace"))
            }) { Text("Enviar enlace") }
        },
    )
}

@Composable
private fun CampoBytes(
    etiqueta: String,
    valor: String,
    max: Int,
    cambiar: (String) -> Unit,
) = OutlinedTextField(
    value = valor,
    // El firmware limita en bytes UTF-8 (la ñ y los acentos ocupan 2).
    onValueChange = { if (it.encodeToByteArray().size <= max) cambiar(it) },
    label = { Text(etiqueta) },
    supportingText = { Text("${valor.encodeToByteArray().size}/$max") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
)

private fun qrBitmap(texto: String): Bitmap {
    val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 512, 512)
    val px = IntArray(m.width * m.height) { if (m[it % m.width, it / m.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}
