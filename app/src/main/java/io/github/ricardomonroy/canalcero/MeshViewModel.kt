package io.github.ricardomonroy.canalcero

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ricardomonroy.canalcero.mesh.MeshBle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Encontrado(
    val device: BluetoothDevice,
    val nombre: String,
    val rssi: Int,
)

// shortcut: la conexión vive con la Activity; si se cierra la app se pierde. Pasar a un foreground service en el modo emergencia.
@SuppressLint("MissingPermission") // La UI pide los permisos antes de llamar.
class MeshViewModel(
    app: Application,
) : AndroidViewModel(app) {
    val ble = MeshBle(app, viewModelScope)

    val encontrados = mutableStateMapOf<String, Encontrado>()
    var escaneando by mutableStateOf(false)
        private set

    private val scanner get() = getApplication<Application>().getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner

    private val scanCb =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                r: ScanResult,
            ) {
                val nombre = r.scanRecord?.deviceName ?: r.device.name ?: r.device.address
                encontrados[r.device.address] = Encontrado(r.device, nombre, r.rssi)
            }
        }

    fun escanear() {
        val s = scanner ?: return
        if (escaneando) return
        encontrados.clear()
        escaneando = true
        // Solo nodos que anuncian el servicio Meshtastic.
        val filtro = ScanFilter.Builder().setServiceUuid(ParcelUuid(MeshBle.SERVICIO)).build()
        s.startScan(listOf(filtro), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        viewModelScope.launch {
            delay(15_000)
            detenerEscaneo()
        }
    }

    fun detenerEscaneo() {
        if (!escaneando) return
        scanner?.stopScan(scanCb)
        escaneando = false
    }

    fun conectar(e: Encontrado) {
        detenerEscaneo()
        ble.conectar(e.device)
    }

    override fun onCleared() {
        detenerEscaneo()
        ble.desconectar()
    }
}
