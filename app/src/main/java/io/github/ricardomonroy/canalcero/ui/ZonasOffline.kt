package io.github.ricardomonroy.canalcero.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ricardomonroy.canalcero.mesh.teselas
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** Una zona descargada; [regiones] tiene una por estilo (claro y oscuro). */
data class Zona(
    val regiones: List<OfflineRegion>,
    val nombre: String,
    val bytes: Long,
    val completa: Boolean,
)

/**
 * Mapas sin internet: MapLibre guarda las teselas de una zona en su base local y las usa solo cuando no hay red.
 * OpenFreeMap sirve teselas vectoriales hasta z14; más cerca se dibujan ampliando las de z14.
 */
object ZonasOffline {
    const val Z_MIN = 5
    const val Z_MAX = 14

    // Límite propio para no bajar medio país por error (~1 GB en ciudad).
    const val MAX_TESELAS = 20_000L

    var progreso by mutableStateOf<String?>(null)
        private set

    private fun gestor(ctx: Context): OfflineManager {
        MapLibre.getInstance(ctx)
        return OfflineManager.getInstance(ctx).also { it.setOfflineMapboxTileCountLimit(MAX_TESELAS) }
    }

    fun estimar(b: LatLngBounds): Long = teselas(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast, Z_MIN, Z_MAX)

    /** Descarga la zona para cada estilo, uno tras otro (las teselas se comparten: el segundo es casi gratis). */
    fun descargar(
        ctx: Context,
        nombre: String,
        b: LatLngBounds,
        estilos: List<String> = ESTILOS,
        fin: (String?) -> Unit,
    ) {
        if (estilos.isEmpty()) return fin(null)
        descargarUno(ctx, nombre, b, estilos.first(), parte = "${ESTILOS.size - estilos.size + 1}/${ESTILOS.size}") { error ->
            if (error != null) fin(error) else descargar(ctx, nombre, b, estilos.drop(1), fin)
        }
    }

    private fun descargarUno(
        ctx: Context,
        nombre: String,
        b: LatLngBounds,
        estilo: String,
        parte: String,
        fin: (String?) -> Unit,
    ) {
        val def = OfflineTilePyramidRegionDefinition(estilo, b, Z_MIN.toDouble(), Z_MAX.toDouble(), ctx.resources.displayMetrics.density)
        progreso = "Preparando \"$nombre\" ($parte)…"
        gestor(ctx).createOfflineRegion(
            def,
            nombre.encodeToByteArray(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    region.setObserver(
                        object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(s: OfflineRegionStatus) {
                                if (s.isComplete) {
                                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    progreso = null
                                    fin(null)
                                } else if (s.requiredResourceCount > 0) {
                                    progreso = "Descargando \"$nombre\" ($parte): ${100 * s.completedResourceCount / s.requiredResourceCount} % · ${mb(s.completedResourceSize)}"
                                }
                            }

                            // Errores de red sueltos se reintentan solos; solo se informa.
                            override fun onError(e: OfflineRegionError) {
                                progreso = "Descargando \"$nombre\" ($parte): reintentando (${e.reason})"
                            }

                            override fun mapboxTileCountLimitExceeded(limite: Long) {
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                progreso = null
                                fin("La zona es muy grande (más de $limite teselas). Acerca el mapa.")
                            }
                        },
                    )
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    progreso = null
                    fin(error)
                }
            },
        )
    }

    fun listar(
        ctx: Context,
        listo: (List<Zona>) -> Unit,
    ) = gestor(ctx).listOfflineRegions(
        object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regiones: Array<OfflineRegion>?) {
                val todas = regiones.orEmpty().toList()
                if (todas.isEmpty()) return listo(emptyList())
                val partes = mutableListOf<Zona>()
                todas.forEach { r ->
                    r.getStatus(
                        object : OfflineRegion.OfflineRegionStatusCallback {
                            override fun onStatus(s: OfflineRegionStatus?) = agregar(Zona(listOf(r), r.metadata.decodeToString(), s?.completedResourceSize ?: 0, s?.isComplete == true))

                            override fun onError(error: String?) = agregar(Zona(listOf(r), r.metadata.decodeToString(), 0, false))

                            fun agregar(z: Zona) {
                                partes += z
                                if (partes.size < todas.size) return
                                // El tamaño sumado sobreestima un poco: las teselas compartidas cuentan en ambas.
                                val zonas =
                                    partes.groupBy { it.nombre }.map { (nombre, ps) ->
                                        Zona(ps.flatMap { it.regiones }, nombre, ps.sumOf { it.bytes }, ps.size == ESTILOS.size && ps.all { it.completa })
                                    }
                                listo(zonas.sortedBy { it.nombre })
                            }
                        },
                    )
                }
            }

            override fun onError(error: String) = listo(emptyList())
        },
    )

    fun borrar(
        z: Zona,
        listo: () -> Unit,
    ) {
        var faltan = z.regiones.size
        z.regiones.forEach {
            it.delete(
                object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() = uno()

                    override fun onError(error: String) = uno()

                    fun uno() {
                        if (--faltan == 0) listo()
                    }
                },
            )
        }
    }

    fun mb(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)
}
