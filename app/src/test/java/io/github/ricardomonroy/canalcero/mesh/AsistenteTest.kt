package io.github.ricardomonroy.canalcero.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ModuleSettings
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.User

class AsistenteTest {
    private fun canal(
        i: Int,
        prec: Int,
    ) = Channel(index = i, settings = ChannelSettings(module_settings = ModuleSettings(position_precision = prec)))

    private fun st(
        nombre: String,
        lora: org.meshtastic.proto.Config.LoRaConfig?,
        canales: Map<Int, Channel>,
    ) = MeshState(miNodo = 1, nodos = mapOf(1 to NodeInfo(num = 1, user = User(long_name = nombre))), lora = lora, canales = canales)

    @Test
    fun nodoDeFabricaPideTodo() {
        val s = st("Meshtastic d534", org.meshtastic.proto.Config.LoRaConfig(), mapOf(0 to canal(0, 13)))
        assertTrue(s.esNodoNuevo())
        assertEquals(listOf(Paso.REGION, Paso.NOMBRE, Paso.PRIVACIDAD, Paso.CANAL), s.pasosPendientes())
    }

    @Test
    fun nodoConfiguradoNoPideNada() {
        val s = st("Ricardo", null.paraMexico(), mapOf(0 to canal(0, 0), 1 to canal(1, 32)))
        assertFalse(s.esNodoNuevo())
        assertEquals(emptyList<Paso>(), s.pasosPendientes())
    }
}
