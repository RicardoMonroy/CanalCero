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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.util.AtomicFile
import io.github.ricardomonroy.canalcero.mesh.Alerta
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

    init {
        crearCanalesDeNotificacion()
        vigilarGrupo()
        guardarHistorialAlCambiar()
        avisarMensajesNuevos()
        reconectarUltimoNodo()
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
        detenerUbicacion()
        MeshService.detener(app)
    }

    // --- Ubicación del teléfono para el nodo ---

    private val lm get() = app.getSystemService(LocationManager::class.java)

    private val escuchaUbicacion =
        LocationListener { l -> ble.enviarPosicion(l.latitude, l.longitude, if (l.hasAltitude()) l.altitude.toInt() else null) }

    /** La UI pide ACCESS_FINE_LOCATION antes de activar. */
    fun alternarUbicacion(activar: Boolean) {
        prefs.edit { putBoolean("compartir_ubicacion", activar) }
        detenerUbicacion()
        if (activar && tiene(Manifest.permission.ACCESS_FINE_LOCATION)) {
            compartirUbicacion = true
            val proveedor =
                if (Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER else LocationManager.GPS_PROVIDER
            lm.getLastKnownLocation(proveedor)?.let(escuchaUbicacion::onLocationChanged)
            // Cada 30 s o 25 m como mínimo: más seguido saturaría el canal LoRa.
            lm.requestLocationUpdates(proveedor, 30_000L, 25f, escuchaUbicacion, Looper.getMainLooper())
        }
        // El servicio necesita el tipo "location" para seguir recibiendo ubicación con la pantalla apagada.
        if (ble.dispositivo != null) MeshService.actualizar(app)
    }

    private fun detenerUbicacion() {
        compartirUbicacion = false
        lm.removeUpdates(escuchaUbicacion)
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
                if (visible || !tiene(Manifest.permission.POST_NOTIFICATIONS)) return@collect
                nuevos.forEach { m ->
                    val n =
                        NotificationCompat
                            .Builder(app, CANAL_MENSAJES)
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

    companion object {
        const val CANAL_SERVICIO = "servicio"
        const val CANAL_MENSAJES = "mensajes"
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
