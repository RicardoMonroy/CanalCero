package io.github.ricardomonroy.canalcero.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.R
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_CANAL
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_CORTO
import io.github.ricardomonroy.canalcero.mesh.MAX_BYTES_NOMBRE_LARGO
import io.github.ricardomonroy.canalcero.mesh.MeshBle
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.PerfilPosicion
import io.github.ricardomonroy.canalcero.mesh.ROLES
import io.github.ricardomonroy.canalcero.mesh.Rol
import io.github.ricardomonroy.canalcero.mesh.paraMexico
import io.github.ricardomonroy.canalcero.mesh.sinRegion
import io.github.ricardomonroy.canalcero.mesh.canalPrivado
import io.github.ricardomonroy.canalcero.mesh.leerUrlCanal
import io.github.ricardomonroy.canalcero.mesh.nombreCanal
import io.github.ricardomonroy.canalcero.mesh.urlCanal
import kotlinx.coroutines.launch
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

enum class SubAjuste(
    val titulo: String,
) {
    INICIO("Ajustes"),
    MI_NODO("Mi nodo"),
    UBICACION("Ubicación"),
    CANALES("Canales"),
    MAPAS("Mapas sin internet"),
    APARIENCIA("Apariencia"),
}

@Composable
fun Ajustes(
    st: MeshState,
    ble: MeshBle,
    malla: Malla,
    sub: SubAjuste,
    ir: (SubAjuste?) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    // Las operaciones de admin pueden reiniciar el nodo: el error se muestra, no se oculta.
    val ejecutar = { listo: String, accion: suspend () -> Unit ->
        ocupado = true
        aviso = null
        alcance.launch {
            aviso = runCatching { accion() }.fold({ listo }, { "No se pudo: ${it.message}" })
            ocupado = false
        }
        Unit
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        aviso?.let {
            Text(
                it,
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        when (sub) {
            SubAjuste.INICIO -> Inicio(st, malla, ir)
            SubAjuste.MI_NODO -> MiNodo(st, ble, ocupado, ejecutar)
            SubAjuste.UBICACION -> Ubicacion(st, ble, malla, ocupado, ejecutar)
            SubAjuste.CANALES -> Canales(st, ble, ocupado, ejecutar) { aviso = it }
            SubAjuste.MAPAS -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { MapasSinInternet() }
            SubAjuste.APARIENCIA -> Apariencia(malla)
        }
    }
}

@Composable
private fun Inicio(
    st: MeshState,
    malla: Malla,
    ir: (SubAjuste?) -> Unit,
) {
    val yo = st.miNodo?.let { st.nodos[it]?.user }
    val privados = st.canales.keys.count { it > 0 }
    val perfil = st.cfgPosicion?.let { PerfilPosicion.de(it)?.nombre } ?: "personalizado"
    // Un nodo nuevo viene sin región: se avisa arriba de todo, es lo primero que hay que arreglar.
    if (st.lora.sinRegion()) {
        Fila(R.drawable.ic_error, "Configura la región de tu nodo", "Sin región no puede transmitir", alerta = true) { ir(SubAjuste.MI_NODO) }
    }
    Seccion("Mi nodo")
    Fila(R.drawable.ic_person, "Nombre, rol y región", listOfNotNull(yo?.long_name, ROLES.firstOrNull { it.rol == st.cfgDispositivo?.role }?.nombre).joinToString(" · ")) { ir(SubAjuste.MI_NODO) }
    Seccion("Privacidad")
    Fila(R.drawable.ic_location_on, "Ubicación", (if (malla.compartirUbicacion) "Compartiendo" else "No se comparte") + " · perfil $perfil") { ir(SubAjuste.UBICACION) }
    Fila(R.drawable.ic_lock, "Canales", if (privados == 0) "Solo el público" else "$privados privado" + if (privados > 1) "s" else "") { ir(SubAjuste.CANALES) }
    Seccion("App")
    Fila(R.drawable.ic_download, "Mapas sin internet", "Zonas descargadas para usar sin red") { ir(SubAjuste.MAPAS) }
    Fila(R.drawable.ic_palette, "Apariencia", malla.tema.nombre) { ir(SubAjuste.APARIENCIA) }
    Spacer(Modifier.height(16.dp))
    Fila(R.drawable.ic_link_off, "Desconectar nodo", "Deja de usar este nodo en el teléfono", alerta = true) {
        ir(null)
        malla.desconectar()
    }
}

@Composable
private fun Fila(
    @DrawableRes icono: Int,
    titulo: String,
    detalle: String,
    alerta: Boolean = false,
    tocar: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().clickable(onClick = tocar).padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    val color = if (alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Icono(icono, null, tint = color)
    Column(Modifier.weight(1f)) {
        Text(titulo, style = MaterialTheme.typography.bodyLarge, color = if (alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if (detalle.isNotBlank()) Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!alerta) Icono(R.drawable.ic_chevron_right, null)
}

@Composable
private fun MiNodo(
    st: MeshState,
    ble: MeshBle,
    ocupado: Boolean,
    ejecutar: (String, suspend () -> Unit) -> Unit,
) {
    var rolPendiente by remember { mutableStateOf<Rol?>(null) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (st.lora.sinRegion()) {
            Text("Tu nodo no tiene región: no puede transmitir.", color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { ejecutar("Región configurada. El nodo se reinicia y se reconecta solo.") { ble.configurar(Config(lora = st.lora.paraMexico())) } },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Configurar para México (915 MHz)") }
        }
        val yo = st.miNodo?.let { st.nodos[it]?.user }
        var largo by rememberSaveable(yo?.long_name) { mutableStateOf(yo?.long_name.orEmpty()) }
        var corto by rememberSaveable(yo?.short_name) { mutableStateOf(yo?.short_name.orEmpty()) }
        Text("Nombre", style = MaterialTheme.typography.titleMedium)
        CampoBytes("Nombre", largo, MAX_BYTES_NOMBRE_LARGO) { largo = it }
        CampoBytes("Nombre corto (en el mapa)", corto, MAX_BYTES_NOMBRE_CORTO) { corto = it }
        Button(
            onClick = { ejecutar("Nombre guardado") { ble.renombrar(largo.trim(), corto.trim()) } },
            enabled = !ocupado && largo.isNotBlank() && corto.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar nombre") }
    }
    st.cfgDispositivo?.let { cfg ->
        Seccion("Rol")
        Column(Modifier.padding(horizontal = 16.dp)) {
            ROLES.forEach { r -> Opcion(r.nombre, r.detalle, elegida = r.rol == cfg.role, habilitada = !ocupado) { rolPendiente = r } }
            if (ROLES.none { it.rol == cfg.role }) Text("Rol actual: ${cfg.role}", style = MaterialTheme.typography.bodySmall)
        }
    }
    rolPendiente?.let { r ->
        AlertDialog(
            onDismissRequest = { rolPendiente = null },
            title = { Text("¿Cambiar a \"${r.nombre}\"?") },
            text = { Text(listOfNotNull(r.detalle, r.aviso, "El nodo se reinicia y la app se reconecta sola.").joinToString("\n\n")) },
            confirmButton = {
                Button(onClick = {
                    rolPendiente = null
                    val cfg = st.cfgDispositivo ?: return@Button
                    ejecutar("Rol cambiado a ${r.nombre}. El nodo se reinicia.") { ble.configurar(Config(device = cfg.copy(role = r.rol))) }
                }) { Text("Cambiar") }
            },
            dismissButton = { TextButton(onClick = { rolPendiente = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Ubicacion(
    st: MeshState,
    ble: MeshBle,
    malla: Malla,
    ocupado: Boolean,
    ejecutar: (String, suspend () -> Unit) -> Unit,
) {
    val alternar = alternadorUbicacion(malla)
    Row(
        Modifier.fillMaxWidth().clickable { alternar(!malla.compartirUbicacion) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Compartir mi ubicación", style = MaterialTheme.typography.bodyLarge)
            Text("El teléfono le da tu ubicación al nodo; el nodo la envía con la precisión de cada canal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = malla.compartirUbicacion, onCheckedChange = alternar)
    }
    st.cfgPosicion?.let { cfg ->
        val actual = PerfilPosicion.de(cfg)
        Seccion("Cada cuánto se envía")
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (actual == null) Text("Configuración personalizada (hecha en otra app).", style = MaterialTheme.typography.bodySmall)
            PerfilPosicion.entries.forEach { p ->
                Opcion(p.nombre, p.detalle, elegida = p == actual, habilitada = !ocupado) {
                    ejecutar("Posición: perfil ${p.nombre}") { ble.configurar(Config(position = p.aplicar(cfg))) }
                }
            }
        }
    }
    Seccion("Quién la ve")
    Text(
        "Canal público: " + (if ((st.canales[0]?.settings?.module_settings?.position_precision ?: 0) == 0) "nadie." else "aproximada (±km) para cualquiera cerca.") +
            "\nCanales privados: exacta, solo sus miembros.\nMensaje directo: exacta, con el botón de ubicación del chat.",
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun Canales(
    st: MeshState,
    ble: MeshBle,
    ocupado: Boolean,
    ejecutar: (String, suspend () -> Unit) -> Unit,
    avisar: (String) -> Unit,
) {
    var qr by remember { mutableStateOf<String?>(null) }
    var porUnirse by remember { mutableStateOf<ChannelSettings?>(null) }
    var saliendo by remember { mutableStateOf<Chat?>(null) }
    saliendo?.let { OpcionesChat(st, it, ble) { saliendo = null } }

    st.canales.values.sortedBy { it.index }.forEach { c ->
        val s = c.settings
        val prec = s?.module_settings?.position_precision ?: 0
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AvatarChat(st, Chat.Canal(c.index), 40.dp)
            Column(Modifier.weight(1f)) {
                Text(nombreCanal(c), style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (c.index == 0) "Público" else "Privado") + " · ubicación " +
                        when (prec) {
                            0 -> "no se comparte"
                            32 -> "exacta"
                            else -> "aproximada"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (c.index > 0 && s != null) {
                IconButton(onClick = { qr = urlCanal(s, st.lora) }) { Icono(R.drawable.ic_share, "Compartir") }
                IconButton(onClick = { saliendo = Chat.Canal(c.index) }) { Icono(R.drawable.ic_logout, "Salir", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Seccion("Crear canal privado")
        var nuevo by rememberSaveable { mutableStateOf("") }
        CampoBytes("Nombre (ej. Familia)", nuevo, MAX_BYTES_NOMBRE_CANAL) { nuevo = it }
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
        Seccion("Unirme a un canal")
        Unirse(alInterpretar = { porUnirse = it }, alError = avisar)
        Text(
            "Al crear o unirte a un canal privado, tu ubicación deja de enviarse por el canal público y va exacta solo por el privado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun Apariencia(malla: Malla) {
    Seccion("Tema de la app")
    Column(Modifier.padding(horizontal = 16.dp)) {
        TemaApp.entries.forEach { t -> Opcion(t.nombre, "", elegida = malla.tema == t, habilitada = true) { malla.cambiarTema(t) } }
    }
    Seccion("Mapa")
    Row(Modifier.fillMaxWidth().clickable { malla.alternarEstilo() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Mapa claro", style = MaterialTheme.typography.bodyLarge)
            Text("Independiente del tema de la app. También se cambia con el botón del mapa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = malla.estiloClaro, onCheckedChange = { malla.alternarEstilo() })
    }
}

/** Activa o desactiva la ubicación pidiendo el permiso si hace falta. */
@Composable
fun alternadorUbicacion(malla: Malla): (Boolean) -> Unit {
    val ctx = LocalContext.current
    val pedir =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
            if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) malla.alternarUbicacion(true)
        }
    return { on ->
        if (!on || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            malla.alternarUbicacion(on)
        } else {
            pedir.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
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
private fun Opcion(
    nombre: String,
    detalle: String,
    elegida: Boolean,
    habilitada: Boolean,
    elegir: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().selectable(selected = elegida, enabled = habilitada, role = Role.RadioButton, onClick = elegir).padding(vertical = 4.dp),
) {
    RadioButton(selected = elegida, onClick = null, enabled = habilitada)
    Column(Modifier.padding(start = 8.dp)) {
        Text(nombre)
        if (detalle.isNotBlank()) Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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

@Composable
private fun MapasSinInternet() {
    val ctx = LocalContext.current
    var zonas by remember { mutableStateOf<List<Zona>?>(null) }
    val progreso = ZonasOffline.progreso
    // Se vuelve a listar al terminar una descarga (progreso pasa a null).
    LaunchedEffect(progreso == null) { ZonasOffline.listar(ctx) { zonas = it } }

    Text("Para descargar una zona, muévete a ella en Mapa y toca el botón de descarga. En el mapa verás su contorno punteado.", style = MaterialTheme.typography.bodyMedium)
    progreso?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    zonas?.forEach { z ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(z.nombre)
                Text(ZonasOffline.mb(z.bytes) + if (z.completa) "" else " · incompleta", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { ZonasOffline.borrar(z) { ZonasOffline.listar(ctx) { zonas = it } } }) {
                Text("Borrar", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (zonas?.isEmpty() == true) Text("Aún no tienes zonas descargadas.", style = MaterialTheme.typography.bodySmall)
}
