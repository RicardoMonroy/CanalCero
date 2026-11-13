package io.github.ricardomonroy.canalcero.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.Config

class PerfilesTest {
    @Test
    fun perfilSeReconoceYConservaOtrosCampos() {
        val base = Config.PositionConfig(gps_mode = Config.PositionConfig.GpsMode.NOT_PRESENT, fixed_position = false)
        PerfilPosicion.entries.forEach { p ->
            val c = p.aplicar(base)
            assertEquals(p, PerfilPosicion.de(c))
            assertEquals(Config.PositionConfig.GpsMode.NOT_PRESENT, c.gps_mode) // set_config reemplaza todo: no perder campos
        }
        assertEquals(60, PerfilPosicion.RODADA.aplicar(base).position_broadcast_secs)
        assertNull(PerfilPosicion.de(base.copy(position_broadcast_secs = 123)))
    }

    @Test
    fun regionParaMexico() {
        assertTrue(null.sinRegion())
        assertTrue(Config.LoRaConfig().sinRegion())
        val l = Config.LoRaConfig(hop_limit = 5).paraMexico()
        assertFalse(l.sinRegion())
        assertEquals(Config.LoRaConfig.RegionCode.US, l.region)
        assertEquals(Config.LoRaConfig.ModemPreset.LONG_FAST, l.modem_preset)
        assertEquals(5, l.hop_limit)
    }

    @Test
    fun rolesSinDuplicados() = assertEquals(ROLES.size, ROLES.map { it.rol }.toSet().size)
}
