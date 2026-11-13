package io.github.ricardomonroy.canalcero.mesh

import io.github.ricardomonroy.canalcero.proto.Historial
import io.github.ricardomonroy.canalcero.proto.MensajeGuardado

/** Mensajes que se guardan en el teléfono; los más viejos se descartan. */
const val MAX_MENSAJES_GUARDADOS = 1000

fun MeshState.aHistorial(): Historial =
    Historial(
        mi_nodo = miNodo ?: 0,
        mensajes =
            mensajes.takeLast(MAX_MENSAJES_GUARDADOS).map {
                MensajeGuardado(it.id, it.de, it.para, it.canal, it.texto, it.hora, it.entrega.ordinal)
            },
        nodos = nodos.values.toList(),
    )

fun Historial.aEstado(): MeshState =
    MeshState(
        miNodo = mi_nodo.takeIf { it != 0 },
        mensajes =
            mensajes.map {
                val e = Entrega.entries.getOrElse(it.entrega) { Entrega.RECIBIDO }
                // Tras reiniciar la app ya no llegará el ack de un envío pendiente.
                Mensaje(it.id, it.de, it.para, it.canal, it.texto, it.hora, if (e == Entrega.ENVIANDO) Entrega.FALLO else e)
            },
        nodos = nodos.associateBy { it.num },
    )
