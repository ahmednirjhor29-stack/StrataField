package com.stratafield.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.stratafield.app.ui.theme.NothingRed

data class MatrixData(
    val cells: List<String> = List(9) { "" },
    val connections: List<List<Int>> = emptyList(),
    val columns: Int = 3
)

@Composable
fun StratigraphicMatrixTool(modifier: Modifier = Modifier, initialValue: String = "", onChange: (String) -> Unit = {}) {
    var matrix by remember(initialValue) { mutableStateOf(deserializeMatrix(initialValue)) }
    var selectedCell by remember { mutableStateOf<Int?>(null) }
    fun update(value: MatrixData) { matrix = value; onChange(serializeMatrix(value)) }
    val columns = matrix.columns.coerceIn(1, 12)
    val rows = ((matrix.cells.size + columns - 1) / columns).coerceAtLeast(1)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tap and hold a box to select it; hold another box to connect corners", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { update(matrix.copy(cells = matrix.cells + "")) }) { Icon(Icons.Default.Add, null); Text("Add box") }
            TextButton(onClick = { update(matrix.copy(columns = (columns + 1).coerceAtMost(12), cells = matrix.cells + "")) }) { Text("Add column") }
            IconButton(onClick = { update(MatrixData()); selectedCell = null }) { Icon(Icons.Default.Clear, "Clear", tint = NothingRed) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 180.dp).aspectRatio((columns.toFloat() / rows).coerceIn(0.55f, 3.5f))) {
            val gap = 8.dp
            val cellWidth = ((maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(36.dp)
            val cellHeight = ((maxHeight - gap * (rows - 1)) / rows).coerceAtLeast(48.dp)
            val lineColor = MaterialTheme.colorScheme.onSurface
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    fun center(i: Int): Offset {
                        val row = i / columns; val col = i % columns
                        return Offset(col * (cellWidth + gap).toPx() + cellWidth.toPx()/2, row * (cellHeight + gap).toPx() + cellHeight.toPx()/2)
                    }
                    fun edge(from: Int, toward: Int): Offset {
                        val a = center(from); val b = center(toward)
                        val dx = (b.x - a.x).compareTo(0f).toFloat()
                        val dy = (b.y - a.y).compareTo(0f).toFloat()
                        return Offset(a.x + dx * cellWidth.toPx() / 2, a.y + dy * cellHeight.toPx() / 2)
                    }
                    matrix.connections.forEach { pair -> if (pair.size == 2 && pair.all { it in matrix.cells.indices }) drawLine(lineColor, edge(pair[0], pair[1]), edge(pair[1], pair[0]), 3f) }
                }
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(rows) { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(columns) { col ->
                                val index = row * columns + col
                                if (index < matrix.cells.size) {
                                    Column(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).border(if (selectedCell == index) 2.dp else 1.dp, if (selectedCell == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).pointerInput(index) { detectTapGestures(onLongPress = {
                                        val current = selectedCell
                                        if (current == null || current == index) selectedCell = if (current == index) null else index
                                        else {
                                            val pair = listOf(minOf(current, index), maxOf(current, index))
                                            val newLines = if (matrix.connections.any { it == pair }) matrix.connections.filterNot { it == pair } else matrix.connections + listOf(pair)
                                            update(matrix.copy(connections = newLines)); selectedCell = null
                                        }
                                    }) }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        BasicTextField(value = matrix.cells[index], onValueChange = { text -> val cells = matrix.cells.toMutableList(); cells[index] = text; update(matrix.copy(cells = cells)) }, textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(), cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
                                    }
                                } else Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun serializeMatrix(data: MatrixData): String = Gson().toJson(data)
fun deserializeMatrix(json: String): MatrixData = try { if (json.isBlank()) MatrixData() else Gson().fromJson(json, MatrixData::class.java) ?: MatrixData() } catch (_: Exception) { MatrixData() }
