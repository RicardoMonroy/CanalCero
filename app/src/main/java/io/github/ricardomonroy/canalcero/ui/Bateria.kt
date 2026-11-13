package io.github.ricardomonroy.canalcero.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.ricardomonroy.canalcero.Malla
import io.github.ricardomonroy.canalcero.R
import io.github.ricardomonroy.canalcero.mesh.Estado
import io.github.ricardomonroy.canalcero.mesh.MeshState

fun sinRestriccionBateria(ctx: Context): Boolean = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

/**
 * Estado de la exención de batería y la acción para pedirla. Se vuelve a leer al regresar del diálogo del sistema.
 * Sin la exención, Android (sobre todo Samsung y Xiaomi) puede dormir la app y dejar de recibir con la pantalla apagada.
 */
@Composable
fun recordarBateria(): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    var libre by remember { mutableStateOf(sinRestriccionBateria(ctx)) }
    val lanzar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { libre = sinRestriccionBateria(ctx) }
    return libre to {
        // App distribuida fuera de Play: pedir la exención directo es lo más simple para el usuario.
        @SuppressLint("BatteryLife")
        val directo = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${ctx.packageName}".toUri())
        runCatching { lanzar.launch(directo) }.onFailure { runCatching { lanzar.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    }
}

/** Aviso una vez conectado, hasta que se permita o se descarte. */
@Composable
fun AvisoBateria(
    st: MeshState,
    malla: Malla,
) {
    val (libre, pedir) = recordarBateria()
    if (libre || malla.asistente || malla.avisoBateriaOculto || st.estado != Estado.LISTO) return
    Row(
        Modifier.fillMaxWidth().background(Ambar.copy(alpha = 0.18f)).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icono(R.drawable.ic_error, null, tint = MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f)) {
            Text("Recibe con la pantalla apagada", style = MaterialTheme.typography.titleSmall)
            Text("Quita el ahorro de batería a CanalCero para no perder mensajes ni SOS.", style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = pedir) { Text("Permitir") }
            TextButton(onClick = { malla.ocultarAvisoBateria() }) { Text("Ahora no") }
        }
    }
}
