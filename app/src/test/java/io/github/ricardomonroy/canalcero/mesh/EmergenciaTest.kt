package io.github.ricardomonroy.canalcero.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmergenciaTest {
    private val yo = 1
    private val mama = 2
    private val papa = 3
    private val tio = 4

    private fun m(
        de: Int,
        texto: String,
        hora: Long,
        canal: Int = 1,
    ) = Mensaje(hora.toInt(), de, BROADCAST, canal, texto, hora, if (de == yo) Entrega.ENTREGADO else Entrega.RECIBIDO)

    @Test
    fun reconoceTiposPorPrefijo() {
        TipoEmergencia.entries.forEach { assertEquals(it, tipoEmergencia(it.texto)) }
        assertEquals(TipoEmergencia.BIEN, tipoEmergencia("  ✅ Estoy bien, en casa")) // la app oficial puede agregar texto
        assertNull(tipoEmergencia("hola ✅"))
    }

    @Test
    fun paseDeListaCuentaRespuestasYFaltantes() {
        val pase = m(yo, TipoEmergencia.PASE_DE_LISTA.texto, 100)
        val st =
            MeshState(
                miNodo = yo,
                mensajes =
                    listOf(
                        m(tio, "hola a todos", 10), // escribió antes: es de este canal
                        pase,
                        m(mama, TipoEmergencia.BIEN.texto, 110),
                        m(papa, TipoEmergencia.AYUDA.texto, 120),
                        m(papa, "se me cayó el teléfono", 130), // texto normal no es respuesta
                        m(mama, TipoEmergencia.BIEN.texto, 50, canal = 2), // otro canal: no cuenta
                    ),
            )
        val p = st.paseDeLista(pase)
        assertEquals(setOf(mama, papa), p.respuestas.keys)
        assertEquals(TipoEmergencia.AYUDA, tipoEmergencia(p.respuestas[papa]!!.texto))
        assertEquals(listOf(tio), p.faltan)
    }

    @Test
    fun alertaSeQuitaConEstoyBienPosterior() {
        val sos = m(papa, TipoEmergencia.SOS.texto, 1_000)
        val base = MeshState(miNodo = yo, mensajes = listOf(sos, m(mama, TipoEmergencia.AYUDA.texto, 2_000)))
        assertEquals(setOf(papa, mama), base.alertasRecibidas(3_000).map { it.de }.toSet())
        val resuelto = base.copy(mensajes = base.mensajes + m(papa, TipoEmergencia.BIEN.texto, 2_500))
        assertEquals(listOf(mama), resuelto.alertasRecibidas(3_000).map { it.de })
        assertEquals(emptyList<Mensaje>(), base.alertasRecibidas(1_000 + 3 * 3600_000L)) // vencidas
    }
}
