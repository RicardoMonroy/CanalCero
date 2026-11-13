package io.github.ricardomonroy.canalcero.mesh

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.RouteDiscovery
import org.meshtastic.proto.Routing
import org.meshtastic.proto.User

enum class Estado { DESCONECTADO, CONECTANDO, EMPAREJANDO, DESCARGANDO, LISTO, RECONECTANDO }

/** Destino "todos" de Meshtastic (0xFFFFFFFF). */
const val BROADCAST: Int = -1

const val TEXTO_UBICACION_RECIBIDA = "📍 Te compartió su ubicación (míralo en el mapa)"
const val AVISO_UBICACION_MS = 15 * 60_000L
const val TEXTO_UBICACION_ENVIADA = "📍 Le enviaste tu ubicación exacta"

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
    /** Canales activos por índice (0 = primario). Se guardan completos: para editarlos hay que reenviarlos enteros. */
    val canales: Map<Int, Channel> = emptyMap(),
    val lora: Config.LoRaConfig? = null,
    val cfgPosicion: Config.PositionConfig? = null,
    val cfgDispositivo: Config.DeviceConfig? = null,
    val mensajes: List<Mensaje> = emptyList(),
    val error: String? = null,
    /** Último traceroute por nodo destino. */
    val trazas: Map<Int, Traza> = emptyMap(),
) {
    fun nombre(num: Int): String = nodos[num]?.user?.long_name?.ifBlank { null } ?: idNodo(num)

    fun nombre(chat: Chat): String =
        when (chat) {
            is Chat.Canal -> canales[chat.indice]?.let(::nombreCanal) ?: "Canal ${chat.indice}"
            is Chat.Directo -> nombre(chat.nodo)
        }
}

/** Nuevo estado de conexión conservando lo que no depende de ella: historial, nodos conocidos y nuestro número. */
fun MeshState.reiniciar(
    estado: Estado,
    error: String? = null,
): MeshState = MeshState(estado = estado, error = error, miNodo = miNodo, nodos = nodos, mensajes = mensajes)

/**
 * Resultado de un traceroute: nodos por los que pasó el paquete (incluye origen y destino) y la señal
 * (SNR en dB) con que se oyó cada salto; null = desconocida.
 */
data class Traza(
    val ida: List<Int>,
    val snrIda: List<Float?>,
    val vuelta: List<Int>,
    val snrVuelta: List<Float?>,
    val hora: Long,
)

// El firmware manda el SNR multiplicado por 4 en un int8; -128 significa "no medido".
private fun snrTraza(v: Int): Float? = if (v == -128) null else v / 4f

/** Arma la traza a partir de la respuesta del destino. Pura. */
fun traza(
    yo: Int,
    destino: Int,
    r: RouteDiscovery,
    ahora: Long,
): Traza =
    Traza(
        ida = listOf(yo) + r.route + destino,
        snrIda = r.snr_towards.map(::snrTraza),
        vuelta = listOf(destino) + r.route_back + yo,
        snrVuelta = r.snr_back.map(::snrTraza),
        hora = ahora,
    )

/** Borra una conversación solo en este teléfono (Meshtastic no tiene borrado remoto). */
fun MeshState.sinChat(chat: Chat): MeshState = copy(mensajes = mensajes.filterNot { it.chat(miNodo) == chat })

/** Borra un mensaje solo en este teléfono. El par (de, id) lo identifica: los ids son por remitente. */
fun MeshState.sinMensaje(m: Mensaje): MeshState = copy(mensajes = mensajes.filterNot { it.de == m.de && it.id == m.id })

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
        fr.config != null ->
            copy(
                lora = fr.config.lora ?: lora,
                cfgPosicion = fr.config.position ?: cfgPosicion,
                cfgDispositivo = fr.config.device ?: cfgDispositivo,
            )
        fr.packet != null -> recibir(fr.packet, ahora)
        fr.config_complete_id != null -> copy(estado = Estado.LISTO)
        else -> this
    }

fun MeshState.conCanal(c: Channel): MeshState = if (c.role == Channel.Role.DISABLED) copy(canales = canales - c.index) else copy(canales = canales + (c.index to c))

// El primario sin nombre usa el nombre del preset (LongFast) en la app oficial.
fun nombreCanal(c: Channel): String = c.settings?.name?.ifBlank { null } ?: if (c.index == 0) "Principal" else "Canal ${c.index}"

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

        PortNum.TRACEROUTE_APP -> {
            // Respuesta a nuestro traceroute: viene del destino con request_id = el id que mandamos.
            val yo = miNodo
            if (d.request_id != 0 && yo != null && p.to == yo) {
                runCatching { RouteDiscovery.ADAPTER.decode(d.payload) }.getOrNull()?.let { r ->
                    st = st.copy(trazas = trazas + (p.from to traza(yo, p.from, r, ahora)))
                }
            }
        }

        PortNum.POSITION_APP -> {
            Position.ADAPTER.decode(d.payload).takeIf { it.latitude_i != null }?.let { nodo = nodo.copy(position = it) }
            // Posición mandada solo a nosotros (botón "Enviar mi ubicación" de un chat directo).
            // En vivo llega cada minuto: un aviso en el chat cada 15 min como máximo.
            val reciente = mensajes.any { it.de == p.from && it.texto == TEXTO_UBICACION_RECIBIDA && ahora - it.hora < AVISO_UBICACION_MS }
            if (p.to == miNodo && p.from != miNodo && !reciente) {
                st = st.copy(mensajes = mensajes + Mensaje(p.id, p.from, p.to, p.channel, TEXTO_UBICACION_RECIBIDA, ahora, Entrega.RECIBIDO))
            }
        }

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

/** Clave estable de una conversación para guardar qué se ha leído. */
fun Chat.clave(): String =
    when (this) {
        is Chat.Canal -> "c$indice"
        is Chat.Directo -> "d$nodo"
    }

/** Inverso de [clave]. */
fun chatDeClave(k: String): Chat? {
    val n = k.drop(1).toIntOrNull() ?: return null
    return when (k.firstOrNull()) {
        'c' -> Chat.Canal(n)
        'd' -> Chat.Directo(n)
        else -> null
    }
}

/** Mensajes recibidos después de la última vez que se abrió cada conversación. [leidos]: clave → hora en ms. */
fun MeshState.noLeidos(leidos: Map<String, Long>): Map<Chat, Int> =
    mensajes
        .filter { it.entrega == Entrega.RECIBIDO }
        .groupBy { it.chat(miNodo) }
        .mapValues { (c, ms) -> ms.count { it.hora > (leidos[c.clave()] ?: 0L) } }
        .filterValues { it > 0 }

/**
 * Nivel de señal 0–4 a partir del SNR de LoRa. LoRa decodifica hasta ~-20 dB con LongFast, así que
 * un SNR negativo todavía es usable (no como en WiFi).
 */
fun nivelSenal(snr: Float): Int =
    when {
        snr >= 5f -> 4
        snr >= 0f -> 3
        snr >= -7f -> 2
        snr >= -15f -> 1
        else -> 0
    }

/** Nivel 0–4 a partir del RSSI de Bluetooth al escanear (qué tan cerca está el nodo del teléfono). */
fun nivelRssi(rssi: Int): Int =
    when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        rssi >= -90 -> 1
        else -> 0
    }

/** ¿El canal público está enviando nuestra ubicación (aproximada) a cualquiera cerca? */
fun MeshState.ubicacionPublica(): Boolean = (canales[0]?.settings?.module_settings?.position_precision ?: 0) != 0
