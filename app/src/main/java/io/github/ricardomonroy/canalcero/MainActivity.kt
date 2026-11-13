package io.github.ricardomonroy.canalcero

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.Estado
import io.github.ricardomonroy.canalcero.ui.PantallaChat
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.idNodo
import io.github.ricardomonroy.canalcero.ui.CanalCeroTheme
import org.meshtastic.proto.NodeInfo

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CanalCeroTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) { App() }
                }
            }
        }
    }
}

private val PERMISOS =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
private fun App(vm: MeshViewModel = viewModel()) {
    val st by vm.ble.state.collectAsState()
    Text("CanalCero", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    if (st.estado == Estado.DESCONECTADO) Escaneo(vm, st.error) else Conectado(st, vm)
}

@Composable
private fun Escaneo(
    vm: MeshViewModel,
    error: String?,
) {
    val ctx = LocalContext.current
    val pedir =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
            if (r.values.all { it }) vm.escanear()
        }
    val tienePermisos = { PERMISOS.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } }

    Text("Enciende tu nodo y búscalo.", Modifier.padding(vertical = 8.dp))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = { if (tienePermisos()) vm.escanear() else pedir.launch(PERMISOS) },
        enabled = !vm.escaneando,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) { Text(if (vm.escaneando) "Buscando…" else "Buscar nodos") }

    LazyColumn {
        items(vm.encontrados.values.sortedByDescending { it.rssi }, key = { it.device.address }) { e ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.conectar(e) }.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(e.nombre, style = MaterialTheme.typography.titleMedium)
                Text("${e.rssi} dBm")
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Conectado(
    st: MeshState,
    vm: MeshViewModel,
) {
    var abierto by rememberSaveable(stateSaver = ChatSaver) { mutableStateOf<Chat?>(null) }
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val texto =
        when (st.estado) {
            Estado.EMPAREJANDO -> "Escribe el PIN que muestra la pantalla del nodo"
            Estado.CONECTANDO -> "Conectando…"
            Estado.DESCARGANDO -> "Descargando nodos… (${st.nodos.size})"
            else -> "Conectado · firmware ${st.firmware ?: "?"}"
        }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { vm.ble.desconectar() }) { Text("Desconectar") }
    }

    abierto?.let { chat ->
        BackHandler { abierto = null }
        PantallaChat(st, chat, enviar = { vm.ble.enviarTexto(it, chat) }, volver = { abierto = null })
        return
    }

    TabRow(selectedTabIndex = pestana) {
        Tab(pestana == 0, { pestana = 0 }, text = { Text("Chats") })
        Tab(pestana == 1, { pestana = 1 }, text = { Text("Nodos (${st.nodos.size})") })
    }
    val ahora = System.currentTimeMillis() / 1000
    LazyColumn {
        if (pestana == 0) {
            val chats = (st.canales.keys.sorted().map { Chat.Canal(it) } + st.mensajes.map { it.chat(st.miNodo) }).distinct()
            items(chats, key = { it.toString() }) { c ->
                val ultimo = st.mensajes.lastOrNull { it.chat(st.miNodo) == c }
                Column(Modifier.fillMaxWidth().clickable { abierto = c }.padding(vertical = 16.dp)) {
                    Text((if (c is Chat.Canal) "# " else "") + st.nombre(c), style = MaterialTheme.typography.titleMedium)
                    ultimo?.let { Text("${st.nombre(it.de)}: ${it.texto}", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
                HorizontalDivider()
            }
        } else {
            items(st.nodos.values.sortedByDescending { it.last_heard }, key = { it.num }) { n ->
                val esMio = n.num == st.miNodo
                Box(Modifier.clickable(enabled = !esMio) { abierto = Chat.Directo(n.num) }) { FilaNodo(n, esMio, ahora) }
                HorizontalDivider()
            }
        }
    }
}

// Chat no es Parcelable: se guarda como [tipo, valor] para sobrevivir a rotaciones.
private val ChatSaver =
    listSaver<Chat?, Int>(
        save = { c ->
            when (c) {
                is Chat.Canal -> listOf(0, c.indice)
                is Chat.Directo -> listOf(1, c.nodo)
                null -> emptyList()
            }
        },
        restore = { l -> if (l.isEmpty()) null else if (l[0] == 0) Chat.Canal(l[1]) else Chat.Directo(l[1]) },
    )

@Composable
private fun FilaNodo(
    n: NodeInfo,
    esMio: Boolean,
    ahora: Long,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            (n.user?.long_name ?: idNodo(n.num)) + if (esMio) "  (tu nodo)" else "",
            style = MaterialTheme.typography.titleMedium,
            color = if (esMio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        val detalles =
            listOfNotNull(
                idNodo(n.num),
                n.user?.short_name,
                if (n.last_heard > 0) "visto hace ${hace(ahora - n.last_heard)}" else null,
                n.hops_away?.let { if (it == 0) "directo" else "$it saltos" },
                if (!esMio && n.snr != 0f) "SNR ${"%.1f".format(n.snr)} dB" else null,
            )
        Text(detalles.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
}

private fun hace(seg: Long): String =
    when {
        seg < 60 -> "${seg.coerceAtLeast(0)} s"
        seg < 3600 -> "${seg / 60} min"
        seg < 86400 -> "${seg / 3600} h"
        else -> "${seg / 86400} d"
    }
