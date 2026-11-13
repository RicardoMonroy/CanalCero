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
