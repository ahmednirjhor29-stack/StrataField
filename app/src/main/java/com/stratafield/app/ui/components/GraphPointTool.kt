package com.stratafield.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val connections: List<GraphConnection> = emptyList(),
    val inkStrokes: List<GraphInkStroke>? = emptyList(),
    val scaleRatioNumerator: Float = 1f
)

data class GraphInkPoint(val x: Float, val y: Float)
data class GraphInkStroke(val points: List<GraphInkPoint>, val color: Long, val width: Float = 3f)

private data class GraphSnapshot(val points: List<GraphPoint>, val connections: List<GraphConnection>, val inkStrokes: List<GraphInkStroke>, val scaleRatio: String, val ratioNumerator: String, val ratioDenominator: String, val unit: String, val anchor: Offset)

private const val GRID_DP = 28f
private const val HIT_RADIUS_PX = 45f

private fun ratioFieldText(value: Float): String = value.toString().removeSuffix(".0")

@Composable
fun GraphPointTool(
    modifier: Modifier = Modifier,
    initialData: GraphPaperData = GraphPaperData(),
    onClose: () -> Unit = {},
    onDataChange: (GraphPaperData) -> Unit = {}
) {
    val density = LocalDensity.current.density
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val initialNumerator = initialData.scaleRatioNumerator.takeIf { it.isFinite() && it > 0f } ?: 1f
    var scaleRatioText by remember { mutableStateOf(ratioFieldText(initialData.scaleRatio)) }
    var ratioNumeratorInput by remember { mutableStateOf(ratioFieldText(initialNumerator)) }
    var ratioDenominatorInput by remember { mutableStateOf(ratioFieldText(initialData.scaleRatio * initialNumerator)) }
    var squareUnit by remember { mutableStateOf(initialData.squareUnit.ifBlank { "cm" }) }
    var points by remember { mutableStateOf(initialData.points) }
    var connections by remember { mutableStateOf(initialData.connections) }
    var inkStrokes by remember { mutableStateOf(initialData.inkStrokes.orEmpty()) }
    var currentInkPoints by remember { mutableStateOf<List<GraphInkPoint>>(emptyList()) }
    var eraserMode by remember { mutableStateOf(false) }
    var inkColor by remember { mutableStateOf(NothingBlack) }
    var xInput by remember { mutableStateOf("") }
    var yInput by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<GraphSnapshot>() }
    val redoHistory = remember { mutableStateListOf<GraphSnapshot>() }

    var connectMode by remember { mutableStateOf(false) }
    var pointEditMode by remember { mutableStateOf(false) }
    var selectedPointId by remember { mutableStateOf<String?>(null) }
    var dragPreviewFromId by remember { mutableStateOf<String?>(null) }
    var dragPreviewToId by remember { mutableStateOf<String?>(null) }
    var dragPreviewCurrent by remember { mutableStateOf<Offset?>(null) }
    var dragPreviewCurve by remember { mutableStateOf(Offset.Zero) }

    var showUnitDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var eyedropperMode by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("#FF2457") }

    var zoom by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    // Anchor is stored in graph units so it remains attached to the paper while the view moves.
    var anchor by remember { mutableStateOf(Offset.Zero) }
    var northArrow by remember { mutableStateOf(Offset(0f, -1f)) }
    var anchorMode by remember { mutableStateOf(false) }
    var northMode by remember { mutableStateOf(false) }

    fun pushData() {
        val ratio = scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
        onDataChange(GraphPaperData(ratio, squareUnit, points, connections, inkStrokes, ratioNumeratorInput.toFloatOrNull()?.takeIf { it > 0f } ?: 1f))
    }

    fun changeUnit(newUnit: String) {
        val oldUnit = squareUnit
        if (oldUnit == newUnit) return
        history.add(GraphSnapshot(points.toList(),connections.toList(),inkStrokes.toList(),scaleRatioText,ratioNumeratorInput,ratioDenominatorInput,squareUnit,anchor))
        redoHistory.clear()
        // Keep the grid calibration number fixed in the newly selected unit.
        // This converts coordinates to the new unit and redraws them at their
        // corresponding locations on that unit's grid (10 cm becomes 0.1 m).
        points = points.map { point -> point.copy(x=convertUnit(point.x,oldUnit,newUnit),y=convertUnit(point.y,oldUnit,newUnit)) }
        inkStrokes = inkStrokes.map { stroke -> stroke.copy(points=stroke.points.map { p -> GraphInkPoint(convertUnit(p.x,oldUnit,newUnit),convertUnit(p.y,oldUnit,newUnit)) }) }
        anchor = Offset(convertUnit(anchor.x,oldUnit,newUnit),convertUnit(anchor.y,oldUnit,newUnit))
        xInput = xInput.toFloatOrNull()?.let { convertUnit(it,oldUnit,newUnit).toString() } ?: xInput
        yInput = yInput.toFloatOrNull()?.let { convertUnit(it,oldUnit,newUnit).toString() } ?: yInput
        squareUnit = newUnit
        pushData()
    }

    fun snapshot() = GraphSnapshot(points.toList(), connections.toList(), inkStrokes.toList(), scaleRatioText, ratioNumeratorInput, ratioDenominatorInput, squareUnit, anchor)
    fun recordUndo() { history.add(snapshot()); redoHistory.clear() }
    fun changeScaleRatio(newRatio: Float, numeratorText: String, denominatorText: String) {
        val oldRatio=scaleRatioText.toFloatOrNull()?.takeIf { it>0f } ?: 1f
        if(!newRatio.isFinite() || newRatio<=0f) return
        if(newRatio!=oldRatio) {
            recordUndo()
            val factor=newRatio/oldRatio
            // Recalibrate stored coordinates while preserving their positions on the paper.
            points=points.map { it.copy(x=it.x*factor,y=it.y*factor) }
            inkStrokes=inkStrokes.map { stroke -> stroke.copy(points=stroke.points.map { GraphInkPoint(it.x*factor,it.y*factor) }) }
            anchor=anchor*factor
            xInput=xInput.toFloatOrNull()?.let { ratioFieldText(it*factor) } ?: xInput
            yInput=yInput.toFloatOrNull()?.let { ratioFieldText(it*factor) } ?: yInput
            scaleRatioText=ratioFieldText(newRatio)
        }
        ratioNumeratorInput=numeratorText
        ratioDenominatorInput=denominatorText
        pushData()
    }
    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        redoHistory.add(snapshot())
        points = previous.points; connections = previous.connections; inkStrokes = previous.inkStrokes
        scaleRatioText=previous.scaleRatio; ratioNumeratorInput=previous.ratioNumerator; ratioDenominatorInput=previous.ratioDenominator; squareUnit=previous.unit; anchor=previous.anchor
        selectedPointId = null; pushData()
    }
    fun redo() {
        val next = redoHistory.removeLastOrNull() ?: return
        history.add(snapshot())
        points = next.points; connections = next.connections; inkStrokes = next.inkStrokes
        scaleRatioText=next.scaleRatio; ratioNumeratorInput=next.ratioNumerator; ratioDenominatorInput=next.ratioDenominator; squareUnit=next.unit; anchor=next.anchor
        selectedPointId = null; pushData()
    }

    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline
    val pointColor = MaterialTheme.colorScheme.primary
    val selectedColor = NothingRed
    val lineColor = MaterialTheme.colorScheme.onSurface
    val eraserPreviewColor = MaterialTheme.colorScheme.error

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Graph Paper", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = ::undo, enabled = history.isNotEmpty()) { Icon(Icons.Filled.Undo, contentDescription = "Undo") }
            IconButton(onClick = ::redo, enabled = redoHistory.isNotEmpty()) { Icon(Icons.Filled.Redo, contentDescription = "Redo") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close graph paper") }
        }
        Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
            Column(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    RibbonToolButton(Icons.Filled.CleaningServices, "Eraser", true, Modifier.weight(1f), eraserMode) { eraserMode = !eraserMode; connectMode = false; pointEditMode = false }
                    RibbonToolButton(Icons.Filled.TouchApp, "Select", true, Modifier.weight(1f), pointEditMode) { pointEditMode = !pointEditMode; connectMode = false; eraserMode = false }
                    RibbonToolButton(Icons.Filled.Timeline, "Connect", true, Modifier.weight(1f), connectMode) { connectMode = !connectMode; pointEditMode = false; eraserMode = false }
                    RibbonToolButton(Icons.Filled.Palette, "Color", true, Modifier.weight(1f)) {
                        customColorHex = selectedPointId?.let { id -> points.firstOrNull { it.id == id }?.color?.takeIf { it != 0L }?.let { String.format("#%06X", 0xFFFFFF and Color(it.toULong()).toArgb()) } }
                            ?: String.format("#%06X", 0xFFFFFF and inkColor.toArgb())
                        showColorDialog = true
                    }
                    RibbonToolButton(Icons.Filled.Delete, "Del point", selectedPointId != null, Modifier.weight(1f)) { selectedPointId?.let { id -> recordUndo(); points = points.filterNot { it.id == id }; connections = connections.filterNot { it.fromId == id || it.toId == id }; selectedPointId = null; pushData() } }
                    RibbonToolButton(Icons.Filled.RemoveRoad, "Del lines", connections.isNotEmpty(), Modifier.weight(1f)) { if (connections.isNotEmpty()) { recordUndo(); connections = emptyList(); pushData() } }
                    RibbonToolButton(Icons.Filled.ArrowUpward, "North", true, Modifier.weight(1f), northMode) { northMode=!northMode; anchorMode=false }
                    RibbonToolButton(Icons.Filled.MyLocation, "Anchor", true, Modifier.weight(1f), anchorMode) { anchorMode=!anchorMode; northMode=false }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    RibbonToolButton(Icons.Filled.CenterFocusStrong, "Center", true, Modifier.weight(1f)) { val g=GRID_DP*density*zoom; val ratio=scaleRatioText.toFloatOrNull()?.takeIf { it>0f }?:1f; panOffset=Offset(-anchor.x/ratio*g,anchor.y/ratio*g) }
                    Row(Modifier.weight(3f).height(40.dp).zIndex(1f), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.Center) {
                        val ratioTextStyle = MaterialTheme.typography.labelLarge.copy(color=MaterialTheme.colorScheme.onSurface)
                        val ratioKeyboard = KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction=androidx.compose.ui.text.input.ImeAction.Done)
                        val ratioKeyboardActions = KeyboardActions(onDone={ focusManager.clearFocus(); keyboardController?.hide() })
                        @Composable fun RatioInput(value: String, onValueChange: (String) -> Unit, isNumerator: Boolean) {
                            BasicTextField(
                                value=value,
                                onValueChange={ text ->
                                    val numeratorText=if(isNumerator) text else ratioNumeratorInput
                                    val denominatorText=if(isNumerator) ratioDenominatorInput else text
                                    val numerator=numeratorText.toFloatOrNull()?.takeIf { it.isFinite() && it>0f }
                                    val denominator=denominatorText.toFloatOrNull()?.takeIf { it.isFinite() && it>0f }
                                    if(numerator!=null && denominator!=null) changeScaleRatio(denominator/numerator,numeratorText,denominatorText)
                                    onValueChange(text)
                                },
                                modifier=Modifier.weight(1f).height(28.dp)
                                    .border(0.7.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(3.dp))
                                    .padding(horizontal=3.dp, vertical=1.dp),
                                singleLine=true,
                                textStyle=ratioTextStyle,
                                keyboardOptions=ratioKeyboard,
                                keyboardActions=ratioKeyboardActions,
                                decorationBox={ innerTextField -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopStart) { innerTextField() } }
                            )
                        }
                        RatioInput(ratioNumeratorInput,{ ratioNumeratorInput=it },true)
                        Text(":",Modifier.padding(horizontal=3.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurface)
                        RatioInput(ratioDenominatorInput,{ ratioDenominatorInput=it },false)
                    }
                    RibbonToolButton(Icons.Filled.Straighten, "Units", true, Modifier.weight(1f)) { showUnitDialog = true }
                    repeat(4) { Spacer(Modifier.weight(1f)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(xInput, { xInput = it }, label = { Text("X axis ($squareUnit)") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(yInput, { yInput = it }, label = { Text("Y axis ($squareUnit)") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    Button(onClick = {
                        val x = xInput.toFloatOrNull(); val y = yInput.toFloatOrNull()
                        if (x != null && y != null) {
                            val point = GraphPoint(java.util.UUID.randomUUID().toString(), x, y)
                            recordUndo(); points = points + point; selectedPointId = point.id
                            pushData(); xInput = ""; yInput = ""
                        }
                    }, enabled = xInput.toFloatOrNull() != null && yInput.toFloatOrNull() != null, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("Add") }
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
                    .pointerInput(connectMode, pointEditMode, eraserMode, anchorMode, northMode) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            if(anchorMode || northMode) return@detectTransformGestures
                            val oldZoom=zoom; val newZoom=(oldZoom*gestureZoom).coerceIn(0.12f,12f)
                            val center=Offset(size.width/2f,size.height/2f)
                            val centroidLocal=centroid-center
                            val worldAtCentroid=centroidLocal-panOffset
                            panOffset=centroidLocal-worldAtCentroid*(newZoom/oldZoom)+pan
                            zoom=newZoom
                        }
                    }
                    .pointerInput(anchorMode, northMode) {
                        if(anchorMode || northMode) detectDragGestures(
                            onDragStart = { p -> if(anchorMode) anchor=screenToGraph(p,size.width.toFloat(),size.height.toFloat(),panOffset,zoom,GRID_DP.dp.toPx(),scaleRatioText.toFloatOrNull()?.takeIf { it>0f }?:1f) else { val c=Offset(size.width-48.dp.toPx(),48.dp.toPx()); val d=p-c; val m=hypot(d.x.toDouble(),d.y.toDouble()).toFloat().coerceAtLeast(1f); northArrow=d/m } },
                            onDrag = { change, _ -> change.consume(); if(anchorMode) anchor=screenToGraph(change.position,size.width.toFloat(),size.height.toFloat(),panOffset,zoom,GRID_DP.dp.toPx(),scaleRatioText.toFloatOrNull()?.takeIf { it>0f }?:1f) else { val c=Offset(size.width-48.dp.toPx(),48.dp.toPx()); val d=change.position-c; val m=hypot(d.x.toDouble(),d.y.toDouble()).toFloat().coerceAtLeast(1f); northArrow=d/m } }
                        )
                    }
                    .pointerInput(connectMode, pointEditMode, eraserMode, points, zoom, panOffset) {
                        if ((!connectMode && !pointEditMode) || eraserMode) return@pointerInput
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
                                recordUndo(); connections = connections + newConn
                                pushData()
                            }
                            selectedPointId = null
                            dragPreviewFromId = null
                            dragPreviewToId = null
                            dragPreviewCurrent = null
                            dragPreviewCurve = Offset.Zero
                        }
                    }
                    .pointerInput(eraserMode, zoom, panOffset, scaleRatioText, inkStrokes, points, connections) {
                        if (!eraserMode) return@pointerInput
                        val ratio = scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
                        val gridPx = GRID_DP.dp.toPx() * zoom
                        val originX = size.width / 2f + panOffset.x
                        val originY = size.height / 2f + panOffset.y
                        var gesturePoints = emptyList<GraphInkPoint>()
                        fun worldPoint(position: Offset) = GraphInkPoint((position.x - originX) / gridPx * ratio, (originY - position.y) / gridPx * ratio)
                        detectDragGestures(
                            onDragStart = { position -> gesturePoints = listOf(worldPoint(position)); currentInkPoints = gesturePoints },
                            onDrag = { change, _ ->
                                change.consume()
                                gesturePoints = gesturePoints + worldPoint(change.position)
                                currentInkPoints = gesturePoints
                            },
                            onDragEnd = {
                                if (eraserMode && gesturePoints.isNotEmpty()) {
                                    val eraseRadius = 18f * ratio / gridPx
                                    fun nearErase(x: Float, y: Float) = gesturePoints.any { hypot((x - it.x).toDouble(), (y - it.y).toDouble()) <= eraseRadius }
                                    val erasedIds = points.filter { nearErase(it.x, it.y) }.map { it.id }.toSet()
                                    val newStrokes = inkStrokes.filterNot { stroke -> stroke.points.any { nearErase(it.x, it.y) } }
                                    if (erasedIds.isNotEmpty() || newStrokes.size != inkStrokes.size) {
                                        recordUndo()
                                        inkStrokes = newStrokes
                                        points = points.filterNot { it.id in erasedIds }
                                        connections = connections.filterNot { it.fromId in erasedIds || it.toId in erasedIds }
                                        pushData()
                                    }
                                }
                                currentInkPoints = emptyList(); gesturePoints = emptyList()
                            },
                            onDragCancel = { currentInkPoints = emptyList(); gesturePoints = emptyList() }
                        )
                    }
                    .pointerInput(points, inkStrokes, zoom, panOffset, connectMode, pointEditMode, eraserMode, eyedropperMode, scaleRatioText) {
                        if (connectMode || pointEditMode || eraserMode) return@pointerInput
                        detectTapGestures { position ->
                            val gridPx = GRID_DP.dp.toPx() * zoom
                            val ratio = scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
                            val originX = size.width / 2f + panOffset.x
                            val originY = size.height / 2f + panOffset.y
                            val tappedPoint = points.minByOrNull { point ->
                                val px = originX + (point.x / ratio) * gridPx
                                val py = originY - (point.y / ratio) * gridPx
                                hypot((px - position.x).toDouble(), (py - position.y).toDouble())
                            }?.takeIf { point ->
                                val px = originX + (point.x / ratio) * gridPx
                                val py = originY - (point.y / ratio) * gridPx
                                hypot((px - position.x).toDouble(), (py - position.y).toDouble()) <= HIT_RADIUS_PX
                            }
                            if (eyedropperMode) {
                                val sampledPoint = tappedPoint?.let { point -> if (point.color == 0L) pointColor else Color(point.color.toULong()) }
                                val strokeColor = inkStrokes.asSequence().flatMap { stroke -> stroke.points.asSequence().map { stroke to it } }
                                    .map { (stroke, p) ->
                                        val px = originX + (p.x / ratio) * gridPx
                                        val py = originY - (p.y / ratio) * gridPx
                                        stroke to hypot((px - position.x).toDouble(), (py - position.y).toDouble())
                                    }.filter { it.second <= HIT_RADIUS_PX }.minByOrNull { it.second }?.first?.color?.let { Color(it.toULong()) }
                                val sampled = sampledPoint ?: strokeColor
                                if (sampled != null) {
                                    inkColor = sampled
                                    customColorHex = String.format("#%06X", 0xFFFFFF and sampled.toArgb())
                                    eyedropperMode = false
                                }
                            } else {
                                selectedPointId = tappedPoint?.id
                            }
                        }
                    }
            ) {
                val ratio = scaleRatioText.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
                val gridPx = GRID_DP.dp.toPx() * zoom
                val originX = size.width / 2f + panOffset.x
                val originY = size.height / 2f + panOffset.y

                // Clip every label to the drawing surface; negative and positive ticks are generated from visible bounds.
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.clipRect(0f,0f,size.width,size.height)

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
                // y = originY - i*gridPx, so positive and negative tick ranges must be derived in that direction.
                val minYIndex = kotlin.math.floor(((originY-size.height) / gridPx).toDouble()).toInt()
                val maxYIndex = kotlin.math.ceil((originY / gridPx).toDouble()).toInt()
                for (i in minYIndex..maxYIndex) {
                    val y = originY - i * gridPx
                    val (color, width) = lineStyle(i)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), width)
                }

                fun inkPath(strokePoints: List<GraphInkPoint>): Path = Path().apply {
                    strokePoints.forEachIndexed { index, point ->
                        val x = originX + point.x / ratio * gridPx
                        val y = originY - point.y / ratio * gridPx
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                inkStrokes.forEach { stroke -> if (stroke.points.size > 1) drawPath(inkPath(stroke.points), Color(stroke.color.toULong()), style = Stroke(width = stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)) }
                if (currentInkPoints.size > 1) drawPath(inkPath(currentInkPoints), if (eraserMode) eraserPreviewColor else inkColor, style = Stroke(width = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))

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
                    if (p.id == selectedPointId) {
                        val coordinatePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = axisColor.toArgb()
                            textSize = 8.dp.toPx()
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        }
                        drawContext.canvas.nativeCanvas.drawText("(${formatAxisValue(p.x)}, ${formatAxisValue(p.y)})", px.x + 9.dp.toPx(), px.y - 7.dp.toPx(), coordinatePaint)
                    }
                }
                val anchorPx=toPx(GraphPoint("anchor",anchor.x,anchor.y))
                val anchorRadius=5.dp.toPx()
                drawCircle(NothingRed,anchorRadius,anchorPx,style=Stroke(1.dp.toPx()))
                drawLine(NothingRed,anchorPx-Offset(7.dp.toPx(),0f),anchorPx+Offset(7.dp.toPx(),0f),1.dp.toPx())
                drawLine(NothingRed,anchorPx-Offset(0f,7.dp.toPx()),anchorPx+Offset(0f,7.dp.toPx()),1.dp.toPx())
                drawContext.canvas.nativeCanvas.restore()
                // North marker is fixed to the viewport and remains upright as the paper rotates.
                val arrowCenter=Offset(size.width-48.dp.toPx(),48.dp.toPx())
                val tip=arrowCenter+northArrow*20.dp.toPx()
                val wing=Offset(-northArrow.y,northArrow.x)*6.dp.toPx()
                drawLine(NothingRed,arrowCenter,tip,2.dp.toPx())
                val head=Path().apply { moveTo(tip.x,tip.y); lineTo(tip.x-northArrow.x*8.dp.toPx()+wing.x,tip.y-northArrow.y*8.dp.toPx()+wing.y); lineTo(tip.x-northArrow.x*8.dp.toPx()-wing.x,tip.y-northArrow.y*8.dp.toPx()-wing.y); close() }
                drawPath(head,NothingRed)
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
            text = if (eyedropperMode) {
                "Eyedropper active · tap a point or drawn mark to sample its color"
            } else if (connectMode) {
                if (selectedPointId == null) "Tap a point to start a connection" else "Tap (or tap+drag to curve) another point to connect"
            } else if (pointEditMode) {
                if (selectedPointId == null) "Tap a point to select it for recoloring or removal" else "Point selected - choose a color or remove it"
            } else {
                "Pinch to zoom, drag to pan (${(zoom * 100).toInt()}%)"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }

    if (showUnitDialog) {
        AlertDialog(
            onDismissRequest = { showUnitDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Choose Units", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                UnitMenu(value = squareUnit, onChange = ::changeUnit)
            },
            confirmButton = {
                TextButton(onClick = { showUnitDialog = false }) {
                    Text("Done", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (showColorDialog) {
        val palette = listOf(
            Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
            Color(0xFF039BE5), Color(0xFF00ACC1), Color(0xFF00897B), Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFC0CA33),
            Color(0xFFFDD835), Color(0xFFFFB300), Color(0xFFFB8C00), Color(0xFFF4511E), Color(0xFF6D4C41), Color(0xFF757575),
            Color(0xFF000000), Color(0xFF424242), Color(0xFF9E9E9E), Color(0xFFFFFFFF), Color(0xFF90CAF9), Color(0xFFA5D6A7)
        )
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Color panel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = customColorHex, onValueChange = { customColorHex = it }, label = { Text("Color hex") }, singleLine = true)
                    palette.chunked(6).forEach { rowColors ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowColors.forEach { color ->
                                Box(Modifier.size(30.dp).background(color, RoundedCornerShape(50)).clickable { customColorHex = String.format("#%06X", 0xFFFFFF and color.toArgb()) }.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val color = android.graphics.Color.parseColor(customColorHex)
                        val pickedColor = Color(color)
                        selectedPointId?.let { id -> recordUndo(); points = points.map { if (it.id == id) it.copy(color = pickedColor.value.toLong()) else it } }
                        inkColor = pickedColor
                        pushData()
                        showColorDialog = false
                    } catch (_: IllegalArgumentException) { }
                }) { Text("Apply") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showColorDialog = false; eyedropperMode = true }) {
                        Icon(Icons.Filled.Colorize, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Eyedropper")
                    }
                    TextButton(onClick = { showColorDialog = false }) { Text("Cancel") }
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
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp), colors = ButtonDefaults.textButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), maxLines = 1)
        }
    }
}

private fun formatAxisValue(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

private fun screenToGraph(position: Offset,width: Float,height: Float,pan: Offset,zoom: Float,grid: Float,ratio: Float): Offset {
    val center=Offset(width/2f,height/2f)
    val local=position-center-pan
    val pixelsPerSquare=grid*zoom
    return Offset(local.x/pixelsPerSquare*ratio,-local.y/pixelsPerSquare*ratio)
}

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
