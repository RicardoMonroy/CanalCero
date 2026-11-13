package io.github.ricardomonroy.canalcero.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ricardomonroy.canalcero.R
import io.github.ricardomonroy.canalcero.malla
import io.github.ricardomonroy.canalcero.mesh.MeshState
import io.github.ricardomonroy.canalcero.mesh.esAproximada
import io.github.ricardomonroy.canalcero.mesh.grados
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

// OpenFreeMap: sin API key, con atribución OSM incluida en el estilo. Ambos comparten teselas e íconos.
const val ESTILO_CLARO = "https://tiles.openfreemap.org/styles/liberty"
const val ESTILO_OSCURO = "https://tiles.openfreemap.org/styles/dark"
val ESTILOS = listOf(ESTILO_CLARO, ESTILO_OSCURO)
private val QUERETARO = LatLng(20.5888, -100.3899)

@Composable
fun Mapa(
    st: MeshState,
    modifier: Modifier = Modifier,
    enfocar: Int? = null,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val vista =
        remember {
            MapLibre.getInstance(ctx)
            // TextureView: con SurfaceView el mapa sale negro en el Pixel 10 (GPU PowerVR) dentro de Compose.
            MapView(ctx, MapLibreMapOptions.createFromAttributes(ctx).textureMode(true)).apply { onCreate(null) }
        }
    var estilo by remember { mutableStateOf<Style?>(null) }
    var centrado by remember { mutableStateOf(false) }

    // MapView necesita los eventos de ciclo de vida de la Activity.
    DisposableEffect(lifecycle) {
        val obs =
            LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> vista.onStart()
                    Lifecycle.Event.ON_RESUME -> vista.onResume()
                    Lifecycle.Event.ON_PAUSE -> vista.onPause()
                    Lifecycle.Event.ON_STOP -> vista.onStop()
                    else -> Unit
                }
            }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            vista.onPause()
            vista.onStop()
            vista.onDestroy()
        }
    }

    val malla = ctx.malla
    val claro = malla.estiloClaro
    // Al cambiar de estilo MapLibre descarta capas y fuentes: se vuelven a crear.
    LaunchedEffect(claro) {
        estilo = null
        vista.getMapAsync { m -> m.setStyle(Style.Builder().fromUri(if (claro) ESTILO_CLARO else ESTILO_OSCURO)) { s -> estilo = prepararEstilo(s, claro) } }
    }

    var pidiendo by remember { mutableStateOf<LatLngBounds?>(null) }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                vista.apply {
                    getMapAsync { m ->
                        // Nodos a pocos metros encuadran a zoom ~22: sin calles en pantalla, el mapa se ve negro.
                        m.setMaxZoomPreference(17.0)
                        m.cameraPosition =
                            org.maplibre.android.camera.CameraPosition
                                .Builder()
                                .target(QUERETARO)
                                .zoom(10.0)
                                .build()
                    }
                }
            },
        )
        // Abajo a la derecha: arriba está la brújula y abajo a la izquierda la atribución.
        Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalAlignment = Alignment.End) {
            ZonasOffline.progreso?.let {
                Text(it, Modifier.background(Color(0xCC1C1F23)).padding(6.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFF4EFE6))
            }
            SmallFloatingActionButton(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { malla.alternarEstilo() }) {
                Icono(if (claro) R.drawable.ic_dark_mode else R.drawable.ic_light_mode, if (claro) "Mapa oscuro" else "Mapa claro", tint = MaterialTheme.colorScheme.onSurface)
            }
            if (ZonasOffline.progreso == null) {
                SmallFloatingActionButton(containerColor = MaterialTheme.colorScheme.primary, onClick = { vista.getMapAsync { m -> pidiendo = m.projection.visibleRegion.latLngBounds } }) {
                    Icono(R.drawable.ic_download, "Descargar esta zona", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        // Arriba a la izquierda: la brújula de MapLibre ocupa la esquina derecha.
        val alternar = alternadorUbicacion(malla)
        FilterChip(
            selected = malla.compartirUbicacion,
            onClick = { alternar(!malla.compartirUbicacion) },
            label = { Text(if (malla.compartirUbicacion) "Compartiendo ubicación" else "Compartir ubicación") },
            leadingIcon = { Icono(R.drawable.ic_location_on, null, Modifier.size(18.dp)) },
            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
    pidiendo?.let { b -> DialogoDescarga(b) { pidiendo = null } }

    // Se redibujan al cambiar de estilo o al terminar una descarga.
    LaunchedEffect(estilo, ZonasOffline.progreso == null) {
        val s = estilo ?: return@LaunchedEffect
        ZonasOffline.listar(ctx) { zonas ->
            val f =
                zonas.mapNotNull { z ->
                    val b = (z.regiones.first().definition as? OfflineTilePyramidRegionDefinition)?.bounds ?: return@mapNotNull null
                    val anillo =
                        listOf(
                            Point.fromLngLat(b.longitudeWest, b.latitudeSouth),
                            Point.fromLngLat(b.longitudeEast, b.latitudeSouth),
                            Point.fromLngLat(b.longitudeEast, b.latitudeNorth),
                            Point.fromLngLat(b.longitudeWest, b.latitudeNorth),
                            Point.fromLngLat(b.longitudeWest, b.latitudeSouth),
                        )
                    Feature.fromGeometry(Polygon.fromLngLats(listOf(anillo))).apply { addStringProperty("nombre", z.nombre) }
                }
            s.getSourceAs<GeoJsonSource>("zonas")?.setGeoJson(FeatureCollection.fromFeatures(f))
        }
    }

    LaunchedEffect(st.nodos, estilo, claro, enfocar) {
        val s = estilo ?: return@LaunchedEffect
        val puntos =
            st.nodos.values.mapNotNull { n ->
                val (lat, lon) = n.position.grados() ?: return@mapNotNull null
                val mio = n.num == st.miNodo
                val nombre = n.user?.short_name?.ifBlank { null } ?: st.nombre(n.num)
                // "≈": el canal recortó la precisión (LongFast público ≈ ±3 km).
                val etiqueta = if (!mio && n.position!!.esAproximada()) "≈ $nombre" else nombre
                LatLng(lat, lon) to
                    Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                        addStringProperty("nombre", etiqueta)
                        addStringProperty("color", if (mio) "#F26B1D" else if (claro) "#1C1F23" else "#F4EFE6")
                    }
            }
        s.getSourceAs<GeoJsonSource>("nodos")?.setGeoJson(FeatureCollection.fromFeatures(puntos.map { it.second }))
        val foco = enfocar?.let { st.nodos[it]?.position.grados() }
        if (foco != null) {
            centrado = true
            vista.getMapAsync { m -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(foco.first, foco.second), 15.0)) }
            return@LaunchedEffect
        }
        if (centrado || puntos.isEmpty()) return@LaunchedEffect
        centrado = true
        vista.getMapAsync { m ->
            val camara =
                if (puntos.size == 1) {
                    CameraUpdateFactory.newLatLngZoom(puntos[0].first, 14.0)
                } else {
                    CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(puntos.map { it.first }).build(), 120)
                }
            m.animateCamera(camara)
        }
    }
}


@Composable
private fun DialogoDescarga(
    b: LatLngBounds,
    cerrar: () -> Unit,
) {
    val ctx = LocalContext.current
    val n = ZonasOffline.estimar(b)
    var nombre by remember { mutableStateOf("") }
    val cabe = n <= ZonasOffline.MAX_TESELAS
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Descargar esta zona") },
        text = {
            Column {
                // ~40 KB por tesela vectorial en promedio: solo para dar una idea.
                Text(
                    if (cabe) {
                        "Se guarda lo que ves en pantalla para usarlo sin internet. Aprox. ${ZonasOffline.mb(n * 40_000)} ($n teselas)."
                    } else {
                        "La zona es muy grande ($n teselas, máximo ${ZonasOffline.MAX_TESELAS}). Acerca el mapa y vuelve a intentar."
                    },
                )
                if (cabe) OutlinedTextField(nombre, { if (it.length <= 30) nombre = it }, label = { Text("Nombre (ej. Sierra Gorda)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ZonasOffline.descargar(ctx.applicationContext, nombre.trim(), b) { error ->
                    Toast.makeText(ctx, error ?: "Zona \"${nombre.trim()}\" lista para usar sin internet", Toast.LENGTH_LONG).show()
                }
                cerrar()
            }, enabled = cabe && nombre.isNotBlank()) { Text("Descargar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

private fun prepararEstilo(
    s: Style,
    claro: Boolean,
): Style {
    val tinta = if (claro) "#1C1F23" else "#F4EFE6"
    val fondo = if (claro) "#FFFFFF" else "#1C1F23"
    // Zonas descargadas debajo de los nodos: relleno tenue, borde punteado y nombre.
    s.addSource(GeoJsonSource("zonas"))
    s.addLayer(FillLayer("zonas-relleno", "zonas").withProperties(fillColor("#F26B1D"), fillOpacity(0.06f)))
    s.addLayer(LineLayer("zonas-borde", "zonas").withProperties(lineColor("#F26B1D"), lineWidth(2f), lineDasharray(arrayOf(2f, 2f))))
    s.addLayer(
        SymbolLayer("zonas-nombre", "zonas").withProperties(
            textField(get("nombre")),
            textFont(arrayOf("Noto Sans Regular")),
            textSize(12f),
            textColor("#F26B1D"),
            textHaloColor(fondo),
            textHaloWidth(1.5f),
        ),
    )
    s.addSource(GeoJsonSource("nodos"))
    s.addLayer(
        CircleLayer("nodos-punto", "nodos").withProperties(
            circleRadius(8f),
            circleColor(toColor(get("color"))),
            circleStrokeWidth(2f),
            circleStrokeColor(fondo),
        ),
    )
    s.addLayer(
        SymbolLayer("nodos-nombre", "nodos").withProperties(
            textField(get("nombre")),
            // La única fuente que sirven ambos estilos; con la default (Open Sans) las etiquetas dan 404.
            textFont(arrayOf("Noto Sans Regular")),
            textSize(13f),
            textOffset(arrayOf(0f, 1.4f)),
            textColor(tinta),
            textHaloColor(fondo),
            textHaloWidth(1.5f),
        ),
    )
    return s
}
