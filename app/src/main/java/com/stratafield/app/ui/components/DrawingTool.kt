package com.stratafield.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stratafield.app.ui.theme.*

data class StrokePoint(
    val x: Float,
    val y: Float
)

data class StrokeData(
    val points: List<StrokePoint>,
    val color: Long,
    val width: Float
)

@Composable
fun DrawingTool(
    modifier: Modifier = Modifier,
    initialStrokes: List<StrokeData> = emptyList(),
    backgroundType: String = "plain", // plain, graph, grid
    onStrokesChange: (List<StrokeData>) -> Unit = {}
) {
    val strokesSaver: Saver<List<StrokeData>, String> = remember {
        Saver(
            save = { serializeStrokes(it) },
            restore = { deserializeStrokes(it) }
        )
    }
    val colorSaver: Saver<Color, Long> = remember {
        Saver(save = { it.value.toLong() }, restore = { Color(it.toULong()) })
    }
    var strokes by rememberSaveable(stateSaver = strokesSaver) { mutableStateOf(initialStrokes) }
    var currentStroke by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var selectedColor by rememberSaveable(stateSaver = colorSaver) { mutableStateOf(NothingBlack) }
    var strokeWidth by rememberSaveable { mutableStateOf(3f) }
    var tool by rememberSaveable { mutableStateOf("pen") } // pen, eraser, highlighter
    var showColorPicker by remember { mutableStateOf(false) }
    var colorHex by remember { mutableStateOf("#111111") }
    val canvasBackgroundColor = MaterialTheme.colorScheme.surface

    Column(modifier = modifier) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tools
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolButton(
                    icon = Icons.Filled.Edit,
                    selected = tool == "pen",
                    onClick = { tool = "pen" }
                )
                ToolButton(
                    icon = Icons.Filled.Backspace,
                    selected = tool == "eraser",
                    onClick = { tool = "eraser" }
                )
            }

            Button(onClick = { colorHex = String.format("#%06X", 0xFFFFFF and selectedColor.toArgb()); showColorPicker = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Text("Color", color = selectedColor)
            }

            // Clear
            IconButton(
                onClick = {
                    strokes = emptyList()
                    onStrokesChange(strokes)
                }
            ) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = NothingRed)
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
            val gridLineColor = MaterialTheme.colorScheme.outline
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tool, strokeWidth, selectedColor) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = listOf(StrokePoint(offset.x, offset.y))
                            },
                            onDrag = { change, _ ->
                                val point = StrokePoint(change.position.x, change.position.y)
                                currentStroke = currentStroke + point
                                if (tool == "eraser") {
                                    val radius = 20.dp.toPx()
                                    strokes = strokes.flatMap { stroke ->
                                        val segments = mutableListOf<List<StrokePoint>>()
                                        var segment = mutableListOf<StrokePoint>()
                                        stroke.points.forEach { existing ->
                                            val dx = existing.x - point.x
                                            val dy = existing.y - point.y
                                            if (dx * dx + dy * dy < radius * radius) {
                                                if (segment.size > 1) segments.add(segment.toList())
                                                segment = mutableListOf()
                                            } else segment.add(existing)
                                        }
                                        if (segment.size > 1) segments.add(segment.toList())
                                        segments.map { StrokeData(it, stroke.color, stroke.width) }
                                    }
                                    onStrokesChange(strokes)
                                }
                            },
                            onDragEnd = {
                                if (tool != "eraser" && currentStroke.isNotEmpty()) {
                                    val newStroke = StrokeData(
                                        points = currentStroke,
                                        color = selectedColor.value.toLong(),
                                        width = if (tool == "highlighter") strokeWidth * 3 else strokeWidth
                                    )
                                    strokes = strokes + newStroke
                                    onStrokesChange(strokes)
                                    currentStroke = emptyList()
                                }
                            }
                        )
                    }
            ) {
                // Draw background grid
                if (backgroundType == "graph" || backgroundType == "grid") {
                    val gridSize = 20.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = gridLineColor.copy(alpha = 0.3f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += gridSize
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = gridLineColor.copy(alpha = 0.3f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += gridSize
                    }
                }

                // Draw existing strokes
                strokes.forEach { stroke ->
                    if (stroke.points.size > 1) {
                        val path = Path()
                        path.moveTo(stroke.points[0].x, stroke.points[0].y)
                        stroke.points.drop(1).forEach { point ->
                            path.lineTo(point.x, point.y)
                        }
                        drawPath(
                            path = path,
                            color = Color(stroke.color),
                            style = Stroke(
                                width = stroke.width.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                // Draw current stroke
                if (currentStroke.size > 1 && tool != "eraser") {
                    val path = Path()
                    path.moveTo(currentStroke[0].x, currentStroke[0].y)
                    currentStroke.drop(1).forEach { point ->
                        path.lineTo(point.x, point.y)
                    }
                    drawPath(
                        path = path,
                        color = if (tool == "eraser") canvasBackgroundColor else selectedColor,
                        style = Stroke(
                            width = if (tool == "highlighter") strokeWidth * 3 else strokeWidth,
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        }

        // Width control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Width",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = strokeWidth,
                onValueChange = { strokeWidth = it },
                valueRange = 1f..10f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                )
            )
            Text(
                text = "${strokeWidth.toInt()}px",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showColorPicker) AlertDialog(
        onDismissRequest = { showColorPicker = false },
        title = { Text("Custom drawing color") },
        text = { OutlinedTextField(value = colorHex, onValueChange = { colorHex = it }, label = { Text("Hex color (example #3478DB)") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { try { selectedColor = Color(android.graphics.Color.parseColor(colorHex)); showColorPicker = false } catch (_: IllegalArgumentException) { } }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = { showColorPicker = false }) { Text("Cancel") } }
    )
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ColorButton(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color, RoundedCornerShape(6.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
    )
}

// Serialization helpers
fun serializeStrokes(strokes: List<StrokeData>): String {
    return Gson().toJson(strokes)
}

fun deserializeStrokes(json: String): List<StrokeData> {
    return try {
        val type = object : TypeToken<List<StrokeData>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        emptyList()
    }
}
