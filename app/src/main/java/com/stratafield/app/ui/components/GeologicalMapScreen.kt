package com.stratafield.app.ui.components

import android.view.MotionEvent
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import com.stratafield.app.data.model.FormResponse
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
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
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID

private enum class MapMode { NAVIGATE, DRAW, SELECT, DELETE }
private enum class FeatureEditAction { NONE, MOVE, RESHAPE }
private enum class MapFileFormat(val preferenceSuffix: String, val mimeType: String, val extension: String) {
    GEOJSON("geojson", "application/geo+json", "geojson"),
    KML("kml", "application/vnd.google-earth.kml+xml", "kml")
}
private val featureKinds = listOf("point", "line", "polygon", "boundary", "fault", "contact", "text")
private const val SATELLITE_STYLE = """{"version":8,"sources":{"satellite":{"type":"raster","tiles":["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"maxzoom":19,"attribution":"Source: Esri, Vantor, Earthstar Geographics, and the GIS User Community"}},"layers":[{"id":"satellite-imagery","type":"raster","source":"satellite"}]}"""

@Composable
fun GeologicalMapScreen(
    projectId: Long,
    responses: List<FormResponse>,
    savedGeoJson: String,
    layerVisible: Boolean,
    onSaveLayer: (String, Boolean) -> Unit,
    onViewResponse: (FormResponse) -> Unit,
    onDeleteResponse: (FormResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pins = remember(responses) { GeologicalMapData.surveyPins(responses) }
    var features by remember(projectId) { mutableStateOf(GeologicalMapData.fromGeoJson(savedGeoJson)) }
    var visible by remember(projectId) { mutableStateOf(layerVisible) }
    var mode by remember { mutableStateOf(MapMode.NAVIGATE) }
    var kind by remember { mutableStateOf("point") }
    var draft by remember { mutableStateOf(emptyList<GeoPoint>()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var featurePendingDelete by remember { mutableStateOf<GeoFeature?>(null) }
    var selectedPin by remember { mutableStateOf<SurveyMapPin?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var httpConfigured by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    var firstFrameRendered by remember { mutableStateOf(false) }
    var mapLoadError by remember { mutableStateOf<String?>(null) }
    var mapTimedOut by remember { mutableStateOf(false) }
    val internetState = remember { mutableStateOf(hasValidatedInternet(context)) }
    var hasInternet by internetState
    var mapReloadToken by remember { mutableIntStateOf(0) }
    var mapHandle by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    var editorText by remember { mutableStateOf("") }
    var showFeatureEditor by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var pickerHue by remember { mutableFloatStateOf(0f) }
    var pickerSaturation by remember { mutableFloatStateOf(1f) }
    var pickerBrightness by remember { mutableFloatStateOf(1f) }
    var editAction by remember { mutableStateOf(FeatureEditAction.NONE) }
    var cameraInitialized by remember { mutableStateOf(false) }
    var userCameraCentered by remember { mutableStateOf(false) }
    var userHasMovedMap by remember { mutableStateOf(false) }
    val undo = remember { mutableStateListOf<String>() }
    val redo = remember { mutableStateListOf<String>() }
    val mapFilePreferences = remember(context) {
        context.getSharedPreferences("geological_map_files", android.content.Context.MODE_PRIVATE)
    }
    var mapFileFormat by remember(projectId) {
        mutableStateOf(runCatching {
            MapFileFormat.valueOf(mapFilePreferences.getString("$projectId.selectedFormat", "GEOJSON") ?: "GEOJSON")
        }.getOrDefault(MapFileFormat.GEOJSON))
    }
    var saveFormatMenuExpanded by remember(projectId) { mutableStateOf(false) }
    var savingMapFile by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> locationPermissionGranted = grants.values.any { it } }
    LaunchedEffect(Unit) {
        val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    DisposableEffect(context, locationPermissionGranted) {
        val locationManager = context.getSystemService(LocationManager::class.java)
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val listener = LocationListener { userLocation = it }
        if (fineGranted || coarseGranted) {
            runCatching {
                val providers = locationManager?.getProviders(true).orEmpty().filter { provider ->
                    provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER
                }
                providers.forEach { provider ->
                    locationManager?.getLastKnownLocation(provider)?.let { cached ->
                        if (userLocation == null || cached.time > (userLocation?.time ?: 0L)) userLocation = cached
                    }
                    locationManager?.requestLocationUpdates(provider, 2_000L, 2f, listener, Looper.getMainLooper())
                }
            }
        }
        onDispose { runCatching { locationManager?.removeUpdates(listener) } }
    }

    fun writeMapFile(uri: Uri, content: String, format: MapFileFormat, saveAsProjectDestination: Boolean) {
        scope.launch {
            savingMapFile = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use { it.write(content) }
                        ?: error("The selected file could not be opened for writing.")
                }
            }
            savingMapFile = false
            val destinationKey = "$projectId.${format.preferenceSuffix}"
            if (result.isSuccess) {
                if (saveAsProjectDestination) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    mapFilePreferences.edit()
                        .putString(destinationKey, uri.toString())
                        .putString("$projectId.selectedFormat", format.name)
                        .remove(projectId.toString())
                        .apply()
                    mapFileFormat = format
                }
                Toast.makeText(context, "Map saved to ${uri.lastPathSegment ?: "file"}", Toast.LENGTH_SHORT).show()
            } else {
                if (!saveAsProjectDestination) {
                    mapFilePreferences.edit().remove(destinationKey).apply()
                }
                Toast.makeText(
                    context,
                    if (saveAsProjectDestination) "Could not save the map file" else "Could not update the saved file. Tap Save to choose a new location.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    fun mapFileContent(format: MapFileFormat) = when (format) {
        MapFileFormat.GEOJSON -> GeologicalMapData.toGeoJson(features)
        MapFileFormat.KML -> GeologicalMapData.toKml(features)
    }
    val geoJsonSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MapFileFormat.GEOJSON.mimeType)) { uri ->
        if (uri != null) writeMapFile(uri, mapFileContent(MapFileFormat.GEOJSON), MapFileFormat.GEOJSON, saveAsProjectDestination = true)
    }
    val kmlSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MapFileFormat.KML.mimeType)) { uri ->
        if (uri != null) writeMapFile(uri, mapFileContent(MapFileFormat.KML), MapFileFormat.KML, saveAsProjectDestination = true)
    }

    fun saveMapFile() {
        val geoJson = GeologicalMapData.toGeoJson(features)
        onSaveLayer(geoJson, visible)
        val format = mapFileFormat
        mapFilePreferences.edit().putString("$projectId.selectedFormat", format.name).apply()
        val rememberedUri = mapFilePreferences.getString("$projectId.${format.preferenceSuffix}", null)
            ?: if (format == MapFileFormat.GEOJSON) mapFilePreferences.getString(projectId.toString(), null) else null
        if (rememberedUri != null && format == MapFileFormat.GEOJSON &&
            mapFilePreferences.getString("$projectId.geojson", null) == null
        ) {
            mapFilePreferences.edit().putString("$projectId.geojson", rememberedUri).remove(projectId.toString()).apply()
        }
        val uri = rememberedUri?.let(Uri::parse)
        if (uri == null) {
            val filename = "project-$projectId-map.${format.extension}"
            if (format == MapFileFormat.KML) kmlSaveLauncher.launch(filename) else geoJsonSaveLauncher.launch(filename)
        } else {
            writeMapFile(uri, mapFileContent(format), format, saveAsProjectDestination = false)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(GeologicalMapData.toGeoJson(features)) } }
    }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val mainHandler = Handler(Looper.getMainLooper())
        fun refreshConnection(available: Boolean = hasValidatedInternet(context)) {
            mainHandler.post {
                if (internetState.value != available) {
                    internetState.value = available
                    if (available) {
                        mapLoadError = null
                        mapTimedOut = false
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

    LaunchedEffect(hasInternet, firstFrameRendered, mapReloadToken) {
        mapTimedOut = false
        if (hasInternet && !firstFrameRendered) {
            delay(10_000)
            if (!firstFrameRendered) mapTimedOut = true
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
    LaunchedEffect(userLocation, mapReady) {
        if (mapReady) mapHandle?.getStyle { style ->
            style.getSourceAs<GeoJsonSource>("user-location")?.setGeoJson(userLocationGeoJson(userLocation))
        }
        val location = userLocation
        if (mapReady && location != null && pins.isEmpty() && !userCameraCentered && !userHasMovedMap) {
            mapHandle?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0)
            )
            userCameraCentered = true
        }
    }
    LaunchedEffect(draft, kind, mapReady) {
        if (mapReady) {
            val draftJson = GeologicalMapData.draftToGeoJson(draft, kind)
            mapHandle?.getStyle { it.getSourceAs<GeoJsonSource>("geology-draft")?.setGeoJson(draftJson) }
        }
    }
    LaunchedEffect(visible, mapReady) {
        mapHandle?.getStyle { style ->
            val layerVisibility = if (visible) Property.VISIBLE else Property.NONE
            listOf("geology-fill", "geology-lines", "geology-points", "geology-labels", "geology-draft-fill", "geology-draft-lines", "geology-draft-points").forEach { style.getLayer(it)?.setProperties(visibility(layerVisibility)) }
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
            item {
                Box {
                    AssistChip(
                        onClick = { saveFormatMenuExpanded = true },
                        label = { Text(mapFileFormat.name) },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
                    )
                    DropdownMenu(expanded = saveFormatMenuExpanded, onDismissRequest = { saveFormatMenuExpanded = false }) {
                        MapFileFormat.entries.forEach { format ->
                            DropdownMenuItem(
                                text = { Text(if (format == MapFileFormat.GEOJSON) "GeoJSON" else "KML") },
                                onClick = {
                                    mapFileFormat = format
                                    mapFilePreferences.edit().putString("$projectId.selectedFormat", format.name).apply()
                                    saveFormatMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            item {
                TextButton(onClick = ::saveMapFile, enabled = !savingMapFile) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (savingMapFile) "Saving…" else "Save")
                }
            }
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
                    features.firstOrNull { it.id == selectedId }?.let { feature ->
                        val hsv = FloatArray(3)
                        runCatching { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(feature.color), hsv) }
                        pickerHue = hsv[0]
                        pickerSaturation = hsv[1]
                        pickerBrightness = hsv[2]
                        showColorPicker = true
                    }
                }) { Icon(Icons.Default.Palette, "Choose feature color") }
                IconButton(onClick = { featurePendingDelete = features.firstOrNull { it.id == selectedId } }) { Icon(Icons.Default.Delete, "Delete selected feature") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(mapReloadToken) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapLibre.getInstance(ctx)
                    HttpRequestUtil.setLogEnabled(true)
                    HttpRequestUtil.setPrintRequestUrlOnFailure(true)
                    if (!httpConfigured) {
                        val dispatcher = Dispatcher().apply {
                            maxRequests = 24
                            maxRequestsPerHost = 20
                        }
                        val cache = Cache(File(ctx.cacheDir, "osm-tile-cache"), 256L * 1024L * 1024L)
                        val client = OkHttpClient.Builder().cache(cache).addInterceptor { chain ->
                            val request = chain.request().newBuilder().header("User-Agent", "StrataField/1.0.0 (Android geological field mapping)").build()
                            val startedAt = System.nanoTime()
                            try {
                                val response = chain.proceed(request)
                                if (!response.isSuccessful) {
                                    android.util.Log.w("StrataMapNetwork", "HTTP ${response.code} in ${(System.nanoTime() - startedAt) / 1_000_000}ms ${request.url.host}${request.url.encodedPath}")
                                }
                                response
                            } catch (error: java.io.IOException) {
                                android.util.Log.e("StrataMapNetwork", "request failed after ${(System.nanoTime() - startedAt) / 1_000_000}ms ${request.url.host}${request.url.encodedPath}: ${error.javaClass.simpleName}: ${error.message}")
                                throw error
                            }
                        }.dispatcher(dispatcher)
                            .connectionPool(ConnectionPool(8, 5, java.util.concurrent.TimeUnit.MINUTES))
                            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build()
                        HttpRequestUtil.setOkHttpClient(client)
                        httpConfigured = true
                    }
                    MapView(ctx).also { view ->
                        android.util.Log.i("StrataMapLifecycle", "create MapView reload=$mapReloadToken")
                        mapView = view
                        firstFrameRendered = false
                        mapLoadError = null
                        view.addOnDidFinishRenderingMapListener { _ ->
                            // Reveal the map as soon as the first frame is drawn; vector tiles can continue loading.
                            android.util.Log.d("StrataMapLifecycle", "MapView rendered a frame")
                            firstFrameRendered = true
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
                            map.addOnCameraMoveStartedListener { reason ->
                                if (reason == org.maplibre.android.maps.MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                    userHasMovedMap = true
                                }
                            }
                            map.setStyle(Style.Builder().fromJson(SATELLITE_STYLE)) { style ->
                                android.util.Log.i("StrataMapLifecycle", "style loaded")
                                style.addSource(GeoJsonSource("geology", GeologicalMapData.toGeoJson(features)))
                                style.addSource(GeoJsonSource("geology-draft", GeologicalMapData.draftToGeoJson(draft, kind)))
                                style.addSource(GeoJsonSource("form-pins", pinsGeoJson(pins)))
                                style.addSource(GeoJsonSource("user-location", userLocationGeoJson(userLocation)))
                                style.addLayer(FillLayer("geology-fill", "geology").withProperties(fillColor(org.maplibre.android.style.expressions.Expression.get("color")), fillOpacity(0.3f), fillOutlineColor(org.maplibre.android.style.expressions.Expression.get("color"))))
                                style.addLayer(LineLayer("geology-lines", "geology").withProperties(lineColor(org.maplibre.android.style.expressions.Expression.get("color")), lineWidth(1.5f)))
                                style.addLayer(CircleLayer("geology-points", "geology").withProperties(circleRadius(7f), circleColor(org.maplibre.android.style.expressions.Expression.get("color")), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                                style.addLayer(SymbolLayer("geology-labels", "geology").withProperties(textField(org.maplibre.android.style.expressions.Expression.get("name")), textFont(arrayOf("Noto Sans Regular")), textSize(10f), textColor("#263238"), textHaloColor("#FFFFFF"), textHaloWidth(1.5f), textOffset(arrayOf(0f, 1.1f))))
                                style.addLayer(FillLayer("geology-draft-fill", "geology-draft").withProperties(fillColor(org.maplibre.android.style.expressions.Expression.get("color")), fillOpacity(0.24f), fillOutlineColor(org.maplibre.android.style.expressions.Expression.get("color"))))
                                style.addLayer(LineLayer("geology-draft-lines", "geology-draft").withProperties(lineColor(org.maplibre.android.style.expressions.Expression.get("color")), lineWidth(1.5f)))
                                style.addLayer(CircleLayer("geology-draft-points", "geology-draft").withProperties(circleRadius(6f), circleColor(org.maplibre.android.style.expressions.Expression.get("color")), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                                style.addLayer(CircleLayer("form-pin-circles", "form-pins").withProperties(circleRadius(8f), circleColor("#1565C0"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                                style.addLayer(CircleLayer("user-location-halo", "user-location").withProperties(circleRadius(12f), circleColor("#2196F3"), circleOpacity(0.22f)))
                                style.addLayer(CircleLayer("user-location-dot", "user-location").withProperties(circleRadius(6f), circleColor("#1565C0"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2.5f)))
                                style.addLayer(SymbolLayer("form-pin-labels", "form-pins").withProperties(textField(org.maplibre.android.style.expressions.Expression.get("name")), textFont(arrayOf("Noto Sans Regular")), textSize(11f), textColor("#10243A"), textHaloColor("#FFFFFF"), textHaloWidth(1.5f), textOffset(arrayOf(0f, 1.2f))))
                                map.uiSettings.isRotateGesturesEnabled = true
                                map.uiSettings.setCompassEnabled(true)
                                map.uiSettings.setCompassFadeFacingNorth(false)
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
                                        MapMode.DELETE -> {
                                            selectedPin = null
                                            featurePendingDelete = hitTest(tapped)
                                            selectedId = featurePendingDelete?.id
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
                                    val initialTarget = userLocation?.let { LatLng(it.latitude, it.longitude) }
                                        ?: pins.firstOrNull()?.point?.let { LatLng(it.latitude, it.longitude) }
                                        ?: LatLng(0.0, 0.0)
                                    val initialZoom = when {
                                        userLocation != null -> 15.0
                                        pins.isNotEmpty() -> 13.0
                                        else -> 2.5
                                    }
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(initialTarget)
                                        .zoom(initialZoom)
                                        .build()
                                    userCameraCentered = userLocation != null
                                    cameraInitialized = true
                                }
                            }
                        }
                    }
                },
                update = {
                    mapHandle?.let { map ->
                        map.uiSettings.isScrollGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                    }
                }
            )
            }
            val statusMessage = when {
                !hasInternet -> "No internet connection. Connect and retry to load the online map."
                mapLoadError != null -> "The map could not be loaded. Check your connection and retry."
                mapTimedOut && !mapReady -> "The online map style could not finish loading. Check your connection and retry."
                mapTimedOut -> "Map tiles are taking too long to load. Check your connection and retry."
                !firstFrameRendered -> "Loading map…"
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
                        if (!firstFrameRendered) {
                            TextButton(onClick = {
                                mapReady = false
                                firstFrameRendered = false
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
                Text("Source: Esri, Vantor, Earthstar Geographics, and the GIS User Community", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                Text("Satellite imagery", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            }
            selectedPin?.let { pin ->
                val response = responses.firstOrNull { it.id == pin.responseId }
                Card(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(.9f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(pin.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(pin.notes.ifBlank { "No general notes" }, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { response?.let(onViewResponse) }, enabled = response != null) { Text("Details") }
                            TextButton(
                                onClick = {
                                    response?.let(onDeleteResponse)
                                    selectedPin = null
                                },
                                enabled = response != null,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
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

    val activeMapView = mapView
    DisposableEffect(activeMapView) {
        onDispose {
            activeMapView?.let { view ->
                android.util.Log.i("StrataMapLifecycle", "destroy MapView")
                view.onPause(); view.onStop(); view.onDestroy()
            }
            if (mapView === activeMapView) mapView = null
        }
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
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Feature color · HSB") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HsbColorPicker(
                        hue = pickerHue,
                        saturation = pickerSaturation,
                        brightness = pickerBrightness,
                        onHueChange = { pickerHue = it },
                        onSaturationBrightnessChange = { saturation, brightness ->
                            pickerSaturation = saturation
                            pickerBrightness = brightness
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(24.dp).background(hsbComposeColor(pickerHue, pickerSaturation, pickerBrightness), RoundedCornerShape(4.dp)))
                        Text(hsbHexColor(pickerHue, pickerSaturation, pickerBrightness), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    checkpoint()
                    val selectedColor = hsbHexColor(pickerHue, pickerSaturation, pickerBrightness)
                    features = features.map { if (it.id == selectedId) it.copy(color = selectedColor) else it }
                    save()
                    showColorPicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showColorPicker = false }) { Text("Cancel") } }
        )
    }
    featurePendingDelete?.let { feature ->
        AlertDialog(
            onDismissRequest = { featurePendingDelete = null },
            title = { Text("Delete map feature?") },
            text = { Text("Delete \"${feature.name}\" from this map? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        checkpoint()
                        features = features.filterNot { it.id == feature.id }
                        if (selectedId == feature.id) selectedId = null
                        featurePendingDelete = null
                        save()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { featurePendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HsbColorPicker(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onHueChange: (Float) -> Unit,
    onSaturationBrightnessChange: (Float, Float) -> Unit
) {
    val hueColor = hsbComposeColor(hue, 1f, 1f)
    val hueColors = remember {
        listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)
    }
    Canvas(
        Modifier.fillMaxWidth().height(180.dp)
            .pointerInput(Unit) {
                fun update(position: Offset) {
                    onSaturationBrightnessChange(
                        (position.x / size.width).coerceIn(0f, 1f),
                        (1f - position.y / size.height).coerceIn(0f, 1f)
                    )
                }
                detectTapGestures { update(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        onSaturationBrightnessChange(
                            (position.x / size.width).coerceIn(0f, 1f),
                            (1f - position.y / size.height).coerceIn(0f, 1f)
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onSaturationBrightnessChange(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        )
                    }
                )
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val marker = Offset(saturation * size.width, (1f - brightness) * size.height)
        drawCircle(Color.Black, radius = 9.dp.toPx(), center = marker, style = Stroke(width = 2.dp.toPx()))
        drawCircle(Color.White, radius = 7.dp.toPx(), center = marker, style = Stroke(width = 2.dp.toPx()))
    }
    Canvas(
        Modifier.fillMaxWidth().height(24.dp)
            .pointerInput(Unit) {
                fun update(position: Offset) = onHueChange((position.x / size.width).coerceIn(0f, 1f) * 360f)
                detectTapGestures { update(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position -> onHueChange((position.x / size.width).coerceIn(0f, 1f) * 360f) },
                    onDrag = { change, _ ->
                        change.consume()
                        onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                )
            }
    ) {
        drawRect(Brush.horizontalGradient(hueColors))
        val x = hue / 360f * size.width
        drawCircle(Color.Black, radius = 9.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(width = 2.dp.toPx()))
        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(width = 2.dp.toPx()))
    }
}

private fun hsbComposeColor(hue: Float, saturation: Float, brightness: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

private fun hsbHexColor(hue: Float, saturation: Float, brightness: Float): String =
    "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF)

private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(a.latitude - b.latitude); val dLon = Math.toRadians(a.longitude - b.longitude)
    val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) + kotlin.math.cos(Math.toRadians(a.latitude)) * kotlin.math.cos(Math.toRadians(b.latitude)) * kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return 12_742_000.0 * kotlin.math.asin(kotlin.math.sqrt(h))
}

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

private fun userLocationGeoJson(location: Location?): String = com.google.gson.Gson().toJson(com.google.gson.JsonObject().apply {
    addProperty("type", "FeatureCollection")
    add("features", com.google.gson.JsonArray().also { features ->
        if (location != null) {
            features.add(com.google.gson.JsonObject().apply {
                addProperty("type", "Feature")
                add("properties", com.google.gson.JsonObject())
                add("geometry", com.google.gson.JsonObject().apply {
                    addProperty("type", "Point")
                    add("coordinates", com.google.gson.JsonArray().also { it.add(location.longitude); it.add(location.latitude) })
                })
            })
        }
    })
})
