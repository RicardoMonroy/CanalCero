package io.github.ricardomonroy.canalcero.mesh

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Mínimo entre búsquedas de nodos: cada una hace contestar a todos los nodos cercanos por radio. */
const val ESPERA_BUSQUEDA_SEG = 5 * 60L

/** Segundos que faltan para poder buscar otra vez (0 = ya se puede). Tiempos en ms. */
fun esperaRestante(
    ultima: Long,
    ahora: Long,
): Long = if (ultima == 0L) 0 else (ESPERA_BUSQUEDA_SEG - (ahora - ultima) / 1000).coerceAtLeast(0)

/** "Cerca": oídos en la última hora. */
const val CERCA_SEG = 3600L

/** Nodos oídos en este lapso aparecen en la pestaña Ruta. */
const val RECIENTE_SEG = 2 * 3600L

/** Sin noticias de un compañero en este lapso = alerta (con el perfil Rodada transmite cada minuto). */
const val SIN_SENAL_SEG = 10 * 60L

data class Companero(
    val num: Int,
    val nombre: String,
    val distancia: Double?,
    val rumbo: Double?,
    val haceSeg: Long?,
    val aproximada: Boolean,
)

enum class Alerta { LEJOS, SIN_SENAL }

/** Nodos oídos recientemente, del más cercano al más lejano (sin posición al final). [ahoraSeg] en segundos. */
fun companeros(
    st: MeshState,
    ahoraSeg: Long,
): List<Companero> {
    val yo = st.miNodo?.let { st.nodos[it]?.position.grados() }
    return st.nodos.values
        .filter { it.num != st.miNodo && it.last_heard > 0 && ahoraSeg - it.last_heard <= RECIENTE_SEG }
        .map { n ->
            val donde = n.position.grados()
            Companero(
                num = n.num,
                nombre = st.nombre(n.num),
                distancia = if (yo != null && donde != null) distancia(yo, donde) else null,
                rumbo = if (yo != null && donde != null) rumbo(yo, donde) else null,
                haceSeg = ahoraSeg - n.last_heard,
                aproximada = n.position?.esAproximada() ?: false,
            )
        }.sortedWith(compareBy(nullsLast()) { it.distancia })
}

/** Alertas activas solo para los nodos del grupo. Sin señal tiene prioridad: su distancia ya no es confiable. */
fun alertas(
    lista: List<Companero>,
    grupo: Set<Int>,
    umbralM: Double,
): Map<Int, Alerta> =
    lista
        .filter { it.num in grupo }
        .mapNotNull { c ->
            when {
                (c.haceSeg ?: 0) > SIN_SENAL_SEG -> c.num to Alerta.SIN_SENAL
                (c.distancia ?: 0.0) > umbralM -> c.num to Alerta.LEJOS
                else -> null
            }
        }.toMap()

/** Teselas (esquema XYZ) que cubren el rectángulo entre [zMin] y [zMax], para estimar una descarga. */
fun teselas(
    sur: Double,
    oeste: Double,
    norte: Double,
    este: Double,
    zMin: Int,
    zMax: Int,
): Long =
    (zMin..zMax).sumOf { z ->
        val n = 1L shl z
        fun x(lon: Double) = floor((lon + 180) / 360 * n).toLong().coerceIn(0, n - 1)

        fun y(lat: Double): Long {
            val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toLong().coerceIn(0, n - 1)
        }
        (x(este) - x(oeste) + 1) * (y(sur) - y(norte) + 1)
    }

// --- Ubicación en vivo ---

/** Cada cuánto se reenvía la ubicación en vivo a cada destino. */
const val INTERVALO_EN_VIVO_MS = 60_000L

/** Quita las sesiones en vivo vencidas. [sesiones]: clave de Chat → hasta (ms). */
fun vigentes(
    sesiones: Map<String, Long>,
    ahora: Long,
): Map<String, Long> = sesiones.filterValues { it > ahora }

/** ¿Toca reenviar a este destino? [ultimo] = último envío (ms) o null si nunca. */
fun tocaEnviar(
    ultimo: Long?,
    ahora: Long,
): Boolean = ultimo == null || ahora - ultimo >= INTERVALO_EN_VIVO_MS

// --- Prueba de alcance ---

/** Un paquete recibido durante la prueba: con qué señal llegó, por cuántos saltos y desde qué distancia. */
data class Muestra(
    val hora: Long,
    val nodo: Int,
    val nombre: String,
    val rssi: Int,
    val snr: Float,
    val saltos: Int?,
    val distancia: Double?,
    val aproximada: Boolean,
    /** Distancia confiable: ambas posiciones exactas y de los últimos [POSICION_RECIENTE_SEG]. */
    val fiable: Boolean = false,
)

/** Una posición más vieja que esto ya no sirve para medir alcance (el nodo pudo moverse). */
const val POSICION_RECIENTE_SEG = 15 * 60L

private fun org.meshtastic.proto.Position?.reciente(ahoraSeg: Long): Boolean = this != null && time > 0 && ahoraSeg - time <= POSICION_RECIENTE_SEG

fun muestra(
    st: MeshState,
    p: org.meshtastic.proto.MeshPacket,
    ahora: Long,
): Muestra {
    val miPos = st.miNodo?.let { st.nodos[it]?.position }
    val yo = miPos.grados()
    val otro = st.nodos[p.from]?.position
    val donde = otro.grados()
    val seg = ahora / 1000
    return Muestra(
        hora = ahora,
        nodo = p.from,
        nombre = st.nombre(p.from),
        rssi = p.rx_rssi,
        snr = p.rx_snr,
        // hop_start = saltos permitidos al salir; hop_limit = los que le quedan al llegar. Firmware viejo no manda hop_start.
        saltos = if (p.hop_start > 0) p.hop_start - p.hop_limit else null,
        distancia = if (yo != null && donde != null) distancia(yo, donde) else null,
        aproximada = otro?.esAproximada() ?: false,
        fiable = yo != null && donde != null && otro?.esAproximada() == false && miPos.reciente(seg) && otro.reciente(seg),
    )
}

/** Mayor distancia confiable (posición exacta) a la que llegó un paquete sin repetidor. */
fun alcanceDirecto(ms: List<Muestra>): Double? = ms.filter { it.saltos == 0 && it.fiable }.mapNotNull { it.distancia }.maxOrNull()

/** Récord de alcance directo entre nuestro nodo y otro: la mayor distancia lineal a la que llegó un paquete sin repetidor. */
data class Record(
    val metros: Double,
    val hora: Long,
)

/** Clave por par de nodos: el récord depende de los dos equipos (y sus antenas). */
fun claveRecord(
    mio: Int,
    otro: Int,
): String = "${idNodo(mio)}:${idNodo(otro)}"

/** Actualiza los récords con una muestra; solo cuentan paquetes directos con distancia fiable. */
fun conRecord(
    records: Map<String, Record>,
    mio: Int?,
    m: Muestra,
): Map<String, Record> {
    if (mio == null || m.saltos != 0 || !m.fiable) return records
    val d = m.distancia ?: return records
    val k = claveRecord(mio, m.nodo)
    val previo = records[k]
    return if (previo == null || d > previo.metros) records + (k to Record(d, m.hora)) else records
}

fun csvAlcance(ms: List<Muestra>): String =
    buildString {
        appendLine("hora_ms,nodo,nombre,rssi_dbm,snr_db,saltos,distancia_m,aproximada")
        ms.forEach {
            // Nombres con comas o comillas: se encierran en comillas dobles (RFC 4180).
            val nombre = "\"" + it.nombre.replace("\"", "\"\"") + "\""
            appendLine(listOf(it.hora, idNodo(it.nodo), nombre, it.rssi, it.snr, it.saltos ?: "", it.distancia?.toInt() ?: "", it.aproximada).joinToString(","))
        }
    }
