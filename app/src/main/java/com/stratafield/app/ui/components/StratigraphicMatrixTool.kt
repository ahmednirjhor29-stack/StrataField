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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
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
    val undo = remember { mutableStateListOf<MatrixData>() }; val redo = remember { mutableStateListOf<MatrixData>() }
    var editCell by remember { mutableStateOf<Int?>(null) }; var editText by remember { mutableStateOf("") }
    fun commit(value: MatrixData) { undo.add(matrix); redo.clear(); matrix = value; onChange(serializeMatrix(value)) }
    fun update(value: MatrixData) { commit(value) }
    val columns = matrix.columns.coerceIn(1, 12)
    val rows = ((matrix.cells.size + columns - 1) / columns).coerceAtLeast(1)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { undo.lastOrNull()?.let { redo.add(matrix); matrix=undo.removeAt(undo.lastIndex); onChange(serializeMatrix(matrix)) } }, enabled=undo.isNotEmpty()) { Icon(Icons.Default.Undo,"Undo") }
            IconButton(onClick = { redo.lastOrNull()?.let { undo.add(matrix); matrix=redo.removeAt(redo.lastIndex); onChange(serializeMatrix(matrix)) } }, enabled=redo.isNotEmpty()) { Icon(Icons.Default.Redo,"Redo") }
            IconButton(onClick = { commit(MatrixData()); selectedCell = null }) { Icon(Icons.Default.Clear, "Clear", tint = NothingRed) }
            Spacer(Modifier.weight(1f))
            selectedCell?.let { i -> IconButton(onClick={ editCell=i; editText=matrix.cells.getOrElse(i){""} }) { Icon(Icons.Default.Edit,"Edit selected box text") } }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 180.dp).aspectRatio((columns.toFloat() / rows).coerceIn(0.55f, 3.5f))) {
            val gap = 8.dp
            val cellWidth = ((maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(36.dp)
            val cellHeight = ((maxHeight - gap * (rows - 1)) / rows).coerceAtLeast(42.dp)
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
                        val halfW=cellWidth.toPx()/2; val halfH=cellHeight.toPx()/2
                        val t = 1f / maxOf(kotlin.math.abs(dx)/halfW, kotlin.math.abs(dy)/halfH, 0.0001f)
                        return Offset(a.x + dx*t, a.y + dy*t)
                    }
                    matrix.connections.forEach { pair -> if (pair.size == 2 && pair.all { it in matrix.cells.indices }) drawLine(lineColor, edge(pair[0], pair[1]), edge(pair[1], pair[0]), 3f) }
                }
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(rows) { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(columns) { col ->
                                val index = row * columns + col
                                if (index < matrix.cells.size) {
                                    Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).border(if (selectedCell == index) 2.dp else 1.dp, if (selectedCell == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).clickable {
                                        val current = selectedCell
                                        if (current == null || current == index) selectedCell = index
                                        else {
                                            val pair = listOf(minOf(current, index), maxOf(current, index))
                                            val newLines = if (matrix.connections.any { it == pair }) matrix.connections.filterNot { it == pair } else matrix.connections + listOf(pair)
                                            update(matrix.copy(connections = newLines)); selectedCell = null
                                        }
                                    }.padding(5.dp), contentAlignment=Alignment.Center) {
                                        Text(matrix.cells[index], style=MaterialTheme.typography.bodySmall.copy(fontWeight=FontWeight.Medium,textAlign=TextAlign.Center), modifier=Modifier.fillMaxWidth())
                                        if (selectedCell==index) IconButton(onClick={ commit(matrix.copy(cells=matrix.cells+"")); selectedCell=matrix.cells.lastIndex }, modifier=Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Default.Add,"Add box", modifier=Modifier.size(16.dp)) }
                                    }
                                } else Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    editCell?.let { index -> AlertDialog(onDismissRequest={editCell=null}, title={Text("Edit box text")}, text={OutlinedTextField(editText,{editText=it},singleLine=false)}, confirmButton={TextButton(onClick={ val cells=matrix.cells.toMutableList(); cells[index]=editText; commit(matrix.copy(cells=cells)); editCell=null }) { Text("Save") }}, dismissButton={TextButton(onClick={editCell=null}) { Text("Cancel") }}) }
}

fun serializeMatrix(data: MatrixData): String = Gson().toJson(data)
fun deserializeMatrix(json: String): MatrixData = try { if (json.isBlank()) MatrixData() else Gson().fromJson(json, MatrixData::class.java) ?: MatrixData() } catch (_: Exception) { MatrixData() }
