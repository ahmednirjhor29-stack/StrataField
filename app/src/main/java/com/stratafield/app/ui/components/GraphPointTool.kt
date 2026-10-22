package com.stratafield.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stratafield.app.ui.theme.*
import kotlin.math.hypot

data class GraphPoint(
    val id: String,
    val x: Float, // real-world units
    val y: Float, // real-world units
    val color: Long = 0L
)

data class GraphConnection(
    val id: String,
    val fromId: String,
    val toId: String,
    val curveX: Float = 0f, // px offset of the bezier control point, opposite the drag direction
    val curveY: Float = 0f
)

data class GraphPaperData(
    val scaleRatio: Float = 1f, // real-world distance represented by one grid square
    val squareUnit: String = "cm",
    val points: List<GraphPoint> = emptyList(),
    val connections: List<GraphConnection> = emptyList()
)

private sealed class GraphAction {
    data class PointAdded(val id: String) : GraphAction()
    data class ConnectionAdded(val id: String) : GraphAction()
}

private const val GRID_DP = 28f
private const val HIT_RADIUS_PX = 45f

@Composable
fun GraphPointTool(
    modifier: Modifier = Modifier,
    initialData: GraphPaperData = GraphPaperData(),
    latitude: String = "",
    longitude: String = "",
    onAddCoordinates: (String, String) -> Unit = { _, _ -> },
    onDataChange: (GraphPaperData) -> Unit = {}
) {
    var scaleRatioText by remember { mutableStateOf(if (initialData.scaleRatio == 1f) "1" else initialData.scaleRatio.toString()) }
    var squareUnit by remember { mutableStateOf(initialData.squareUnit.ifBlank { "cm" }) }
    var points by remember { mutableStateOf(initialData.points) }
    var connections by remember { mutableStateOf(initialData.connections) }
    val history = remember { mutableStateListOf<GraphAction>() }

    var connectMode by remember { mutableStateOf(false) }
    var pointEditMode by remember { mutableStateOf(false) }
    var selectedPointId by remember { mutableStateOf<String?>(null) }
    var dragPreviewFromId by remember { mutableStateOf<String?>(null) }
    var dragPreviewToId by remember { mutableStateOf<String?>(null) }
    var dragPreviewCurrent by remember { mutableStateOf<Offset?>(null) }
    var dragPreviewCurve by remember { mutableStateOf(Offset.Zero) }

    var showAddPoint by remember { mutableStateOf(false) }
    var showScaleDialog by remember { mutableStateOf(false) }
    var showUnitDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("#FF2457") }
    var latitudeText by remember(latitude) { mutableStateOf(latitude) }
    var longitudeText by remember(longitude) { mutableStateOf(longitude) }

    var zoom by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    fun pushData() {
        val ratio = scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
        onDataChange(GraphPaperData(ratio, squareUnit, points, connections))
    }

    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline
    val pointColor = MaterialTheme.colorScheme.primary
    val selectedColor = NothingRed
    val lineColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    RibbonToolButton(Icons.Filled.Undo, "Undo", history.isNotEmpty()) {
                        val action = history.removeLastOrNull()
                        if (action != null) {
                            when (action) {
                                is GraphAction.PointAdded -> { points = points.filterNot { it.id == action.id }; connections = connections.filterNot { it.fromId == action.id || it.toId == action.id } }
                                is GraphAction.ConnectionAdded -> connections = connections.filterNot { it.id == action.id }
                            }
                            selectedPointId = null; pushData()
                        }
                    }
                    RibbonToolButton(Icons.Filled.Add, "Add point") { showAddPoint = true }
                    RibbonToolButton(Icons.Filled.Edit, "Select point") { pointEditMode = !pointEditMode; connectMode = false }
                    RibbonToolButton(Icons.Filled.Palette, "Point color", selectedPointId != null) {
                        customColorHex = selectedPointId?.let { id -> points.firstOrNull { it.id == id }?.color?.takeIf { it != 0L }?.let { String.format("#%06X", 0xFFFFFF and Color(it.toULong()).toArgb()) } } ?: "#FF2457"
                        showColorDialog = true
                    }
                    RibbonToolButton(Icons.Filled.Delete, "Remove point", selectedPointId != null) { selectedPointId?.let { id -> points = points.filterNot { it.id == id }; connections = connections.filterNot { it.fromId == id || it.toId == id }; selectedPointId = null; pushData() } }
                    RibbonToolButton(Icons.Filled.Timeline, "Connect") { connectMode = !connectMode; pointEditMode = false; selectedPointId = null }
                    RibbonToolButton(Icons.Filled.RemoveRoad, "Remove lines", connections.isNotEmpty()) { connections = emptyList(); pushData() }
                    RibbonToolButton(Icons.Filled.Tune, "Scale") { showScaleDialog = true }
                    RibbonToolButton(Icons.Filled.Straighten, "Units") { showUnitDialog = true }
                    RibbonToolButton(Icons.Filled.ZoomOut, "Zoom out") { zoom = (zoom / 1.25f).coerceIn(0.12f, 12f) }
                    RibbonToolButton(Icons.Filled.ZoomIn, "Zoom in") { zoom = (zoom * 1.25f).coerceIn(0.12f, 12f) }
                    RibbonToolButton(Icons.Filled.CenterFocusStrong, "Recenter") { zoom = 1f; panOffset = Offset.Zero }
                    RibbonToolButton(Icons.Filled.ClearAll, "Clear") { points = emptyList(); connections = emptyList(); history.clear(); selectedPointId = null; pushData() }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(latitudeText, { latitudeText = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.width(145.dp), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(longitudeText, { longitudeText = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.width(145.dp), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    Button(onClick = { onAddCoordinates(latitudeText, longitudeText) }, enabled = latitudeText.toDoubleOrNull() != null && longitudeText.toDoubleOrNull() != null, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                        Icon(Icons.Filled.AddLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add")
                    }
                }
            }
        }
        // Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(connectMode, pointEditMode) {
                        if (connectMode || pointEditMode) return@pointerInput
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.12f, 12f)
                            panOffset += pan
                        }
                    }
                    .pointerInput(connectMode, pointEditMode, points, zoom, panOffset) {
                        if (!connectMode && !pointEditMode) return@pointerInput
                        val gridPx = GRID_DP.dp.toPx() * zoom
                        awaitEachGesture {
                            val down = awaitFirstDownEvent()
                            val originX = size.width / 2f + panOffset.x
                            val originY = size.height / 2f + panOffset.y
                            val hit = points.minByOrNull { p ->
                                val px = originX + (p.x / (scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f)) * gridPx
                                val py = originY - (p.y / (scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f)) * gridPx
                                hypot((px - down.x).toDouble(), (py - down.y).toDouble()).toFloat()
                            }?.takeIf { p ->
                                val px = originX + (p.x / (scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f)) * gridPx
                                val py = originY - (p.y / (scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f)) * gridPx
                                hypot((px - down.x).toDouble(), (py - down.y).toDouble()) <= HIT_RADIUS_PX
                            }

                            if (pointEditMode) {
                                selectedPointId = hit?.id
                                waitForUpAfter(down.id)
                                return@awaitEachGesture
                            }

                            if (selectedPointId == null) {
                                if (hit != null) {
                                    selectedPointId = hit.id
                                }
                                waitForUpAfter(down.id)
                                return@awaitEachGesture
                            }

                            val fromId = selectedPointId!!
                            if (hit == null || hit.id == fromId) {
                                waitForUpAfter(down.id)
                                selectedPointId = null
                                return@awaitEachGesture
                            }

                            dragPreviewFromId = fromId
                            dragPreviewToId = hit.id
                            var dragTotal = Offset.Zero
                            var currentPos = Offset(down.x, down.y)
                            dragPreviewCurrent = currentPos
                            dragPreviewCurve = Offset.Zero

                            trackDragUntilUp(down.id) { change ->
                                dragTotal += change
                                currentPos += change
                                dragPreviewCurrent = currentPos
                                val factor = 1.5f
                                val curve = Offset(-dragTotal.x * factor, -dragTotal.y * factor)
                                val maxMag = 420f
                                val mag = hypot(curve.x.toDouble(), curve.y.toDouble()).toFloat()
                                dragPreviewCurve = if (mag > maxMag) curve * (maxMag / mag) else curve
                            }

                            val endHit = hit

                            if (endHit.id != fromId) {
                                val newConn = GraphConnection(
                                    id = java.util.UUID.randomUUID().toString(),
                                    fromId = fromId,
                                    toId = endHit.id,
                                    curveX = dragPreviewCurve.x,
                                    curveY = dragPreviewCurve.y
                                )
                                connections = connections + newConn
                                history.add(GraphAction.ConnectionAdded(newConn.id))
                                pushData()
                            }
                            selectedPointId = null
                            dragPreviewFromId = null
                            dragPreviewToId = null
                            dragPreviewCurrent = null
                            dragPreviewCurve = Offset.Zero
                        }
                    }
            ) {
                val ratio = scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
                val gridPx = GRID_DP.dp.toPx() * zoom
                val originX = size.width / 2f + panOffset.x
                val originY = size.height / 2f + panOffset.y

                fun toPx(p: GraphPoint): Offset =
                    Offset(originX + (p.x / ratio) * gridPx, originY - (p.y / ratio) * gridPx)

                fun lineStyle(index: Int): Pair<Color, Float> {
                    val majorEvery = when { zoom < 0.35f -> 20; zoom < 0.7f -> 10; zoom > 4f -> 2; else -> 5 }
                    val isTenth = index % majorEvery == 0
                    val isRed = isTenth && (index / majorEvery) % 2 == 0
                    return when {
                        isRed -> NothingRed.copy(alpha = 0.7f) to 2.4f
                        isTenth -> gridColor.copy(alpha = 0.55f) to 2.0f
                        else -> gridColor.copy(alpha = 0.25f) to 1f
                    }
                }

                val minXIndex = kotlin.math.floor(((0f - originX) / gridPx).toDouble()).toInt()
                val maxXIndex = kotlin.math.ceil(((size.width - originX) / gridPx).toDouble()).toInt()
                for (i in minXIndex..maxXIndex) {
                    val x = originX + i * gridPx
                    val (color, width) = lineStyle(i)
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), width)
                }
                val minYIndex = kotlin.math.floor(((0f - originY) / gridPx).toDouble()).toInt()
                val maxYIndex = kotlin.math.ceil(((size.height - originY) / gridPx).toDouble()).toInt()
                for (i in minYIndex..maxYIndex) {
                    val y = originY + i * gridPx
                    val (color, width) = lineStyle(i)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), width)
                }

                // Axes
                drawLine(axisColor, Offset(0f, originY), Offset(size.width, originY), 2.5f)
                drawLine(axisColor, Offset(originX, 0f), Offset(originX, size.height), 2.5f)

                // Coordinate labels use the current scale and selected unit.
                val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = axisColor.toArgb()
                    textSize = 11.dp.toPx()
                }
                val majorEvery = when { zoom < 0.35f -> 20; zoom < 0.7f -> 10; zoom > 4f -> 2; else -> 5 }
                for (i in minXIndex..maxXIndex) {
                    if (i % majorEvery == 0) {
                        val x = originX + i * gridPx
                        drawContext.canvas.nativeCanvas.drawText("${formatAxisValue(i * ratio)} $squareUnit", x + 3.dp.toPx(), 14.dp.toPx(), labelPaint)
                    }
                }
                for (i in minYIndex..maxYIndex) {
                    if (i % majorEvery == 0) {
                        val y = originY - i * gridPx
                        drawContext.canvas.nativeCanvas.drawText("${formatAxisValue(i * ratio)} $squareUnit", 3.dp.toPx(), y - 3.dp.toPx(), labelPaint)
                    }
                }

                // Connections
                connections.forEach { conn ->
                    val from = points.find { it.id == conn.fromId }
                    val to = points.find { it.id == conn.toId }
                    if (from != null && to != null) {
                        val p1 = toPx(from)
                        val p2 = toPx(to)
                        val path = Path()
                        path.moveTo(p1.x, p1.y)
                        if (conn.curveX == 0f && conn.curveY == 0f) {
                            path.lineTo(p2.x, p2.y)
                        } else {
                            val mid = Offset((p1.x + p2.x) / 2f + conn.curveX, (p1.y + p2.y) / 2f + conn.curveY)
                            path.quadraticBezierTo(mid.x, mid.y, p2.x, p2.y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 3f))
                    }
                }

                // Live preview while dragging second point
                val fromId = dragPreviewFromId
                val toId = dragPreviewToId
                val current = dragPreviewCurrent
                if (fromId != null && current != null) {
                    val from = points.find { it.id == fromId }
                    val to = points.find { it.id == toId }
                    if (from != null) {
                        val p1 = toPx(from)
                        val p2 = to?.let { toPx(it) } ?: current
                        val path = Path()
                        path.moveTo(p1.x, p1.y)
                        val mid = Offset((p1.x + p2.x) / 2f + dragPreviewCurve.x, (p1.y + p2.y) / 2f + dragPreviewCurve.y)
                        path.quadraticBezierTo(mid.x, mid.y, p2.x, p2.y)
                        drawPath(path, lineColor.copy(alpha = 0.5f), style = Stroke(width = 3f))
                    }
                }

                // Points
                points.forEach { p ->
                    val px = toPx(p)
                    drawCircle(
                        color = if (p.id == selectedPointId) selectedColor else if (p.color == 0L) pointColor else Color(p.color.toULong()),
                        radius = 8f,
                        center = px
                    )
                }
            }

            Text(
                text = "${(zoom * 100).toInt()}%",
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

        }

        Text(
            text = if (connectMode) {
                if (selectedPointId == null) "Tap a point to start a connection" else "Tap (or tap+drag to curve) another point to connect"
            } else if (pointEditMode) {
                if (selectedPointId == null) "Tap a point to select it for recoloring or removal" else "Point selected · choose a color or remove it from the menu"
            } else {
                "Pinch to zoom, drag to pan (${(zoom * 100).toInt()}%)"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }

    if (showScaleDialog) {
        AlertDialog(
            onDismissRequest = { showScaleDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Scale Ratio", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = scaleRatioText,
                    onValueChange = { scaleRatioText = it },
                    label = { Text("Grid square ratio") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pushData()
                    showScaleDialog = false
                }) {
                    Text("Add Point", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showScaleDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showUnitDialog) {
        AlertDialog(
            onDismissRequest = { showUnitDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Choose Units", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                UnitMenu(value = squareUnit, onChange = { newUnit -> squareUnit = newUnit; pushData() })
            },
            confirmButton = {
                TextButton(onClick = { showUnitDialog = false }) {
                    Text("Done", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Custom point color") },
            text = {
                OutlinedTextField(
                    value = customColorHex,
                    onValueChange = { customColorHex = it },
                    label = { Text("Hex color (for example #FF2457)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val color = android.graphics.Color.parseColor(customColorHex)
                        selectedPointId?.let { id -> points = points.map { if (it.id == id) it.copy(color = Color(color).value.toLong()) else it } }
                        pushData()
                        showColorDialog = false
                    } catch (_: IllegalArgumentException) { }
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showColorDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddPoint) {
        var xText by remember { mutableStateOf("") }
        var yText by remember { mutableStateOf("") }
        var xUnit by remember { mutableStateOf(squareUnit) }
        var yUnit by remember { mutableStateOf(squareUnit) }
        AlertDialog(
            onDismissRequest = { showAddPoint = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Add Point", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = xText,
                            onValueChange = { xText = it },
                            label = { Text("X distance") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = yText,
                            onValueChange = { yText = it },
                            label = { Text("Y distance") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UnitMenu(value = xUnit, onChange = { xUnit = it })
                        UnitMenu(value = yUnit, onChange = { yUnit = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val x = xText.toFloatOrNull()
                    val y = yText.toFloatOrNull()
                    if (x != null && y != null) {
                        val newPoint = GraphPoint(
                            java.util.UUID.randomUUID().toString(),
                            convertUnit(x, xUnit, squareUnit),
                            convertUnit(y, yUnit, squareUnit)
                        )
                        points = points + newPoint
                        history.add(GraphAction.PointAdded(newPoint.id))
                        pushData()
                        showAddPoint = false
                    }
                }) {
                    Text("Add Point", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPoint = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun RibbonToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

private fun formatAxisValue(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

@Composable
private fun UnitMenu(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(value)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("cm", "m", "inch", "foot").forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onChange(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun convertUnit(value: Float, from: String, to: String): Float {
    fun toCentimeters(v: Float, unit: String): Float = when (unit) {
        "m" -> v * 100f
        "inch" -> v * 2.54f
        "foot" -> v * 30.48f
        else -> v
    }
    fun fromCentimeters(v: Float, unit: String): Float = when (unit) {
        "m" -> v / 100f
        "inch" -> v / 2.54f
        "foot" -> v / 30.48f
        else -> v
    }
    return fromCentimeters(toCentimeters(value, from), to)
}

private data class DownPoint(val id: androidx.compose.ui.input.pointer.PointerId, val x: Float, val y: Float)

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitFirstDownEvent(): DownPoint {
    val down = awaitFirstDown(requireUnconsumed = false)
    return DownPoint(down.id, down.position.x, down.position.y)
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpAfter(
    pointerId: androidx.compose.ui.input.pointer.PointerId
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        if (!change.pressed) return
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.trackDragUntilUp(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    onDelta: (Offset) -> Unit
): Offset {
    var last = Offset.Zero
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return last
        if (!change.pressed) return last
        val delta = change.position - change.previousPosition
        onDelta(delta)
        change.consume()
        last = change.position
    }
}

// Serialization helpers
fun serializeGraphPaper(data: GraphPaperData): String = Gson().toJson(data)

fun deserializeGraphPaper(json: String): GraphPaperData {
    return try {
        if (json.isBlank()) GraphPaperData()
        else Gson().fromJson(json, GraphPaperData::class.java) ?: GraphPaperData()
    } catch (e: Exception) {
        GraphPaperData()
    }
}
