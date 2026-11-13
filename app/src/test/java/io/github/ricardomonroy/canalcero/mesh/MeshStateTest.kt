package io.github.ricardomonroy.canalcero.mesh

import org.junit.Assert.assertEquals
import org.junit.Test
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
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
        val st = secuencia.map(::viaBle).fold(MeshState(estado = Estado.DESCARGANDO)) { s, fr -> s.aplicar(fr, 0) }

        assertEquals(Estado.LISTO, st.estado)
        assertEquals(0x8fccfdc0.toInt(), st.miNodo)
        assertEquals("2.7.26", st.firmware)
        assertEquals(2, st.nodos.size)
        assertEquals("Moto R1", st.nodos[42]?.user?.long_name) // el último node_info gana
        assertEquals("!8fccfdc0", idNodo(st.miNodo!!))
    }

    private val yo = 100
    private val otro = 42

    private fun paquete(
        de: Int,
        para: Int,
        data: Data,
    ) = viaBle(FromRadio(packet = MeshPacket(from = de, to = para, id = 7, rx_snr = 6.5f, decoded = data)))

    @Test
    fun textoRecibidoEnCanalYDirecto() {
        var st = MeshState(miNodo = yo)
        st = st.aplicar(paquete(otro, BROADCAST, Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "hola canal ñ".encodeUtf8())), 5_000)
        st = st.aplicar(paquete(otro, yo, Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = "solo tú".encodeUtf8())), 6_000)

        assertEquals(listOf("hola canal ñ", "solo tú"), st.mensajes.map { it.texto })
        assertEquals(Chat.Canal(0), st.mensajes[0].chat(yo))
        assertEquals(Chat.Directo(otro), st.mensajes[1].chat(yo))
        assertEquals(6, st.nodos[otro]?.last_heard) // nodo nuevo aparece al recibir de él
        assertEquals(6.5f, st.nodos[otro]?.snr)
    }

    @Test
    fun ackMarcaEntregadoYErrorMarcaFallo() {
        val enviado = Mensaje(id = 55, de = yo, para = otro, canal = 0, texto = "x", hora = 0, entrega = Entrega.ENVIANDO)
        val otroEnviado = enviado.copy(id = 56)
        var st = MeshState(miNodo = yo, mensajes = listOf(enviado, otroEnviado))
        val ack = Routing(error_reason = Routing.Error.NONE).encodeByteString()
        val nak = Routing(error_reason = Routing.Error.MAX_RETRANSMIT).encodeByteString()
        st = st.aplicar(paquete(yo, yo, Data(portnum = PortNum.ROUTING_APP, payload = ack, request_id = 55)), 0)
        st = st.aplicar(paquete(yo, yo, Data(portnum = PortNum.ROUTING_APP, payload = nak, request_id = 56)), 0)

        assertEquals(listOf(Entrega.ENTREGADO, Entrega.FALLO), st.mensajes.map { it.entrega })
        assertEquals(Chat.Directo(otro), enviado.chat(yo)) // mi mensaje directo va al chat del destinatario
    }
}
