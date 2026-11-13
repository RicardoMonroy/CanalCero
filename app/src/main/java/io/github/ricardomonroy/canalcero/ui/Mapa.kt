package io.github.ricardomonroy.canalcero.ui

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
import org.maplibre.android.style.layers.CircleLayer
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

// OpenFreeMap: sin API key, con atribución OSM incluida en el estilo.
private const val ESTILO = "https://tiles.openfreemap.org/styles/dark"
private val QUERETARO = LatLng(20.5888, -100.3899)

@Composable
fun Mapa(
    st: MeshState,
    modifier: Modifier = Modifier,
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

    AndroidView(
        modifier = modifier,
        factory = {
            vista.apply {
                getMapAsync { m ->
                    m.cameraPosition =
                        org.maplibre.android.camera.CameraPosition
                            .Builder()
                            .target(QUERETARO)
                            .zoom(10.0)
                            .build()
                    m.setStyle(Style.Builder().fromUri(ESTILO)) { s ->
                        s.addSource(GeoJsonSource("nodos"))
                        s.addLayer(
                            CircleLayer("nodos-punto", "nodos").withProperties(
                                circleRadius(8f),
                                circleColor(toColor(get("color"))),
                                circleStrokeWidth(2f),
                                circleStrokeColor("#1C1F23"),
                            ),
                        )
                        s.addLayer(
                            SymbolLayer("nodos-nombre", "nodos").withProperties(
                                textField(get("nombre")),
                                // La única fuente que sirve el estilo; con la default (Open Sans) las etiquetas dan 404.
                                textFont(arrayOf("Noto Sans Regular")),
                                textSize(13f),
                                textOffset(arrayOf(0f, 1.4f)),
                                textColor("#F4EFE6"),
                                textHaloColor("#1C1F23"),
                                textHaloWidth(1.5f),
                            ),
                        )
                        estilo = s
                    }
                }
            }
        },
    )

    LaunchedEffect(st.nodos, estilo) {
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
                        addStringProperty("color", if (mio) "#F26B1D" else "#F4EFE6")
                    }
            }
        s.getSourceAs<GeoJsonSource>("nodos")?.setGeoJson(FeatureCollection.fromFeatures(puntos.map { it.second }))
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
