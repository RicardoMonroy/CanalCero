package io.github.ricardomonroy.canalcero.mesh

import io.github.ricardomonroy.canalcero.proto.Historial
import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

class HistorialTest {
    private fun m(
        id: Int,
        e: Entrega,
    ) = Mensaje(id, 0x8fccfdc0.toInt(), BROADCAST, 1, "hola ñ $id", 1_700_000_000_000L + id, e)

    @Test
    fun idaYVueltaPorBytes() {
        val nodo = NodeInfo(num = 42, user = User(long_name = "Moto"), position = Position(latitude_i = 205_888_000, longitude_i = -1_003_899_000))
        val st = MeshState(estado = Estado.LISTO, miNodo = 7, nodos = mapOf(42 to nodo), mensajes = listOf(m(1, Entrega.RECIBIDO), m(2, Entrega.ENTREGADO)))
        val vuelta = Historial.ADAPTER.decode(st.aHistorial().encode()).aEstado()

        assertEquals(Estado.DESCONECTADO, vuelta.estado) // la conexión no se guarda
        assertEquals(7, vuelta.miNodo)
        assertEquals(st.mensajes, vuelta.mensajes) // incluye broadcast (-1) y nodo > 2^31
        assertEquals(nodo, vuelta.nodos[42])
    }

    @Test
    fun pendienteSeMarcaFalloYSeRecorta() {
        val muchos = (1..MAX_MENSAJES_GUARDADOS + 5).map { m(it, Entrega.RECIBIDO) } + m(9999, Entrega.ENVIANDO)
        val vuelta = MeshState(mensajes = muchos).aHistorial().aEstado()
        assertEquals(MAX_MENSAJES_GUARDADOS, vuelta.mensajes.size)
        assertEquals(Entrega.FALLO, vuelta.mensajes.last().entrega)
        assertEquals(null, MeshState().aHistorial().aEstado().miNodo)
    }

    @Test
    fun reiniciarConservaHistorial() {
        val st = MeshState(estado = Estado.LISTO, miNodo = 7, firmware = "2.7", mensajes = listOf(m(1, Entrega.RECIBIDO)), error = "x")
        val r = st.reiniciar(Estado.RECONECTANDO, "perdida")
        assertEquals(Estado.RECONECTANDO, r.estado)
        assertEquals(st.mensajes, r.mensajes)
        assertEquals(7, r.miNodo)
        assertEquals(null, r.firmware) // se vuelve a descargar al reconectar
        assertEquals("perdida", r.error)
    }
}
