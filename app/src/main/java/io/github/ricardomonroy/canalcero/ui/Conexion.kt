package io.github.ricardomonroy.canalcero.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.canalcero.MeshViewModel
import io.github.ricardomonroy.canalcero.R
import io.github.ricardomonroy.canalcero.mesh.nivelRssi

/** Primera pantalla: cómo encender, buscar y emparejar un nodo, sin jerga. */
@Composable
fun PantallaConexion(
    vm: MeshViewModel,
    error: String?,
) = Surface(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val pedir =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
            if (PERMISOS_BLE.all { r[it] == true }) vm.escanear()
        }
    val buscar = {
        if (PERMISOS_BLE.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) vm.escanear() else pedir.launch(PERMISOS_BLE + PERMISOS_AVISOS)
    }

    LazyColumn(
        Modifier.safeDrawingPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)))
                Spacer(Modifier.height(12.dp))
                Text("CanalCero", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Mensajes y ubicación cuando no hay señal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Paso(1, "Enciende tu nodo LoRa y déjalo cerca del teléfono.")
                    Paso(2, "Toca Buscar nodos y elige el tuyo.")
                    Paso(3, "La primera vez, escribe el PIN que aparece en la pantalla del nodo.")
                }
            }
        }
        error?.let {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        item {
            Button(onClick = buscar, enabled = !vm.escaneando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (vm.escaneando) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(10.dp))
                    Text("Buscando…")
                } else {
                    Icono(R.drawable.ic_bluetooth_searching, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                    Text("Buscar nodos")
                }
            }
        }
        items(vm.encontrados.values.sortedByDescending { it.rssi }, key = { it.device.address }) { e ->
            Card(onClick = { vm.conectar(e) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar("", MaterialTheme.colorScheme.primaryContainer, 40.dp, MaterialTheme.colorScheme.onPrimaryContainer, R.drawable.ic_cell_tower)
                    Column(Modifier.weight(1f)) {
                        Text(e.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(if (nivelRssi(e.rssi) >= 3) "Muy cerca" else "Señal Bluetooth débil: acércalo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BarrasSenal(nivelRssi(e.rssi))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Paso(
    n: Int,
    texto: String,
) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Avatar("$n", MaterialTheme.colorScheme.primary, 28.dp, MaterialTheme.colorScheme.onPrimary)
    Text(texto, style = MaterialTheme.typography.bodyMedium)
}
