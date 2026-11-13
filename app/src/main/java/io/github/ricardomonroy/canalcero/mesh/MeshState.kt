package io.github.ricardomonroy.canalcero.mesh

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.meshtastic.proto.Channel
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User

enum class Estado { DESCONECTADO, CONECTANDO, EMPAREJANDO, DESCARGANDO, LISTO }

/** Destino "todos" de Meshtastic (0xFFFFFFFF). */
const val BROADCAST: Int = -1

/** Payload máximo de texto que acepta la app oficial. */
const val MAX_BYTES_TEXTO = 200

enum class Entrega { ENVIANDO, ENTREGADO, FALLO, RECIBIDO }

/** Una conversación: un canal (broadcast) o un chat directo con un nodo. */
sealed interface Chat {
    data class Canal(
        val indice: Int,
    ) : Chat

    data class Directo(
        val nodo: Int,
    ) : Chat
}

data class Mensaje(
    val id: Int,
    val de: Int,
    val para: Int,
    val canal: Int,
    val texto: String,
    val hora: Long,
    val entrega: Entrega,
) {
    fun chat(miNodo: Int?): Chat = if (para == BROADCAST) Chat.Canal(canal) else Chat.Directo(if (de == miNodo) para else de)
}

data class MeshState(
    val estado: Estado = Estado.DESCONECTADO,
    val miNodo: Int? = null,
    val firmware: String? = null,
    val nodos: Map<Int, NodeInfo> = emptyMap(),
    val canales: Map<Int, String> = emptyMap(),
    // shortcut: solo en memoria, se pierde al desconectar. Persistir (Room) en el modo emergencia.
    val mensajes: List<Mensaje> = emptyList(),
    val error: String? = null,
) {
    fun nombre(num: Int): String = nodos[num]?.user?.long_name?.ifBlank { null } ?: idNodo(num)

    fun nombre(chat: Chat): String =
        when (chat) {
            is Chat.Canal -> canales[chat.indice] ?: "Canal ${chat.indice}"
            is Chat.Directo -> nombre(chat.nodo)
        }
}

/** Aplica un FromRadio al estado. Pura: sin Android, para poder probarla en JVM. [ahora] en ms. */
fun MeshState.aplicar(
    fr: FromRadio,
    ahora: Long,
): MeshState =
    when {
        fr.my_info != null -> copy(miNodo = fr.my_info.my_node_num)
        fr.node_info != null -> copy(nodos = nodos + (fr.node_info.num to fr.node_info))
        fr.metadata != null -> copy(firmware = fr.metadata.firmware_version)
        fr.channel != null -> conCanal(fr.channel)
        fr.packet != null -> recibir(fr.packet, ahora)
        fr.config_complete_id != null -> copy(estado = Estado.LISTO)
        else -> this
    }

private fun MeshState.conCanal(c: Channel): MeshState =
    if (c.role == Channel.Role.DISABLED) {
        copy(canales = canales - c.index)
    } else {
        // El primario sin nombre usa el nombre del preset (LongFast) en la app oficial.
        copy(canales = canales + (c.index to (c.settings?.name?.ifBlank { null } ?: if (c.index == 0) "Principal" else "Canal ${c.index}")))
    }

private fun MeshState.recibir(
    p: MeshPacket,
    ahora: Long,
): MeshState {
    // Sin decoded = cifrado con un canal que no tenemos: no se puede leer.
    val d = p.decoded ?: return this
    val previo = nodos[p.from] ?: NodeInfo(num = p.from)
    var nodo = if (p.from == miNodo) previo else previo.copy(last_heard = (ahora / 1000).toInt(), snr = p.rx_snr)
    var st = this
    when (d.portnum) {
        PortNum.TEXT_MESSAGE_APP ->
            st = st.copy(mensajes = mensajes + Mensaje(p.id, p.from, p.to, p.channel, d.payload.utf8(), ahora, Entrega.RECIBIDO))

        PortNum.NODEINFO_APP -> nodo = nodo.copy(user = User.ADAPTER.decode(d.payload))

        PortNum.POSITION_APP -> Position.ADAPTER.decode(d.payload).takeIf { it.latitude_i != null }?.let { nodo = nodo.copy(position = it) }

        PortNum.ROUTING_APP -> {
            // Respuesta de enrutamiento a un paquete nuestro: request_id es el id que mandamos.
            val ok = (Routing.ADAPTER.decode(d.payload).error_reason ?: Routing.Error.NONE) == Routing.Error.NONE
            st =
                st.copy(
                    mensajes =
                        mensajes.map {
                            if (it.id == d.request_id && it.entrega == Entrega.ENVIANDO) it.copy(entrega = if (ok) Entrega.ENTREGADO else Entrega.FALLO) else it
                        },
                )
        }

        else -> Unit
    }
    return st.copy(nodos = st.nodos + (p.from to nodo))
}

/** Coordenadas en grados, o null si el nodo no reporta posición (0,0 = sin fix). */
fun Position?.grados(): Pair<Double, Double>? {
    val lat = this?.latitude_i ?: return null
    val lon = longitude_i ?: return null
    if (lat == 0 && lon == 0) return null
    return lat * 1e-7 to lon * 1e-7
}

/**
 * El firmware recorta la posición según el canal (p. ej. ±3 km en LongFast público). Solo 32 bits es exacta;
 * 0 = desconocida (la base de nodos del firmware no la guarda), así que también se trata como aproximada.
 */
fun Position.esAproximada(): Boolean = precision_bits != 32

/** Distancia en metros (haversine). */
fun distancia(
    a: Pair<Double, Double>,
    b: Pair<Double, Double>,
): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.first - a.first)
    val dLon = Math.toRadians(b.second - a.second)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** Rumbo inicial de a hacia b, 0–360° (0 = norte). */
fun rumbo(
    a: Pair<Double, Double>,
    b: Pair<Double, Double>,
): Double {
    val la = Math.toRadians(a.first)
    val lb = Math.toRadians(b.first)
    val dLon = Math.toRadians(b.second - a.second)
    val y = sin(dLon) * cos(lb)
    val x = cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

fun puntoCardinal(grados: Double): String = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[((grados + 22.5) / 45).toInt() % 8]

fun textoDistancia(m: Double): String = if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000)

/** Id de nodo en el formato que muestra Meshtastic: !8fccfdc0 */
fun idNodo(num: Int): String = "!%08x".format(num)
