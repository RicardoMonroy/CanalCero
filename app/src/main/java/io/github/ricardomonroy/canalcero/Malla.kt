package io.github.ricardomonroy.canalcero

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.util.AtomicFile
import io.github.ricardomonroy.canalcero.mesh.Alerta
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.Muestra
import io.github.ricardomonroy.canalcero.mesh.TipoEmergencia
import io.github.ricardomonroy.canalcero.mesh.tipoEmergencia
import io.github.ricardomonroy.canalcero.mesh.urgente
import io.github.ricardomonroy.canalcero.mesh.chatDeClave
import io.github.ricardomonroy.canalcero.mesh.muestra
import io.github.ricardomonroy.canalcero.mesh.tocaEnviar
import io.github.ricardomonroy.canalcero.mesh.vigentes
import io.github.ricardomonroy.canalcero.mesh.clave
import io.github.ricardomonroy.canalcero.ui.TemaApp
import io.github.ricardomonroy.canalcero.mesh.Entrega
import io.github.ricardomonroy.canalcero.mesh.SIN_SENAL_SEG
import io.github.ricardomonroy.canalcero.mesh.alertas
import io.github.ricardomonroy.canalcero.mesh.companeros
import io.github.ricardomonroy.canalcero.mesh.Estado
import io.github.ricardomonroy.canalcero.mesh.MeshBle
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.aEstado
import io.github.ricardomonroy.canalcero.mesh.aHistorial
import io.github.ricardomonroy.canalcero.proto.Historial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Todo lo que debe sobrevivir a la pantalla: conexión con el nodo, ubicación compartida, historial y avisos.
 * Vive en la Application; MeshService mantiene vivo el proceso mientras hay un nodo elegido.
 */
@SuppressLint("MissingPermission") // Cada uso revisa o recibe permisos ya concedidos por la UI.
class Malla(
    private val app: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = app.getSharedPreferences("canalcero", Context.MODE_PRIVATE)
    private val archivo = AtomicFile(File(app.filesDir, "historial.pb"))

    val ble = MeshBle(app, scope, cargarHistorial())

    /** La Activity lo marca en onStart/onStop: con la app a la vista no se notifican mensajes. */
    var visible = false

    var compartirUbicacion by mutableStateOf(false)
        private set

    var tema by mutableStateOf(runCatching { TemaApp.valueOf(prefs.getString("tema", null)!!) }.getOrDefault(TemaApp.SISTEMA))
        private set

    fun cambiarTema(t: TemaApp) {
        tema = t
        prefs.edit { putString("tema", t.name) }
    }

    /** Hora en que se abrió por última vez cada conversación (clave de Chat → ms), para contar no leídos. */
    var leidos by mutableStateOf(
        prefs.getStringSet("leidos", emptySet())!!.mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0L) } }.toMap(),
    )
        private set

    fun marcarLeido(c: Chat) {
        leidos = leidos + (c.clave() to System.currentTimeMillis())
        prefs.edit { putStringSet("leidos", leidos.map { (k, v) -> "$k=$v" }.toSet()) }
    }

    /** Claro por defecto: el oscuro casi no se lee al sol. */
    var estiloClaro by mutableStateOf(prefs.getBoolean("estilo_claro", true))
        private set

    fun alternarEstilo() {
        estiloClaro = !estiloClaro
        prefs.edit { putBoolean("estilo_claro", estiloClaro) }
    }

    // --- Modo ruta: compañeros del grupo y alertas ---

    /** Nodos marcados como "de mi grupo" en la pestaña Ruta; solo ellos generan alertas. */
    var grupo by mutableStateOf(prefs.getStringSet("grupo", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet())
        private set

    var umbralKm by mutableStateOf(prefs.getInt("umbral_km", 2))
        private set

    fun alternarGrupo(num: Int) {
        grupo = if (num in grupo) grupo - num else grupo + num
        prefs.edit { putStringSet("grupo", grupo.map { it.toString() }.toSet()) }
    }

    fun cambiarUmbral(km: Int) {
        umbralKm = km
        prefs.edit { putInt("umbral_km", km) }
    }

    private fun vigilarGrupo() =
        scope.launch {
            var activas = emptyMap<Int, Alerta>()
            while (true) {
                val st = ble.state.value
                val ahora = alertas(companeros(st, System.currentTimeMillis() / 1000), grupo, umbralKm * 1000.0)
                // Solo avisa al aparecer o cambiar una alerta, no en cada revisión.
                ahora.filter { (num, a) -> activas[num] != a }.forEach { (num, a) -> avisarAlerta(st, num, a) }
                activas = ahora
                delay(30_000)
            }
        }

    private fun avisarAlerta(
        st: MeshState,
        num: Int,
        a: Alerta,
    ) {
        if (!tiene(Manifest.permission.POST_NOTIFICATIONS)) return
        val texto =
            when (a) {
                Alerta.LEJOS -> "Está a más de $umbralKm km de ti"
                Alerta.SIN_SENAL -> "Sin señal desde hace más de ${SIN_SENAL_SEG / 60} min"
            }
        val n =
            NotificationCompat
                .Builder(app, CANAL_MENSAJES)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle("Ruta · ${st.nombre(num)}")
                .setContentText(texto)
                .setContentIntent(abrirApp(app))
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(app).notify(ID_ALERTA + num, n)
    }

    fun conectar(d: BluetoothDevice) {
        prefs.edit { putString("ultimo_nodo", d.address) }
        ble.conectar(d)
        MeshService.actualizar(app)
        if (prefs.getBoolean("compartir_ubicacion", false)) alternarUbicacion(true)
    }

    fun desconectar() {
        prefs.edit { remove("ultimo_nodo") }
        ble.desconectar()
        compartirUbicacion = false
        guardarEnVivo(emptyMap())
        actualizarEscucha()
        MeshService.detener(app)
    }

    // --- Ubicación del teléfono para el nodo ---

    private val lm get() = app.getSystemService(LocationManager::class.java)

    private var escuchando = false
    private val ultimoEnvio = mutableMapOf<String, Long>()

    /** El servicio pide el tipo "location" mientras se escucha el GPS. */
    val usaUbicacion get() = escuchando

    private val escuchaUbicacion =
        LocationListener { l ->
            // Al nodo solo si se comparte con los canales; el estado local siempre (distancias, en vivo).
            ble.enviarPosicion(l.latitude, l.longitude, if (l.hasAltitude()) l.altitude.toInt() else null, alNodo = compartirUbicacion)
            enviarEnVivo()
        }

    /** "Compartir con mis canales": el nodo difunde la ubicación según la precisión de cada canal. */
    fun alternarUbicacion(activar: Boolean) {
        prefs.edit { putBoolean("compartir_ubicacion", activar) }
        compartirUbicacion = activar && tiene(Manifest.permission.ACCESS_FINE_LOCATION)
        actualizarEscucha()
    }

    /** Destinos con ubicación en vivo: clave de Chat → hasta (ms). */
    var enVivo by mutableStateOf(prefs.getStringSet("en_vivo", emptySet())!!.mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0L) } }.toMap())
        private set

    private fun guardarEnVivo(m: Map<String, Long>) {
        enVivo = m
        prefs.edit { putStringSet("en_vivo", m.map { (k, v) -> "$k=$v" }.toSet()) }
    }

    /** Ubicación exacta solo a [c], cada minuto, durante [minutos] (0 = una sola vez); null la detiene. */
    fun compartirEnVivo(
        c: Chat,
        minutos: Int?,
    ) {
        val k = c.clave()
        ultimoEnvio.remove(k)
        // 0 = "enviar ahora": una ventana de 30 s alcanza para un solo envío (el intervalo es 60 s).
        val ms = if (minutos == 0) 30_000L else (minutos ?: 0) * 60_000L
        guardarEnVivo(if (minutos == null) enVivo - k else enVivo + (k to System.currentTimeMillis() + ms))
        actualizarEscucha()
        enviarEnVivo()
    }

    fun detenerEnVivo() {
        guardarEnVivo(emptyMap())
        actualizarEscucha()
    }

    private fun enviarEnVivo() {
        val ahora = System.currentTimeMillis()
        val vivas = vigentes(enVivo, ahora)
        if (vivas != enVivo) {
            guardarEnVivo(vivas)
            actualizarEscucha()
        }
        vivas.keys.forEach { k ->
            val previo = ultimoEnvio[k]
            val c = chatDeClave(k) ?: return@forEach
            // Si aún no hay posición del GPS no se marca como enviado: se reintenta en la siguiente vuelta.
            if (tocaEnviar(previo, ahora) && ble.enviarPosicionA(c, registrar = previo == null)) ultimoEnvio[k] = ahora
        }
    }

    /** Enciende o apaga el GPS según lo que se esté compartiendo. */
    private fun actualizarEscucha() {
        val necesita = (compartirUbicacion || enVivo.isNotEmpty()) && tiene(Manifest.permission.ACCESS_FINE_LOCATION)
        if (necesita && !escuchando) {
            escuchando = true
            val proveedor =
                if (Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER else LocationManager.GPS_PROVIDER
            // Cada 30 s o 25 m como mínimo: más seguido saturaría el canal LoRa.
            lm.requestLocationUpdates(proveedor, 30_000L, 25f, escuchaUbicacion, Looper.getMainLooper())
            lm.getLastKnownLocation(proveedor)?.let(escuchaUbicacion::onLocationChanged)
        } else if (!necesita && escuchando) {
            escuchando = false
            lm.removeUpdates(escuchaUbicacion)
        }
        // El servicio necesita el tipo "location" para seguir recibiendo ubicación con la pantalla apagada.
        if (ble.dispositivo != null) MeshService.actualizar(app)
    }

    // --- Emergencia ---

    /**
     * Manda un mensaje de emergencia a [c]. Estoy bien y Necesito ayuda llevan la ubicación exacta una vez;
     * SOS la deja en vivo 1 hora para que puedan encontrarte.
     */
    fun emergencia(
        c: Chat,
        tipo: TipoEmergencia,
    ) {
        ble.enviarTexto(tipo.texto, c)
        when (tipo) {
            TipoEmergencia.BIEN, TipoEmergencia.AYUDA -> compartirEnVivo(c, 0)
            TipoEmergencia.SOS -> compartirEnVivo(c, 60)
            TipoEmergencia.PASE_DE_LISTA -> Unit
        }
    }

    // --- Prueba de alcance ---

    var alcanceActivo by mutableStateOf(false)
        private set
    val muestras = mutableStateListOf<Muestra>()

    fun alternarAlcance(activar: Boolean) {
        if (activar) muestras.clear()
        alcanceActivo = activar
    }

    private fun registrarAlcance() =
        scope.launch {
            ble.paquetes.collect { p -> if (alcanceActivo) muestras += muestra(ble.state.value, p, System.currentTimeMillis()) }
        }

    private fun latidoEnVivo() =
        scope.launch {
            while (true) {
                delay(15_000)
                if (enVivo.isNotEmpty()) enviarEnVivo()
            }
        }

    // --- Historial ---

    private fun cargarHistorial(): MeshState =
        runCatching { Historial.ADAPTER.decode(archivo.readFully()).aEstado() }
            .onFailure { if (archivo.baseFile.exists()) Log.w("Malla", "historial ilegible", it) }
            .getOrDefault(MeshState())

    @OptIn(FlowPreview::class)
    private fun guardarHistorialAlCambiar() =
        scope.launch {
            ble.state
                .map { Triple(it.miNodo, it.mensajes, it.nodos) }
                .distinctUntilChanged()
                .debounce(2_000)
                .collect {
                    val bytes = ble.state.value.aHistorial().encode()
                    withContext(Dispatchers.IO) {
                        val out = archivo.startWrite()
                        runCatching { out.write(bytes) }
                            .onSuccess { archivo.finishWrite(out) }
                            .onFailure { archivo.failWrite(out) }
                    }
                }
        }

    // --- Avisos ---

    private fun avisarMensajesNuevos() =
        scope.launch {
            var vistos = ble.state.value.mensajes.size
            ble.state.collect { st ->
                val nuevos = st.mensajes.drop(vistos).filter { it.entrega == Entrega.RECIBIDO }
                vistos = st.mensajes.size
                if (!tiene(Manifest.permission.POST_NOTIFICATIONS)) return@collect
                nuevos.forEach { m ->
                    // SOS y ayuda suenan aunque la app esté abierta; lo demás solo con la app en segundo plano.
                    val urgente = tipoEmergencia(m.texto)?.urgente() == true
                    if (visible && !urgente) return@forEach
                    val n =
                        NotificationCompat
                            .Builder(app, if (urgente) CANAL_SOS else CANAL_MENSAJES)
                            .setPriority(if (urgente) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
                            .setCategory(if (urgente) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
                            .setSmallIcon(R.drawable.ic_notificacion)
                            .setContentTitle("${st.nombre(m.de)} · ${st.nombre(m.chat(st.miNodo))}")
                            .setContentText(m.texto)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(m.texto))
                            .setContentIntent(abrirApp(app))
                            .setAutoCancel(true)
                            .build()
                    NotificationManagerCompat.from(app).notify(m.id, n)
                }
            }
        }

    private fun crearCanalesDeNotificacion() {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_SERVICIO, "Conexión con el nodo", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CANAL_MENSAJES, "Mensajes", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(
            NotificationChannel(CANAL_SOS, "SOS y pedidos de ayuda", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alertas de emergencia de tu familia o grupo"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            },
        )
    }

    /** Si la app se cerró con un nodo conectado, al volver a abrirla se reconecta sola. */
    private fun reconectarUltimoNodo() {
        val dir = prefs.getString("ultimo_nodo", null) ?: return
        if (Build.VERSION.SDK_INT >= 31 && !tiene(Manifest.permission.BLUETOOTH_CONNECT)) return
        val adaptador = app.getSystemService(BluetoothManager::class.java).adapter ?: return
        conectar(adaptador.getRemoteDevice(dir))
    }

    fun tiene(permiso: String): Boolean =
        if (permiso == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < 33) {
            true
        } else {
            app.checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED
        }

    // Al final a propósito: en Kotlin init corre en orden de declaración, y reconectar usa propiedades
    // declaradas arriba (escuchaUbicacion era null y tronaba al abrir con "Compartir ubicación" activo).
    init {
        crearCanalesDeNotificacion()
        vigilarGrupo()
        guardarHistorialAlCambiar()
        avisarMensajesNuevos()
        registrarAlcance()
        latidoEnVivo()
        reconectarUltimoNodo()
        actualizarEscucha() // reanuda sesiones en vivo guardadas
    }

    companion object {
        const val CANAL_SERVICIO = "servicio"
        const val CANAL_MENSAJES = "mensajes"
        const val CANAL_SOS = "sos"
        private const val ID_ALERTA = 1_000_000

        fun abrirApp(ctx: Context): PendingIntent =
            PendingIntent.getActivity(
                ctx,
                0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

class CanalCeroApp : Application() {
    val malla by lazy { Malla(this) }
}

val Context.malla: Malla get() = (applicationContext as CanalCeroApp).malla

/** Texto corto del estado para la notificación del servicio. */
fun MeshState.resumen(): String =
    when (estado) {
        Estado.LISTO -> "Conectado · ${nodos.size} nodos"
        Estado.RECONECTANDO -> "Reconectando con el nodo…"
        Estado.DESCONECTADO -> "Desconectado"
        else -> "Conectando…"
    }
