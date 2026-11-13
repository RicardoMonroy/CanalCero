package io.github.ricardomonroy.canalcero.mesh

/**
 * Mensajes de emergencia. Viajan como texto normal (la app oficial de Meshtastic los muestra tal cual);
 * CanalCero los reconoce por el emoji inicial y los dibuja distinto.
 */
enum class TipoEmergencia(
    val prefijo: String,
    val texto: String,
) {
    BIEN("✅", "✅ Estoy bien"),
    AYUDA("⚠️", "⚠️ Necesito ayuda"),
    SOS("🆘", "🆘 SOS: necesito ayuda urgente"),
    PASE_DE_LISTA("📋", "📋 Pase de lista: ¿cómo están? Respondan Estoy bien o Necesito ayuda"),
}

fun tipoEmergencia(texto: String): TipoEmergencia? = TipoEmergencia.entries.firstOrNull { texto.trimStart().startsWith(it.prefijo) }

/** ¿Merece alerta fuerte (sonido aunque la app esté abierta)? */
fun TipoEmergencia.urgente(): Boolean = this == TipoEmergencia.SOS || this == TipoEmergencia.AYUDA

/** Estado de un pase de lista: quién respondió (bien/ayuda) y quién falta. */
data class PaseDeLista(
    val inicio: Mensaje,
    val respuestas: Map<Int, Mensaje>,
    val faltan: List<Int>,
)

/**
 * Respuestas al pase de lista [inicio] en su conversación: el último BIEN/AYUDA/SOS de cada nodo después de la
 * pregunta. Como los canales no tienen lista de miembros, "faltan" son quienes ya habían escrito en esa
 * conversación y aún no responden.
 */
fun MeshState.paseDeLista(inicio: Mensaje): PaseDeLista {
    val chat = inicio.chat(miNodo)
    val delChat = mensajes.filter { it.chat(miNodo) == chat }
    val respuestas =
        delChat
            .filter { it.hora >= inicio.hora && it.de != inicio.de && tipoEmergencia(it.texto).let { t -> t != null && t != TipoEmergencia.PASE_DE_LISTA } }
            .associateBy { it.de }
    val conocidos = delChat.map { it.de }.toSet() - inicio.de - miNodo
    return PaseDeLista(inicio, respuestas, (conocidos - respuestas.keys).filterNotNull().sorted())
}

/** SOS o pedidos de ayuda recibidos en las últimas [ventanaMs]: para el aviso rojo arriba de la app. */
fun MeshState.alertasRecibidas(
    ahora: Long,
    ventanaMs: Long = 2 * 3600_000L,
): List<Mensaje> =
    mensajes
        .filter { it.entrega == Entrega.RECIBIDO && ahora - it.hora <= ventanaMs && tipoEmergencia(it.texto)?.urgente() == true }
        // Si después dijo que está bien, la alerta de esa persona ya no aplica.
        .filter { a -> mensajes.none { it.de == a.de && it.hora > a.hora && tipoEmergencia(it.texto) == TipoEmergencia.BIEN } }
        .groupBy { it.de }
        .map { it.value.last() }
