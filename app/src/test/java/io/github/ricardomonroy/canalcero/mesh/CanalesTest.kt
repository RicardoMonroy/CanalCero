package io.github.ricardomonroy.canalcero.mesh

import okio.ByteString.Companion.decodeBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleSettings

class CanalesTest {
    private val lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.US, use_preset = true)

    @Test
    fun canalPrivadoTieneLlaveAes256YPosicionExacta() {
        val a = canalPrivado("Familia")
        val b = canalPrivado("Familia")
        assertEquals(32, a.psk.size)
        assertFalse(a.psk == b.psk) // llave aleatoria cada vez
        assertEquals(PRECISION_EXACTA, a.module_settings?.position_precision)
    }

    @Test
    fun urlIdaYVuelta() {
        val s = canalPrivado("Rodada ñ")
        val url = urlCanal(s, lora)
        assertTrue(url.startsWith("https://meshtastic.org/e/?add=true#"))
        assertFalse(url.contains('=') && url.substringAfter('#').contains('=')) // sin relleno, como la app oficial
        assertEquals(s, leerUrlCanal(url))
        assertEquals(s, leerUrlCanal("  " + url.replace("?add=true", "") + "\n")) // formato sin add y con espacios
    }

    @Test
    fun urlInvalida() {
        assertNull(leerUrlCanal("hola"))
        assertNull(leerUrlCanal("https://meshtastic.org/e/#"))
        assertNull(leerUrlCanal("https://meshtastic.org/e/#%%%"))
    }

    @Test
    fun indicesYDuplicados() {
        val s = canalPrivado("Moto")
        val canales = mapOf(0 to Channel(index = 0), 1 to Channel(index = 1, settings = s), 3 to Channel(index = 3))
        assertEquals(2, indiceLibre(canales))
        assertNull(indiceLibre((0..7).associateWith { Channel(index = it) }))
        assertTrue(yaExiste(canales, s))
        assertFalse(yaExiste(canales, s.copy(psk = "AQ==".decodeBase64()!!)))
    }

    @Test
    fun primarioSinPosicionConservaLlaveYNombre() {
        val llave = "AQ==".decodeBase64()!!
        val p = Channel(index = 0, role = Channel.Role.PRIMARY, settings = ChannelSettings(psk = llave, module_settings = ModuleSettings(position_precision = 13, is_muted = true)))
        val r = primarioSinPosicion(p)
        assertEquals(0, r.settings?.module_settings?.position_precision)
        assertEquals(true, r.settings?.module_settings?.is_muted)
        assertEquals(llave, r.settings?.psk)
        assertEquals(Channel.Role.PRIMARY, r.role)
        // Sin module_settings también funciona.
        assertEquals(0, primarioSinPosicion(Channel(index = 0)).settings?.module_settings?.position_precision)
    }
}
