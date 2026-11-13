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
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.ToRadio
import java.util.UUID
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
                _state.update { it.aplicar(fr) }
            }
        }
}
