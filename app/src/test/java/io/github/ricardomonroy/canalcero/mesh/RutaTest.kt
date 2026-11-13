package io.github.ricardomonroy.canalcero.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

class RutaTest {
    private val ahora = 1_800_000_000L
    private val yo = 1

    // ~0.009° de latitud ≈ 1 km.
    private fun nodo(
        num: Int,
        latKm: Double?,
        haceSeg: Long,
    ) = NodeInfo(
        num = num,
        user = User(long_name = "N$num"),
        last_heard = (ahora - haceSeg).toInt(),
        position = latKm?.let { Position(latitude_i = ((20.0 + it * 0.009) * 1e7).toInt(), longitude_i = -1_000_000_000, precision_bits = 32) },
    )

    private val st =
        MeshState(
            miNodo = yo,
            nodos =
                listOf(
                    nodo(yo, 0.0, 0),
                    nodo(2, 3.0, 30), // lejos
                    nodo(3, 0.5, 30), // cerca
                    nodo(4, 1.0, 20 * 60), // sin señal
                    nodo(5, null, 30), // sin posición
                    nodo(6, 0.2, 3 * 3600), // viejo: no aparece
                ).associateBy { it.num },
        )

    @Test
    fun companerosOrdenadosYFiltrados() {
        val l = companeros(st, ahora)
        assertEquals(listOf(3, 4, 2, 5), l.map { it.num }) // por distancia, sin posición al final; ni yo ni el viejo
        assertEquals(3000.0, l.first { it.num == 2 }.distancia!!, 50.0)
        assertEquals("N", puntoCardinal(l.first { it.num == 2 }.rumbo!!))
    }

    @Test
    fun alertasSoloDelGrupo() {
        val l = companeros(st, ahora)
        assertEquals(mapOf(2 to Alerta.LEJOS, 4 to Alerta.SIN_SENAL), alertas(l, setOf(2, 3, 4, 5), 2000.0))
        assertEquals(emptyMap<Int, Alerta>(), alertas(l, emptySet(), 2000.0))
        assertEquals(mapOf(4 to Alerta.SIN_SENAL), alertas(l, setOf(2, 4), 5000.0))
    }

    @Test
    fun teselasDeUnaZona() {
        assertEquals(1L, teselas(-10.0, -10.0, 10.0, 10.0, 0, 0)) // z0 = una tesela para todo el mundo
        // Una zona de ~30 km cabe holgada en el límite; una de medio país no.
        val chica = teselas(20.45, -100.55, 20.75, -100.25, 5, 14)
        assertTrue(chica in 100..2_000)
        assertTrue(teselas(17.0, -104.0, 23.0, -97.0, 5, 14) > 20_000)
    }

    @Test
    fun enVivoVenceYRespetaIntervalo() {
        val ses = mapOf("d2" to 1_000L, "c1" to 5_000L)
        assertEquals(mapOf("c1" to 5_000L), vigentes(ses, 2_000))
        assertTrue(tocaEnviar(null, 0))
        assertEquals(false, tocaEnviar(10_000, 10_000 + INTERVALO_EN_VIVO_MS - 1))
        assertTrue(tocaEnviar(10_000, 10_000 + INTERVALO_EN_VIVO_MS))
        listOf(Chat.Canal(3), Chat.Directo(-1890386496)).forEach { assertEquals(it, chatDeClave(it.clave())) }
        assertEquals(null, chatDeClave("x9"))
    }

    @Test
    fun muestraDeAlcanceYCsv() {
        val p = org.meshtastic.proto.MeshPacket(from = 2, rx_rssi = -97, rx_snr = -6.25f, hop_start = 3, hop_limit = 3)
        val m = muestra(st, p, 42)
        assertEquals(0, m.saltos)
        assertEquals(3000.0, m.distancia!!, 50.0)
        assertEquals(-97, m.rssi)
        val relevo = muestra(st, p.copy(hop_limit = 1), 43)
        assertEquals(2, relevo.saltos)
        assertEquals(null, muestra(st, p.copy(hop_start = 0), 44).saltos) // firmware viejo
        assertEquals(3000.0, alcanceDirecto(listOf(m, relevo))!!, 50.0) // el de 2 saltos no cuenta
        val csv = csvAlcance(listOf(m.copy(nombre = "Moto, \"R1\"")))
        assertTrue(csv.lines()[1].contains("\"Moto, \"\"R1\"\"\""))
        assertEquals(8, csv.lines()[0].split(',').size)
    }
}
