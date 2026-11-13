package io.github.ricardomonroy.canalcero.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Cliente BLE del protocolo Meshtastic:
 * https://meshtastic.org/docs/development/device/client-api/
 * Escribe ToRadio, y lee FromRadio hasta que venga vacío cada vez que FromNum notifica.
 */
@SuppressLint("MissingPermission") // La UI pide BLUETOOTH_CONNECT antes de llegar aquí.
class MeshBle(
    private val ctx: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        val SERVICIO: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        private val TO_RADIO = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        private val FROM_RADIO = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        private val FROM_NUM = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TAG = "MeshBle"
        private const val TIMEOUT_MS = 10_000L
    }

    private val _state = MutableStateFlow(MeshState())
    val state: StateFlow<MeshState> = _state

    private var gatt: BluetoothGatt? = null
    private var sesion: Job? = null

    // Android solo admite una operación GATT a la vez: este mutex las serializa.
    private val ops = Mutex()
    private var pendiente: CompletableDeferred<ByteArray>? = null

    fun conectar(device: BluetoothDevice) {
        desconectar()
        _state.value = MeshState(estado = Estado.CONECTANDO)
        sesion =
            scope.launch {
                try {
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        _state.update { it.copy(estado = Estado.EMPAREJANDO) }
                        emparejar(device)
                    }
                    _state.update { it.copy(estado = Estado.CONECTANDO) }
                    gatt = device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
                } catch (e: Exception) {
                    falla("No se pudo emparejar: ${e.message}")
                }
            }
    }

    /** Envía texto con want_ack; el estado pasa a ENTREGADO o FALLO cuando el nodo responde por ROUTING_APP. */
    fun enviarTexto(
        texto: String,
        chat: Chat,
    ) {
        val g = gatt ?: return
        val id = Random.nextInt(1, Int.MAX_VALUE)
        // En directos el firmware cifra con la llave pública del destino si la conoce (PKC, firmware 2.5+).
        val (para, canal) =
            when (chat) {
                is Chat.Canal -> BROADCAST to chat.indice
                is Chat.Directo -> chat.nodo to 0
            }
        val m = Mensaje(id, _state.value.miNodo ?: 0, para, canal, texto, System.currentTimeMillis(), Entrega.ENVIANDO)
        _state.update { it.copy(mensajes = it.mensajes + m) }
        val paquete =
            MeshPacket(
                to = para,
                channel = canal,
                id = id,
                want_ack = true,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = texto.encodeUtf8()),
            )
        scope.launch {
            runCatching { enviar(g, ToRadio(packet = paquete)) }.onFailure {
                _state.update { st -> st.copy(mensajes = st.mensajes.map { x -> if (x.id == id) x.copy(entrega = Entrega.FALLO) else x }) }
            }
        }
    }

    /**
     * Da al nodo la ubicación del teléfono (el Heltec V3 no tiene GPS). Va solo al nodo local (to = miNodo):
     * el firmware la retransmite según su intervalo y recortada a la precisión de cada canal.
     */
    fun enviarPosicion(
        lat: Double,
        lon: Double,
        alt: Int?,
    ) {
        val g = gatt ?: return
        val st = _state.value
        val yo = st.miNodo ?: return
        if (st.estado != Estado.LISTO) return
        val p =
            Position(
                latitude_i = (lat * 1e7).roundToInt(),
                longitude_i = (lon * 1e7).roundToInt(),
                altitude = alt,
                time = (System.currentTimeMillis() / 1000).toInt(),
                location_source = Position.LocSource.LOC_EXTERNAL,
            )
        _state.update { it.copy(nodos = it.nodos + (yo to (it.nodos[yo] ?: NodeInfo(num = yo)).copy(position = p))) }
        val paquete =
            MeshPacket(
                to = yo,
                id = Random.nextInt(1, Int.MAX_VALUE),
                decoded = Data(portnum = PortNum.POSITION_APP, payload = p.encodeByteString()),
            )
        scope.launch { runCatching { enviar(g, ToRadio(packet = paquete)) }.onFailure { Log.w(TAG, "posición no enviada", it) } }
    }

    // --- Administración del nodo local (AdminMessage por ADMIN_APP) ---

    // El nodo exige en cada set_* la session_passkey que manda en sus get_*_response; caduca a los 300 s.
    private var passkey: okio.ByteString = okio.ByteString.EMPTY
    private var passkeyHora = 0L
    private var esperaPasskey: CompletableDeferred<Unit>? = null

    private fun capturarPasskey(fr: FromRadio) {
        val d = fr.packet?.decoded?.takeIf { it.portnum == PortNum.ADMIN_APP } ?: return
        val k = runCatching { AdminMessage.ADAPTER.decode(d.payload).session_passkey }.getOrNull() ?: return
        if (k.size == 0) return
        passkey = k
        passkeyHora = System.currentTimeMillis()
        esperaPasskey?.complete(Unit)
    }

    private suspend fun enviarAdmin(
        g: BluetoothGatt,
        yo: Int,
        m: AdminMessage,
    ) = enviar(
        g,
        ToRadio(
            packet =
                MeshPacket(
                    to = yo,
                    id = Random.nextInt(1, Int.MAX_VALUE),
                    decoded = Data(portnum = PortNum.ADMIN_APP, payload = m.encodeByteString(), want_response = m.get_owner_request == true),
                ),
        ),
    )

    /** Ejecuta cambios de configuración en una sola transacción (begin/commit evita reinicios intermedios). */
    private suspend fun administrar(cambios: List<AdminMessage>) {
        val g = gatt ?: error("sin conexión")
        val yo = _state.value.miNodo ?: error("sin nodo")
        if (System.currentTimeMillis() - passkeyHora > 240_000) {
            val espera = CompletableDeferred<Unit>().also { esperaPasskey = it }
            enviarAdmin(g, yo, AdminMessage(get_owner_request = true))
            // La respuesta llega por FromRadio: vaciar() la captura.
            withTimeout(TIMEOUT_MS) {
                vaciar(g)
                espera.await()
            }
        }
        val todos = listOf(AdminMessage(begin_edit_settings = true)) + cambios + AdminMessage(commit_edit_settings = true)
        todos.forEach { enviarAdmin(g, yo, it.copy(session_passkey = passkey)) }
    }

    /** Cambia el nombre que ven los demás nodos (largo y corto de hasta 4 caracteres). */
    suspend fun renombrar(
        largo: String,
        corto: String,
    ) {
        val st = _state.value
        val yo = st.miNodo ?: return
        val nodo = st.nodos[yo] ?: NodeInfo(num = yo)
        val usuario = (nodo.user ?: User(id = idNodo(yo))).copy(long_name = largo, short_name = corto)
        administrar(listOf(AdminMessage(set_owner = usuario)))
        _state.update { it.copy(nodos = it.nodos + (yo to nodo.copy(user = usuario))) }
    }

    /** Agrega un canal secundario y deja el primario público sin compartir posición. */
    suspend fun agregarCanal(s: ChannelSettings) {
        val st = _state.value
        if (yaExiste(st.canales, s)) return
        val indice = indiceLibre(st.canales) ?: error("El nodo ya tiene 8 canales")
        val nuevo = Channel(index = indice, role = Channel.Role.SECONDARY, settings = s)
        val cambios = mutableListOf(AdminMessage(set_channel = nuevo))
        st.canales[0]?.let { cambios += AdminMessage(set_channel = primarioSinPosicion(it)) }
        administrar(cambios)
        _state.update { x -> cambios.mapNotNull { it.set_channel }.fold(x) { acc, c -> acc.conCanal(c) } }
    }

    /** Reemplaza una sección de configuración (position, device o lora). device y lora reinician el nodo. */
    suspend fun configurar(c: Config) {
        administrar(listOf(AdminMessage(set_config = c)))
        _state.update { it.aplicar(FromRadio(config = c), System.currentTimeMillis()) }
    }

    /**
     * Manda la última ubicación del teléfono, exacta, solo a [nodo] (cifrada con su llave pública si el firmware la tiene).
     * Requiere "Compartir mi ubicación" activo para tener una posición.
     */
    fun enviarPosicionA(nodo: Int) {
        val g = gatt ?: return
        val st = _state.value
        val p = st.miNodo?.let { st.nodos[it]?.position } ?: return
        val id = Random.nextInt(1, Int.MAX_VALUE)
        val exacta = p.copy(precision_bits = PRECISION_EXACTA, time = (System.currentTimeMillis() / 1000).toInt())
        val m = Mensaje(id, st.miNodo, nodo, 0, TEXTO_UBICACION_ENVIADA, System.currentTimeMillis(), Entrega.ENVIANDO)
        _state.update { it.copy(mensajes = it.mensajes + m) }
        val paquete =
            MeshPacket(
                to = nodo,
                id = id,
                want_ack = true,
                decoded = Data(portnum = PortNum.POSITION_APP, payload = exacta.encodeByteString()),
            )
        scope.launch {
            runCatching { enviar(g, ToRadio(packet = paquete)) }.onFailure {
                _state.update { st2 -> st2.copy(mensajes = st2.mensajes.map { x -> if (x.id == id) x.copy(entrega = Entrega.FALLO) else x }) }
            }
        }
    }

    fun desconectar() {
        sesion?.cancel()
        gatt?.close()
        gatt = null
        pendiente?.cancel()
        _state.value = MeshState()
    }

    /** Pide el PIN que muestra la pantalla del Heltec. Espera hasta que Android confirme el vínculo. */
    private suspend fun emparejar(device: BluetoothDevice) {
        val listo = CompletableDeferred<Unit>()
        val receptor =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    i: Intent,
                ) {
                    when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                        BluetoothDevice.BOND_BONDED -> listo.complete(Unit)
                        BluetoothDevice.BOND_NONE -> listo.completeExceptionally(IllegalStateException("emparejamiento rechazado"))
                    }
                }
            }
        // Exportado: el broadcast lo emite el proceso de Bluetooth, no esta app.
        ContextCompat.registerReceiver(
            ctx,
            receptor,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            check(device.createBond()) { "createBond falló" }
            withTimeout(60_000) { listo.await() }
        } finally {
            ctx.unregisterReceiver(receptor)
        }
    }

    private fun falla(msg: String) {
        Log.w(TAG, msg)
        gatt?.close()
        gatt = null
        _state.update { MeshState(error = msg) }
    }

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                g: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                when {
                    newState == BluetoothProfile.STATE_CONNECTED -> g.requestMtu(512)
                    newState == BluetoothProfile.STATE_DISCONNECTED -> falla("Conexión perdida (status $status)")
                }
            }

            override fun onMtuChanged(
                g: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
                g.discoverServices()
            }

            override fun onServicesDiscovered(
                g: BluetoothGatt,
                status: Int,
            ) {
                val s = g.getService(SERVICIO) ?: return falla("El dispositivo no tiene el servicio Meshtastic")
                scope.launch {
                    try {
                        handshake(g, s.getCharacteristic(TO_RADIO), s.getCharacteristic(FROM_RADIO), s.getCharacteristic(FROM_NUM))
                    } catch (e: Exception) {
                        falla("Error al descargar la configuración: ${e.message}")
                    }
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                c: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (c.uuid == FROM_NUM) scope.launch { runCatching { vaciar(g) } }
            }

            @Deprecated("API < 33")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                c: BluetoothGattCharacteristic,
            ) {
                if (Build.VERSION.SDK_INT < 33) onCharacteristicChanged(g, c, byteArrayOf())
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                c: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) = terminar(status, value)

            @Deprecated("API < 33")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                c: BluetoothGattCharacteristic,
                status: Int,
            ) {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < 33) terminar(status, c.value ?: byteArrayOf())
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                c: BluetoothGattCharacteristic,
                status: Int,
            ) = terminar(status, byteArrayOf())

            override fun onDescriptorWrite(
                g: BluetoothGatt,
                d: BluetoothGattDescriptor,
                status: Int,
            ) = terminar(status, byteArrayOf())
        }

    private fun terminar(
        status: Int,
        value: ByteArray,
    ) {
        val p = pendiente ?: return
        if (status == BluetoothGatt.GATT_SUCCESS) p.complete(value) else p.completeExceptionally(IllegalStateException("GATT status $status"))
    }

    /** Lanza una operación GATT y espera su callback. */
    private suspend fun op(lanzar: () -> Boolean): ByteArray =
        ops.withLock {
            val d = CompletableDeferred<ByteArray>()
            pendiente = d
            check(lanzar()) { "Android rechazó la operación GATT" }
            withTimeout(TIMEOUT_MS) { d.await() }
        }

    private suspend fun handshake(
        g: BluetoothGatt,
        toRadio: BluetoothGattCharacteristic,
        fromRadio: BluetoothGattCharacteristic,
        fromNum: BluetoothGattCharacteristic,
    ) {
        this.toRadio = toRadio
        this.fromRadio = fromRadio
        g.setCharacteristicNotification(fromNum, true)
        val cccd = fromNum.getDescriptor(CCCD)
        op {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }
        _state.update { it.copy(estado = Estado.DESCARGANDO) }
        // El nodo responde con my_info, node_info..., config y al final config_complete_id.
        enviar(g, ToRadio(want_config_id = Random.nextInt(1, Int.MAX_VALUE)))
        vaciar(g)
    }

    private var toRadio: BluetoothGattCharacteristic? = null
    private var fromRadio: BluetoothGattCharacteristic? = null

    private suspend fun enviar(
        g: BluetoothGatt,
        msg: ToRadio,
    ) {
        val c = toRadio ?: return
        val bytes = msg.encode()
        op {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        }
    }

    // Un solo vaciado a la vez: las notificaciones de FromNum pueden llegar mientras ya leemos.
    private val vaciando = Mutex()

    private suspend fun vaciar(g: BluetoothGatt) =
        vaciando.withLock {
            val c = fromRadio ?: return@withLock
            while (true) {
                val bytes = op { g.readCharacteristic(c) }
                if (bytes.isEmpty()) break
                val fr = FromRadio.ADAPTER.decode(bytes)
                capturarPasskey(fr)
                _state.update { it.aplicar(fr, System.currentTimeMillis()) }
            }
        }
}
