package io.github.ricardomonroy.canalcero.mesh

import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.User

class MeshStateTest {
    // Simula el handshake tal como llega por BLE: bytes codificados, decodificados con el mismo ADAPTER.
    private fun viaBle(fr: FromRadio) = FromRadio.ADAPTER.decode(fr.encode())

    @Test
    fun handshakeCompleto() {
        val secuencia =
            listOf(
                FromRadio(my_info = MyNodeInfo(my_node_num = 0x8fccfdc0.toInt())),
                FromRadio(node_info = NodeInfo(num = 0x8fccfdc0.toInt(), user = User(long_name = "Casa", short_name = "CASA"))),
                FromRadio(node_info = NodeInfo(num = 42, user = User(long_name = "Moto"), hops_away = 1)),
                FromRadio(node_info = NodeInfo(num = 42, user = User(long_name = "Moto R1"), hops_away = 0)),
                FromRadio(metadata = DeviceMetadata(firmware_version = "2.7.26")),
                FromRadio(config_complete_id = 1234),
            )
        val st = secuencia.map(::viaBle).fold(MeshState(estado = Estado.DESCARGANDO)) { s, fr -> s.aplicar(fr) }

        assertEquals(Estado.LISTO, st.estado)
        assertEquals(0x8fccfdc0.toInt(), st.miNodo)
        assertEquals("2.7.26", st.firmware)
        assertEquals(2, st.nodos.size)
        assertEquals("Moto R1", st.nodos[42]?.user?.long_name) // el último node_info gana
        assertEquals("!8fccfdc0", idNodo(st.miNodo!!))
    }
}
