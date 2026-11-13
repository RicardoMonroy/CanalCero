package io.github.ricardomonroy.canalcero.mesh

import org.junit.Assert.assertEquals
import org.junit.Test
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
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

    @Test
    fun posicionRecibidaYDistancia() {
        // Centro de Querétaro y presa de Zimapán (aprox.).
        val qro = Position(latitude_i = 205_888_000, longitude_i = -1_003_899_000, precision_bits = 32)
        val zim = Position(latitude_i = 207_180_000, longitude_i = -994_470_000, precision_bits = 13)
        var st = MeshState(miNodo = yo)
        st = st.aplicar(paquete(otro, BROADCAST, Data(portnum = PortNum.POSITION_APP, payload = zim.encodeByteString())), 0)
        // Posición vacía (sin fix) no borra la anterior.
        st = st.aplicar(paquete(otro, BROADCAST, Data(portnum = PortNum.POSITION_APP, payload = Position().encodeByteString())), 0)

        val donde = st.nodos[otro]?.position.grados()!!
        assertEquals(20.718, donde.first, 1e-6)
        assertEquals(true, st.nodos[otro]?.position?.esAproximada())
        val d = distancia(qro.grados()!!, donde)
        assertEquals(99_000.0, d, 2_000.0) // ~99 km en línea recta
        assertEquals("E", puntoCardinal(rumbo(qro.grados()!!, donde)))
        assertEquals(null, Position(latitude_i = 0, longitude_i = 0).grados())
        assertEquals(false, qro.esAproximada())
        assertEquals(true, Position(latitude_i = 1, longitude_i = 1).esAproximada()) // precisión desconocida
    }

    @Test
    fun ubicacionDirectaCreaMensajeYConfigSeGuarda() {
        val pos = Position(latitude_i = 205_888_000, longitude_i = -1_003_899_000, precision_bits = 32)
        var st = MeshState(miNodo = yo)
        st = st.aplicar(paquete(otro, yo, Data(portnum = PortNum.POSITION_APP, payload = pos.encodeByteString())), 0)
        st = st.aplicar(paquete(otro, BROADCAST, Data(portnum = PortNum.POSITION_APP, payload = pos.encodeByteString())), 0)
        assertEquals(listOf(TEXTO_UBICACION_RECIBIDA), st.mensajes.map { it.texto }) // la de broadcast no crea mensaje
        assertEquals(false, st.nodos[otro]?.position?.esAproximada())

        val pc = org.meshtastic.proto.Config.PositionConfig(position_broadcast_secs = 60)
        st = st.aplicar(viaBle(FromRadio(config = org.meshtastic.proto.Config(position = pc))), 0)
        st = st.aplicar(viaBle(FromRadio(config = org.meshtastic.proto.Config(lora = org.meshtastic.proto.Config.LoRaConfig(hop_limit = 3)))), 0)
        assertEquals(60, st.cfgPosicion?.position_broadcast_secs) // otra sección no la borra
        assertEquals(3, st.lora?.hop_limit)
    }

    @Test
    fun borrarEsLocalYPrecisa() {
        val canal = Mensaje(1, otro, BROADCAST, 1, "en canal", 0, Entrega.RECIBIDO)
        val directo = Mensaje(1, otro, yo, 0, "directo mismo id", 0, Entrega.RECIBIDO) // mismo id, otro chat
        val mio = Mensaje(1, yo, otro, 0, "mío mismo id", 0, Entrega.ENTREGADO) // mismo id, otro remitente
        val st = MeshState(miNodo = yo, mensajes = listOf(canal, directo, mio))

        assertEquals(listOf(canal), st.sinChat(Chat.Directo(otro)).mensajes) // el chat directo incluye los míos
        assertEquals(listOf(directo, mio), st.sinChat(Chat.Canal(1)).mensajes)
        assertEquals(listOf(canal, directo), st.sinMensaje(mio).mensajes) // (de, id) distingue
    }
}
