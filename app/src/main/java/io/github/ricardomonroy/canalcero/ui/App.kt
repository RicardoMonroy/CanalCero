package io.github.ricardomonroy.canalcero.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ricardomonroy.canalcero.MeshViewModel
import io.github.ricardomonroy.canalcero.R
import io.github.ricardomonroy.canalcero.mesh.Chat
import io.github.ricardomonroy.canalcero.mesh.Estado
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.idNodo
import io.github.ricardomonroy.canalcero.mesh.noLeidos

enum class Seccion(
    val nombre: String,
    @DrawableRes val icono: Int,
) {
    CHATS("Chats", R.drawable.ic_chat),
    MAPA("Mapa", R.drawable.ic_map),
    RUTA("Ruta", R.drawable.ic_route),
    NODOS("Nodos", R.drawable.ic_cell_tower),
}

val PERMISOS_BLE =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

// Avisos de mensajes con la app cerrada; opcional: si se niega, la app funciona igual.
val PERMISOS_AVISOS = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

@Composable
fun CanalCeroApp(vm: MeshViewModel = viewModel()) {
    val st by vm.ble.state.collectAsState()
    val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        if (PERMISOS_AVISOS.any { !vm.malla.tiene(it) }) pedirAvisos.launch(PERMISOS_AVISOS)
    }
    if (st.estado == Estado.DESCONECTADO) PantallaConexion(vm, st.error) else Principal(st, vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Principal(
    st: MeshState,
    vm: MeshViewModel,
) {
    val malla = vm.malla
    var seccion by rememberSaveable { mutableStateOf(Seccion.CHATS) }
    var abierto by rememberSaveable(stateSaver = ChatSaver) { mutableStateOf<Chat?>(null) }
    var ajustes by rememberSaveable { mutableStateOf<SubAjuste?>(null) }
    var enfocar by remember { mutableStateOf<Int?>(null) }
    var verNodo by remember { mutableStateOf(false) }

    val verEnMapa = { num: Int ->
        abierto = null
        ajustes = null
        enfocar = num
        seccion = Seccion.MAPA
    }
    val abrirChat = { c: Chat ->
        ajustes = null
        abierto = c
    }

    when {
        abierto != null -> BackHandler { abierto = null }
        ajustes != null -> BackHandler { ajustes = if (ajustes == SubAjuste.INICIO) null else SubAjuste.INICIO }
    }
    if (verNodo) DialogoMiNodo(st, { verNodo = false }) { malla.desconectar() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (abierto != null || ajustes != null) {
                        IconButton(onClick = {
                            if (abierto != null) {
                                abierto = null
                            } else {
                                ajustes = if (ajustes == SubAjuste.INICIO) null else SubAjuste.INICIO
                            }
                        }) { Icono(R.drawable.ic_arrow_back, "Atrás", tint = MaterialTheme.colorScheme.onSurface) }
                    }
                },
                title = {
                    val c = abierto
                    when {
                        c != null -> TituloChat(st, c)
                        ajustes != null -> Text(ajustes!!.titulo)
                        else -> Text(seccion.nombre)
                    }
                },
                actions = {
                    if (abierto == null && ajustes == null) {
                        EstadoNodo(st) { verNodo = true }
                        IconButton(onClick = { ajustes = SubAjuste.INICIO }) { Icono(R.drawable.ic_settings, "Ajustes", tint = MaterialTheme.colorScheme.onSurface) }
                    }
                },
            )
        },
        bottomBar = {
            if (abierto == null && ajustes == null) {
                val pendientes = st.noLeidos(malla.leidos).values.sum()
                NavigationBar {
                    Seccion.entries.forEach { s ->
                        NavigationBarItem(
                            selected = seccion == s,
                            onClick = { seccion = s },
                            icon = {
                                Box {
                                    Icono(s.icono, null, tint = if (seccion == s) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (s == Seccion.CHATS && pendientes > 0) Insignia(pendientes, Modifier.align(Alignment.TopEnd).padding(start = 14.dp))
                                }
                            },
                            label = { Text(s.nombre) },
                        )
                    }
                }
            }
        },
    ) { margen ->
        Column(Modifier.padding(margen).fillMaxSize()) {
            AvisoConexion(st)
            val c = abierto
            val a = ajustes
            when {
                c != null -> PantallaChat(st, c, vm.ble, malla, verEnMapa)
                a != null -> Ajustes(st, vm.ble, malla, a) { ajustes = it }
                seccion == Seccion.CHATS -> Chats(st, malla, abrirChat)
                seccion == Seccion.MAPA -> Mapa(st, Modifier.fillMaxSize(), enfocar).also { LaunchedEffect(enfocar) { enfocar = null } }
                seccion == Seccion.RUTA -> Ruta(st, malla)
                seccion == Seccion.NODOS -> Nodos(st, malla, abrirChat, verEnMapa)
            }
        }
    }
}

@Composable
fun Insignia(
    n: Int,
    modifier: Modifier = Modifier,
) = Text(
    if (n > 99) "99+" else "$n",
    modifier
        .clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.primary)
        .padding(horizontal = 6.dp, vertical = 1.dp),
    color = MaterialTheme.colorScheme.onPrimary,
    style = MaterialTheme.typography.labelSmall,
)

@Composable
private fun TituloChat(
    st: MeshState,
    c: Chat,
) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    AvatarChat(st, c, 36.dp)
    Column {
        Text(st.nombre(c), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                c is Chat.Canal && c.indice == 0 -> "Canal público"
                c is Chat.Canal -> "Canal privado"
                else -> "Mensaje directo · cifrado"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Avatar de una conversación: candado para canales privados, # para el público, iniciales para personas. */
@Composable
fun AvatarChat(
    st: MeshState,
    c: Chat,
    tamano: androidx.compose.ui.unit.Dp = 44.dp,
) = when {
    c is Chat.Canal && c.indice > 0 -> Avatar("", MaterialTheme.colorScheme.primaryContainer, tamano, MaterialTheme.colorScheme.onPrimaryContainer, R.drawable.ic_lock)
    c is Chat.Canal -> Avatar("", MaterialTheme.colorScheme.surfaceVariant, tamano, MaterialTheme.colorScheme.onSurfaceVariant, R.drawable.ic_tag)
    else -> {
        val n = (c as Chat.Directo).nodo
        Avatar(st.nodos[n]?.user?.short_name?.ifBlank { null } ?: idNodo(n).takeLast(4), tonoDe(n), tamano)
    }
}

/** Punto de color + nombre corto del nodo conectado. Verde listo, ámbar en proceso. */
@Composable
private fun EstadoNodo(
    st: MeshState,
    tocar: () -> Unit,
) {
    val color = if (st.estado == Estado.LISTO) VerdeOk else Ambar
    val nombre = st.miNodo?.let { st.nodos[it]?.user?.short_name?.ifBlank { null } ?: idNodo(it).takeLast(4) } ?: "…"
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = tocar)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(nombre, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AvisoConexion(st: MeshState) {
    val texto =
        when (st.estado) {
            Estado.EMPAREJANDO -> "Escribe el PIN que muestra la pantalla de tu nodo"
            Estado.CONECTANDO -> "Conectando con tu nodo…"
            Estado.DESCARGANDO -> "Descargando nodos… (${st.nodos.size})"
            Estado.RECONECTANDO -> "Reconectando: el nodo se reinicia o está fuera de alcance"
            else -> return
        }
    Row(
        Modifier.fillMaxWidth().background(Ambar.copy(alpha = 0.18f)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icono(R.drawable.ic_bluetooth_searching, null, tint = MaterialTheme.colorScheme.onSurface)
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DialogoMiNodo(
    st: MeshState,
    cerrar: () -> Unit,
    desconectar: () -> Unit,
) {
    val yo = st.miNodo
    AlertDialog(
        onDismissRequest = cerrar,
        icon = { Icono(R.drawable.ic_cell_tower, null) },
        title = { Text(yo?.let { st.nombre(it) } ?: "Tu nodo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Dato("Estado", if (st.estado == Estado.LISTO) "Conectado" else "Conectando…")
                yo?.let { Dato("ID", idNodo(it)) }
                st.firmware?.let { Dato("Firmware", it) }
                Dato("Nodos conocidos", "${st.nodos.size}")
            }
        },
        confirmButton = { TextButton(onClick = cerrar) { Text("Cerrar") } },
        dismissButton = {
            TextButton(onClick = {
                cerrar()
                desconectar()
            }) { Text("Desconectar", color = MaterialTheme.colorScheme.error) }
        },
    )
}

@Composable
fun Dato(
    etiqueta: String,
    valor: String,
) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(etiqueta, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(valor)
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
