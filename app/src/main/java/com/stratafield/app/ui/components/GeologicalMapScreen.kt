package com.stratafield.app.ui.components

import android.view.MotionEvent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import com.google.gson.JsonParser
import com.stratafield.app.data.model.FormResponse
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.GeoJsonSource
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID

private enum class MapMode { NAVIGATE, DRAW, SELECT }
private enum class FeatureEditAction { NONE, MOVE, RESHAPE }
private val featureKinds = listOf("point", "line", "polygon", "boundary", "fault", "contact", "text")

@Composable
fun GeologicalMapScreen(
    projectId: Long,
    responses: List<FormResponse>,
    savedGeoJson: String,
    layerVisible: Boolean,
    onSaveLayer: (String, Boolean) -> Unit,
    onViewResponse: (FormResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pins = remember(responses) { GeologicalMapData.surveyPins(responses) }
    var features by remember(projectId) { mutableStateOf(GeologicalMapData.fromGeoJson(savedGeoJson)) }
    var visible by remember(projectId) { mutableStateOf(layerVisible) }
    var mode by remember { mutableStateOf(MapMode.NAVIGATE) }
    var kind by remember { mutableStateOf("point") }
    var draft by remember { mutableStateOf(emptyList<GeoPoint>()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedPin by remember { mutableStateOf<SurveyMapPin?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var httpConfigured by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    var mapRendered by remember { mutableStateOf(false) }
    var mapLoadError by remember { mutableStateOf<String?>(null) }
    var mapTimedOut by remember { mutableStateOf(false) }
    var hasInternet by remember { mutableStateOf(hasValidatedInternet(context)) }
    var mapReloadToken by remember { mutableIntStateOf(0) }
    var mapHandle by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    var editorText by remember { mutableStateOf("") }
    var showFeatureEditor by remember { mutableStateOf(false) }
    var editAction by remember { mutableStateOf(FeatureEditAction.NONE) }
    var cameraInitialized by remember { mutableStateOf(false) }
    val undo = remember { mutableStateListOf<String>() }
    val redo = remember { mutableStateListOf<String>() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(GeologicalMapData.toGeoJson(features)) } }
    }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val mainHandler = Handler(Looper.getMainLooper())
        fun refreshConnection(available: Boolean = hasValidatedInternet(context)) {
            mainHandler.post {
                if (hasInternet != available) {
                    hasInternet = available
                    if (available) {
                        mapReady = false
                        mapRendered = false
                        mapLoadError = null
                        cameraInitialized = false
                        mapReloadToken++
                    }
                }
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshConnection()
            override fun onLost(network: Network) = refreshConnection()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                refreshConnection(
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
        }
        runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { connectivityManager?.unregisterNetworkCallback(callback) } }
    }

    LaunchedEffect(hasInternet, mapReady, mapRendered, mapReloadToken) {
        mapTimedOut = false
        if (hasInternet && mapReady && !mapRendered) {
            delay(15_000)
            if (!mapRendered) mapTimedOut = true
        }
    }

    fun checkpoint() { undo.add(GeologicalMapData.toGeoJson(features)); if (undo.size > 40) undo.removeAt(0); redo.clear() }
    fun save() = onSaveLayer(GeologicalMapData.toGeoJson(features), visible)
    fun updateSource() {
        val json = GeologicalMapData.toGeoJson(features)
        mapHandle?.getStyle { style ->
            (style.getSourceAs<GeoJsonSource>("geology") as? GeoJsonSource)?.setGeoJson(json)
        }
    }
    fun hitTest(point: GeoPoint): GeoFeature? {
        val map = mapHandle ?: return null
        val tapped = map.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
        return features.minByOrNull { feature ->
            feature.points.minOf { vertex ->
                val screen = map.projection.toScreenLocation(LatLng(vertex.latitude, vertex.longitude))
                kotlin.math.hypot((screen.x - tapped.x).toDouble(), (screen.y - tapped.y).toDouble())
            }
        }?.takeIf { feature ->
            feature.points.minOf { vertex ->
                val screen = map.projection.toScreenLocation(LatLng(vertex.latitude, vertex.longitude))
                kotlin.math.hypot((screen.x - tapped.x).toDouble(), (screen.y - tapped.y).toDouble())
            } < 36.0
        }
    }

    LaunchedEffect(savedGeoJson) {
        val latest = GeologicalMapData.fromGeoJson(savedGeoJson)
        if (GeologicalMapData.toGeoJson(latest) != GeologicalMapData.toGeoJson(features)) features = latest
    }
    LaunchedEffect(layerVisible) { visible = layerVisible }
    LaunchedEffect(features, mapReady) { if (mapReady) updateSource() }
    LaunchedEffect(pins, mapReady) {
        if (mapReady) mapHandle?.getStyle { it.getSourceAs<GeoJsonSource>("form-pins")?.setGeoJson(pinsGeoJson(pins)) }
    }
    LaunchedEffect(visible, mapReady) {
        mapHandle?.getStyle { style ->
            val layerVisibility = if (visible) Property.VISIBLE else Property.NONE
            listOf("geology-fill", "geology-lines", "geology-points", "geology-labels").forEach { style.getLayer(it)?.setProperties(visibility(layerVisibility)) }
        }
    }

    Column(modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            items(MapMode.entries) { item ->
                FilterChip(selected = mode == item, onClick = {
                    mode = item; draft = emptyList(); selectedPin = null
                    mapView?.isEnabled = true
                }, label = { Text(item.name.lowercase().replaceFirstChar(Char::uppercase)) })
            }
            item { VerticalDivider(modifier = Modifier.height(28.dp)) }
            if (mode == MapMode.DRAW) item {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { expanded = true }, label = { Text(kind.replaceFirstChar(Char::uppercase)) }, leadingIcon = { Icon(Icons.Default.Category, null) })
                    DropdownMenu(expanded, { expanded = false }) { featureKinds.forEach { k -> DropdownMenuItem(text = { Text(k.replaceFirstChar(Char::uppercase)) }, onClick = { kind = k; expanded = false }) } }
                }
            }
            item { IconButton(onClick = { if (undo.isNotEmpty()) { redo.add(GeologicalMapData.toGeoJson(features)); features = GeologicalMapData.fromGeoJson(undo.removeAt(undo.lastIndex)); save() } }, enabled = undo.isNotEmpty()) { Icon(Icons.Default.Undo, "Undo") } }
            item { IconButton(onClick = { if (redo.isNotEmpty()) { undo.add(GeologicalMapData.toGeoJson(features)); features = GeologicalMapData.fromGeoJson(redo.removeAt(redo.lastIndex)); save() } }, enabled = redo.isNotEmpty()) { Icon(Icons.Default.Redo, "Redo") } }
            item { IconButton(onClick = { visible = !visible; save() }) { Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Toggle geological layer") } }
            item { IconButton(onClick = { exportLauncher.launch("project-$projectId-map.geojson") }) { Icon(Icons.Default.FileDownload, "Export GeoJSON") } }
            if (mode == MapMode.DRAW && draft.isNotEmpty()) item {
                TextButton(onClick = {
                    if (draft.size >= if (kind == "point" || kind == "text") 1 else if (kind == "line") 2 else 3) {
                        checkpoint()
                        features = features + GeoFeature(UUID.randomUUID().toString(), kind, editorText.ifBlank { kind.replaceFirstChar(Char::uppercase) }, "#D84315", draft)
                        draft = emptyList(); editorText = ""; save()
                    }
                }) { Text("Finish") }
            }
            if (mode == MapMode.DRAW && (kind == "text" || draft.isNotEmpty())) item {
                IconButton(onClick = { showFeatureEditor = true }) { Icon(Icons.Default.Edit, "Set feature name") }
            }
            if (mode == MapMode.SELECT && selectedId != null) item {
                FilterChip(selected = editAction == FeatureEditAction.MOVE, onClick = { editAction = if (editAction == FeatureEditAction.MOVE) FeatureEditAction.NONE else FeatureEditAction.MOVE }, label = { Text("Move") })
                FilterChip(selected = editAction == FeatureEditAction.RESHAPE, onClick = { editAction = if (editAction == FeatureEditAction.RESHAPE) FeatureEditAction.NONE else FeatureEditAction.RESHAPE }, label = { Text("Reshape") })
                IconButton(onClick = { features.firstOrNull { it.id == selectedId }?.let { editorText = it.name; showFeatureEditor = true } }) { Icon(Icons.Default.Edit, "Edit selected feature") }
                IconButton(onClick = {
                    checkpoint()
                    features = features.map { if (it.id == selectedId) it.copy(color = if (it.color == "#D84315") "#1565C0" else "#D84315") else it }
                    save()
                }) { Icon(Icons.Default.Palette, "Change feature color") }
                IconButton(onClick = { checkpoint(); features = features.filterNot { it.id == selectedId }; selectedId = null; save() }) { Icon(Icons.Default.Delete, "Delete selected feature") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(mapReloadToken) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    if (!httpConfigured) {
                        val cache = Cache(File(ctx.cacheDir, "osm-tile-cache"), 64L * 1024L * 1024L)
                        val client = OkHttpClient.Builder().cache(cache).addInterceptor { chain ->
                            chain.proceed(chain.request().newBuilder().header("User-Agent", "StrataField/1.0.0 (Android geological field mapping)").build())
                        }.build()
                        HttpRequestUtil.setOkHttpClient(client)
                        httpConfigured = true
                    }
                    MapLibre.getInstance(ctx)
                    MapView(ctx).also { view ->
                        mapView = view
                        mapRendered = false
                        mapLoadError = null
                        view.addOnDidFinishRenderingMapListener { fully ->
                            if (fully) mapRendered = true
                        }
                        view.addOnDidFailLoadingMapListener { error ->
                            mapReady = false
                            mapLoadError = error
                        }
                        view.onCreate(null)
                        view.onStart()
                        view.onResume()
                        view.getMapAsync { map ->
                            mapHandle = map
                            map.setStyle(Style.Builder().fromUri(OPENFREEMAP_STYLE_URL)) { style ->
                                style.addSource(GeoJsonSource("geology", GeologicalMapData.toGeoJson(features)))
                                style.addSource(GeoJsonSource("form-pins", pinsGeoJson(pins)))
                                style.addLayer(FillLayer("geology-fill", "geology").withProperties(fillColor(org.maplibre.android.style.expressions.Expression.get("color")), fillOpacity(0f)))
                                style.addLayer(LineLayer("geology-lines", "geology").withProperties(lineColor(org.maplibre.android.style.expressions.Expression.get("color")), lineWidth(3.0f)))
                                style.addLayer(CircleLayer("geology-points", "geology").withProperties(circleRadius(7f), circleColor(org.maplibre.android.style.expressions.Expression.get("color")), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                                style.addLayer(SymbolLayer("geology-labels", "geology").withProperties(textField(org.maplibre.android.style.expressions.Expression.get("name")), textSize(10f), textColor("#263238"), textHaloColor("#FFFFFF"), textHaloWidth(1.5f), textOffset(arrayOf(0f, 1.1f))))
                                style.addLayer(CircleLayer("form-pin-circles", "form-pins").withProperties(circleRadius(8f), circleColor("#1565C0"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                                style.addLayer(SymbolLayer("form-pin-labels", "form-pins").withProperties(textField(org.maplibre.android.style.expressions.Expression.get("name")), textSize(11f), textColor("#10243A"), textHaloColor("#FFFFFF"), textHaloWidth(1.5f), textOffset(arrayOf(0f, 1.2f))))
                                map.uiSettings.isRotateGesturesEnabled = true
                                map.addOnMapClickListener { latLng ->
                                    val tapped = GeoPoint(latLng.longitude, latLng.latitude)
                                    val pin = pins.minByOrNull { distanceMeters(it.point, tapped) }?.takeIf { distanceMeters(it.point, tapped) < 40.0 }
                                    if (pin != null && mode != MapMode.DRAW) {
                                        selectedPin = pin
                                    } else when (mode) {
                                        MapMode.NAVIGATE -> { selectedPin = null }
                                        MapMode.DRAW -> {
                                            selectedPin = null
                                            draft = if (kind == "point" || kind == "text") listOf(tapped) else draft + tapped
                                            if (kind == "point") {
                                                checkpoint()
                                                features = features + GeoFeature(UUID.randomUUID().toString(), kind, "Point", "#D84315", draft)
                                                draft = emptyList(); save()
                                            }
                                        }
                                        MapMode.SELECT -> {
                                            selectedPin = null
                                            val selected = features.firstOrNull { it.id == selectedId }
                                            if (selected != null && editAction != FeatureEditAction.NONE) {
                                                checkpoint()
                                                val changed = if (editAction == FeatureEditAction.MOVE) {
                                                    val dLat = tapped.latitude - selected.points.map { it.latitude }.average()
                                                    val dLon = tapped.longitude - selected.points.map { it.longitude }.average()
                                                    selected.copy(points = selected.points.map { GeoPoint(it.longitude + dLon, it.latitude + dLat) })
                                                } else {
                                                    val point = map.projection.toScreenLocation(latLng)
                                                    val nearest = selected.points.indices.minByOrNull { i ->
                                                        val p = selected.points[i]
                                                        val screen = map.projection.toScreenLocation(LatLng(p.latitude, p.longitude))
                                                        kotlin.math.hypot((screen.x - point.x).toDouble(), (screen.y - point.y).toDouble())
                                                    } ?: 0
                                                    selected.copy(points = selected.points.mapIndexed { i, p -> if (i == nearest) tapped else p })
                                                }
                                                features = features.map { if (it.id == selected.id) changed else it }
                                                save()
                                            } else selectedId = hitTest(tapped)?.id
                                        }
                                    }
                                    true
                                }
                                view.setOnHoverListener { _, event ->
                                    if (event.action == MotionEvent.ACTION_HOVER_MOVE && pins.isNotEmpty()) {
                                        selectedPin = pins.firstOrNull { pin ->
                                            val p = map.projection.toScreenLocation(LatLng(pin.point.latitude, pin.point.longitude))
                                            kotlin.math.hypot((p.x - event.x).toDouble(), (p.y - event.y).toDouble()) < 24.0
                                        }
                                    } else if (event.action == MotionEvent.ACTION_HOVER_EXIT) selectedPin = null
                                    false
                                }
                                mapReady = true
                                mapLoadError = null
                                if (!cameraInitialized) {
                                    val initialTarget = pins.firstOrNull()?.point?.let { LatLng(it.latitude, it.longitude) }
                                        ?: LatLng(20.0, 0.0)
                                    val initialZoom = if (pins.isEmpty()) 1.5 else 13.0
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(initialTarget)
                                        .zoom(initialZoom)
                                        .build()
                                    cameraInitialized = true
                                }
                            }
                        }
                    }
                },
                update = {
                    mapHandle?.let { map ->
                        val gestures = mode == MapMode.NAVIGATE
                        map.uiSettings.isScrollGesturesEnabled = gestures
                        map.uiSettings.isZoomGesturesEnabled = gestures
                        map.uiSettings.isRotateGesturesEnabled = gestures
                    }
                }
            )
            }
            val statusMessage = when {
                !hasInternet -> "No internet connection. Connect and retry to load the online map."
                mapLoadError != null -> "The map could not be loaded. Check your connection and retry."
                mapTimedOut -> "Map tiles are taking too long to load. Check your connection and retry."
                !mapRendered -> "Loading map…"
                else -> null
            }
            if (statusMessage != null) {
                Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(.92f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(statusMessage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        if (!mapRendered) {
                            TextButton(onClick = {
                                mapReady = false
                                mapRendered = false
                                mapTimedOut = false
                                mapLoadError = null
                                cameraInitialized = false
                                mapReloadToken++
                            }, enabled = hasInternet) { Text("Retry") }
                        }
                    }
                }
            }
            // Independent pins are rendered from saved forms and never written into the editable layer.
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                Text("\u00a9 OpenFreeMap · \u00a9 OpenMapTiles", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                Text("Map tiles: OpenFreeMap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
            selectedPin?.let { pin ->
                val response = responses.firstOrNull { it.id == pin.responseId }
                Card(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(.9f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(pin.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(pin.notes.ifBlank { "No general notes" }, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { response?.let(onViewResponse) }, enabled = response != null) { Text("Details") }
                            TextButton(onClick = { selectedPin = null }) { Text("Close") }
                        }
                    }
                }
            }
            if (mode == MapMode.DRAW && draft.isNotEmpty()) {
                Text("${draft.size} point${if (draft.size == 1) "" else "s"} • tap Finish to save", Modifier.align(Alignment.TopCenter).padding(10.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    DisposableEffect(mapView) {
        onDispose { mapView?.onPause(); mapView?.onStop(); mapView?.onDestroy(); mapView = null }
    }
    if (showFeatureEditor) {
        AlertDialog(onDismissRequest = { showFeatureEditor = false }, title = { Text("Feature name") },
            text = { OutlinedTextField(editorText, { editorText = it }, singleLine = true, label = { Text("Name or annotation") }) },
            confirmButton = { TextButton(onClick = {
                if (draft.isNotEmpty() && mode == MapMode.DRAW) { /* name is applied on Finish */ }
                else { checkpoint(); features = features.map { if (it.id == selectedId) it.copy(name = editorText) else it }; save() }
                showFeatureEditor = false
            }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showFeatureEditor = false }) { Text("Cancel") } })
    }
}

private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(a.latitude - b.latitude); val dLon = Math.toRadians(a.longitude - b.longitude)
    val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) + kotlin.math.cos(Math.toRadians(a.latitude)) * kotlin.math.cos(Math.toRadians(b.latitude)) * kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return 12_742_000.0 * kotlin.math.asin(kotlin.math.sqrt(h))
}

private const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

private fun hasValidatedInternet(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun pinsGeoJson(pins: List<SurveyMapPin>): String = com.google.gson.Gson().toJson(com.google.gson.JsonObject().apply {
    addProperty("type", "FeatureCollection")
    add("features", com.google.gson.JsonArray().also { array -> pins.forEach { pin ->
        array.add(com.google.gson.JsonObject().apply {
            addProperty("type", "Feature"); addProperty("id", "form-${pin.responseId}")
            add("properties", com.google.gson.JsonObject().apply { addProperty("name", pin.name); addProperty("responseId", pin.responseId) })
            add("geometry", com.google.gson.JsonObject().apply {
                addProperty("type", "Point"); add("coordinates", com.google.gson.JsonArray().also { it.add(pin.point.longitude); it.add(pin.point.latitude) })
            })
        })
    } })
})
