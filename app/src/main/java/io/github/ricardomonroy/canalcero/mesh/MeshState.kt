package io.github.ricardomonroy.canalcero.mesh

import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.NodeInfo

enum class Estado { DESCONECTADO, CONECTANDO, EMPAREJANDO, DESCARGANDO, LISTO }

data class MeshState(
    val estado: Estado = Estado.DESCONECTADO,
    val miNodo: Int? = null,
    val firmware: String? = null,
    val nodos: Map<Int, NodeInfo> = emptyMap(),
    val error: String? = null,
)

/** Aplica un FromRadio al estado. Pura: sin Android, para poder probarla en JVM. */
fun MeshState.aplicar(fr: FromRadio): MeshState =
    when {
        fr.my_info != null -> copy(miNodo = fr.my_info.my_node_num)
        fr.node_info != null -> copy(nodos = nodos + (fr.node_info.num to fr.node_info))
        fr.metadata != null -> copy(firmware = fr.metadata.firmware_version)
        fr.config_complete_id != null -> copy(estado = Estado.LISTO)
        else -> this
    }

/** Id de nodo en el formato que muestra Meshtastic: !8fccfdc0 */
fun idNodo(num: Int): String = "!%08x".format(num)
