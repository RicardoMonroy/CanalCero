package io.github.ricardomonroy.canalcero

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano: mantiene vivo el proceso (y con él la conexión BLE de Malla) con la pantalla
 * apagada o la app cerrada. No tiene lógica propia; solo la notificación fija.
 */
class MeshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observando = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val m = malla
        val n = notificacion(m.ble.state.value.resumen())
        // Reiniciado por Android en segundo plano no se permite el tipo "location": sigue solo con Bluetooth.
        runCatching { ServiceCompat.startForeground(this, ID, n, tipos(m)) }
            .recoverCatching { ServiceCompat.startForeground(this, ID, n, tipos(m) and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION.inv()) }
            .onFailure {
                stopSelf()
                return START_NOT_STICKY
            }
        if (!observando) {
            observando = true
            scope.launch {
                m.ble.state.map { it.resumen() }.distinctUntilChanged().collect { texto ->
                    if (m.tiene(Manifest.permission.POST_NOTIFICATIONS)) {
                        @Suppress("MissingPermission")
                        NotificationManagerCompat.from(this@MeshService).notify(ID, notificacion(texto))
                    }
                }
            }
        }
        // Si Android mata el proceso, que lo reinicie: Malla se reconecta al último nodo.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
    }

    private fun notificacion(texto: String) =
        NotificationCompat
            .Builder(this, Malla.CANAL_SERVICIO)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("CanalCero")
            .setContentText(texto)
            .setContentIntent(Malla.abrirApp(this))
            .setOngoing(true)
            .build()

    // "location" solo si hay permiso y se comparte; pedirlo sin permiso hace que Android rechace el servicio.
    private fun tipos(m: Malla): Int {
        var t = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        if (Build.VERSION.SDK_INT >= 29 && m.usaUbicacion) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return t
    }

    companion object {
        private const val ID = 1

        /** Arranca el servicio o actualiza sus tipos (p. ej. al activar la ubicación). */
        fun actualizar(ctx: Context) {
            // Android 12+ prohíbe arrancarlo desde segundo plano; ahí la conexión sigue mientras viva el proceso.
            runCatching { ctx.startForegroundService(Intent(ctx, MeshService::class.java)) }
                .onFailure { android.util.Log.w("MeshService", "no se pudo iniciar", it) }
        }

        fun detener(ctx: Context) = ctx.stopService(Intent(ctx, MeshService::class.java))
    }
}
