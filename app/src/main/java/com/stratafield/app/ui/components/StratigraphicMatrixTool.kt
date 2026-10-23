package com.stratafield.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.stratafield.app.ui.theme.NothingRed
import java.util.ArrayDeque

private const val MATRIX_HOLE = "\u0000"
private const val MAX_MATRIX_SIZE = 20

data class MatrixData(
    val cells: List<String> = List(9) { "" },
    val connections: List<List<Int>> = emptyList(),
    val columns: Int = 3
)

private data class MatrixSnapshot(val matrix: MatrixData)

@Composable
fun StratigraphicMatrixTool(modifier: Modifier = Modifier, initialValue: String = "", onChange: (String) -> Unit = {}) {
    var matrix by remember(initialValue) { mutableStateOf(deserializeMatrix(initialValue)) }
    var selectedCell by remember { mutableStateOf<Int?>(null) }
    var removeMode by remember { mutableStateOf(false) }
    var editCell by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    val undo = remember { mutableStateListOf<MatrixSnapshot>() }
    val redo = remember { mutableStateListOf<MatrixSnapshot>() }
    val cols = matrix.columns.coerceIn(1, MAX_MATRIX_SIZE)
    val rows = ((matrix.cells.size + cols - 1) / cols).coerceIn(1, MAX_MATRIX_SIZE)
    val grid = remember(matrix, cols, rows) {
        List(rows * cols) { i -> matrix.cells.getOrNull(i) ?: MATRIX_HOLE }
    }
    val cellWidth = 68.dp
    val cellHeight = 36.dp // three times the 12sp box text
    val gapX = 28.dp
    val gapY = 28.dp

    fun commit(next: MatrixData) {
        undo.add(MatrixSnapshot(matrix))
        redo.clear()
        matrix = next
        onChange(serializeMatrix(next))
    }
    fun undoAction() {
        val previous = undo.removeLastOrNull() ?: return
        redo.add(MatrixSnapshot(matrix)); matrix = previous.matrix
        selectedCell = null; onChange(serializeMatrix(matrix))
    }
    fun redoAction() {
        val next = redo.removeLastOrNull() ?: return
        undo.add(MatrixSnapshot(matrix)); matrix = next.matrix
        selectedCell = null; onChange(serializeMatrix(matrix))
    }
    fun indexMapForInsertion(insertRow: Int?, insertColumn: Int?): (Int) -> Int {
        val newCols = cols + if (insertColumn != null) 1 else 0
        return { old ->
            val r=old/cols; val c=old%cols
            val nr=if(insertRow!=null && r>=insertRow) r+1 else r
            val nc=if(insertColumn!=null && c>=insertColumn) c+1 else c
            nr*newCols+nc
        }
    }
    fun addBeside(index: Int, side: Int) {
        if(index !in grid.indices || grid[index] == MATRIX_HOLE) return
        val row=index/cols; val col=index%cols
        val targetRow=row+if(side==2) -1 else if(side==3) 1 else 0
        val targetCol=col+if(side==0) -1 else if(side==1) 1 else 0
        // Reuse a deleted/empty slot directly adjacent to this box.
        if(targetRow in 0 until rows && targetCol in 0 until cols) {
            val target=targetRow*cols+targetCol
            if(grid[target] == MATRIX_HOLE) {
                val cells=grid.toMutableList(); cells[target]=""
                commit(matrix.copy(cells=cells)); selectedCell=target
                return
            }
        }
        val insertRow = when(side) { 2 -> row; 3 -> row+1; else -> null }
        val insertColumn = when(side) { 0 -> col; 1 -> col+1; else -> null }
        if(insertRow != null && rows >= MAX_MATRIX_SIZE) return
        if(insertColumn != null && cols >= MAX_MATRIX_SIZE) return
        val mapping=indexMapForInsertion(insertRow,insertColumn)
        val newCols=cols+if(insertColumn!=null) 1 else 0
        val newRows=rows+if(insertRow!=null) 1 else 0
        val cells=MutableList(newRows*newCols){ MATRIX_HOLE }
        grid.forEachIndexed { old,value -> cells[mapping(old)]=value }
        val addedRow=insertRow ?: row
        val addedColumn=insertColumn ?: col
        val newIndex=addedRow*newCols+addedColumn
        cells[newIndex]=""
        val links=matrix.connections.mapNotNull { pair ->
            if(pair.size!=2 || pair.any { it !in grid.indices } || pair.any { grid[it]==MATRIX_HOLE }) null
            else pair.map(mapping)
        }
        commit(MatrixData(cells,links,newCols)); selectedCell=newIndex
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=::undoAction,enabled=undo.isNotEmpty()) { Icon(Icons.Default.Undo,"Undo") }
            IconButton(onClick=::redoAction,enabled=redo.isNotEmpty()) { Icon(Icons.Default.Redo,"Redo") }
            IconButton(onClick={ removeMode=!removeMode; selectedCell=null }) {
                Icon(Icons.Default.Delete, if(removeMode) "Finish removing boxes" else "Remove box", tint=if(removeMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick={ commit(MatrixData()); selectedCell=null; removeMode=false }) { Icon(Icons.Default.Clear,"Clear matrix",tint=NothingRed) }
            Spacer(Modifier.weight(1f))
            selectedCell?.let { index ->
                IconButton(onClick={ editCell=index; editText=grid.getOrNull(index)?.takeUnless { it==MATRIX_HOLE } ?: "" }) { Icon(Icons.Default.Edit,"Edit selected box") }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min=180.dp).height(360.dp)) {
            val contentWidth=cellWidth*cols+gapX*(cols-1)
            val contentHeight=cellHeight*rows+gapY*(rows-1)
            val hScroll=rememberScrollState(); val vScroll=rememberScrollState()
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll).verticalScroll(vScroll)) {
                Box(Modifier.width(contentWidth).height(contentHeight)) {
                    val lineColor=MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(Modifier.fillMaxSize()) {
                        matrix.connections.forEach { pair ->
                            if(pair.size==2 && pair.all { it in grid.indices && grid[it]!=MATRIX_HOLE }) {
                                val route=matrixConnectionRoute(pair[0],pair[1],grid,rows,cols,cellWidth.toPx(),cellHeight.toPx(),gapX.toPx(),gapY.toPx())
                                if(route.size>=2) {
                                    val path=Path().apply { moveTo(route.first().x,route.first().y); route.drop(1).forEach { lineTo(it.x,it.y) } }
                                    drawPath(path,lineColor,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                                }
                            }
                        }
                    }
                    grid.forEachIndexed { index, text ->
                        if(text!=MATRIX_HOLE) {
                            val row=index/cols; val col=index%cols
                            val x=(cellWidth+gapX)*col; val y=(cellHeight+gapY)*row
                            val selected=selectedCell==index
                            Box(Modifier.offset(x=x,y=y).size(cellWidth,cellHeight)
                                .background(MaterialTheme.colorScheme.surface,RoundedCornerShape(3.dp))
                                .border(if(selected) 1.5.dp else 1.dp,if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,RoundedCornerShape(3.dp))
                                .pointerInput(index,removeMode,selectedCell,matrix) {
                                    detectTapGestures {
                                        if(removeMode) {
                                            val cells=grid.toMutableList(); cells[index]=MATRIX_HOLE
                                            val links=matrix.connections.filterNot { pair -> pair.size==2 && index in pair }
                                            commit(matrix.copy(cells=cells,connections=links)); selectedCell=null
                                        } else {
                                            val from=selectedCell
                                            if(from==null || from==index) selectedCell=index
                                            else {
                                                val pair=listOf(minOf(from,index),maxOf(from,index))
                                                val links=if(matrix.connections.any { it==pair }) matrix.connections.filterNot { it==pair } else matrix.connections+listOf(pair)
                                                commit(matrix.copy(connections=links)); selectedCell=null
                                            }
                                        }
                                    }
                                },contentAlignment=Alignment.Center) {
                                Text(text,style=MaterialTheme.typography.labelSmall.copy(fontSize=12.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center),maxLines=2)
                            }
                            if(selected) {
                                SideAddButton(Modifier.offset(x=x-24.dp,y=y+6.dp),"Add square to left") { addBeside(index,0) }
                                SideAddButton(Modifier.offset(x=x+cellWidth,y=y+6.dp),"Add square to right") { addBeside(index,1) }
                                SideAddButton(Modifier.offset(x=x+22.dp,y=y-24.dp),"Add square above") { addBeside(index,2) }
                                SideAddButton(Modifier.offset(x=x+22.dp,y=y+cellHeight),"Add square below") { addBeside(index,3) }
                            }
                        }
                    }
                }
            }
        }
        if(removeMode) Text("Tap a box to remove it. Its space will stay empty.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))
    }
    editCell?.let { index ->
        AlertDialog(onDismissRequest={editCell=null},title={Text("Edit box text")},text={OutlinedTextField(editText,{editText=it},singleLine=false)},confirmButton={TextButton(onClick={
            val cells=grid.toMutableList(); cells[index]=editText; commit(matrix.copy(cells=cells)); editCell=null
        }) { Text("Save") }},dismissButton={TextButton(onClick={editCell=null}) { Text("Cancel") }})
    }
}

@Composable
private fun SideAddButton(modifier: Modifier=Modifier,description: String,onClick:()->Unit) {
    IconButton(onClick=onClick,modifier=modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(50))) {
        Icon(Icons.Default.Add,description,tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(16.dp))
    }
}

private fun matrixConnectionRoute(a:Int,b:Int,cells:List<String>,rows:Int,cols:Int,cw:Float,ch:Float,gx:Float,gy:Float):List<Offset> {
    val startR=a/cols; val startC=a%cols; val endR=b/cols; val endC=b%cols
    val directions=listOf(-1 to 0,0 to 1,1 to 0,0 to -1)
    val queue=ArrayDeque<Pair<Int,Int>>()
    val start=startR to startC; val goal=endR to endC
    val parent=mutableMapOf<Pair<Int,Int>,Pair<Int,Int>?>(); parent[start]=null; queue.add(start)
    while(queue.isNotEmpty() && goal !in parent) {
        val (r,c)=queue.removeFirst()
        for((dr,dc) in directions) {
            val nr=r+dr; val nc=c+dc
            if(nr < -1 || nr > rows || nc < -1 || nc > cols) continue
            val next=nr to nc
            if(next in parent) continue
            val inside=nr in 0 until rows && nc in 0 until cols
            val index=nr*cols+nc
            if(inside && next!=goal && next!=start && cells.getOrNull(index)!=MATRIX_HOLE) continue
            parent[next]=r to c; queue.add(next)
        }
    }
    if(goal !in parent) return emptyList()
    val path=mutableListOf<Pair<Int,Int>>(); var cursor:Pair<Int,Int>?=goal
    while(cursor!=null) { path.add(cursor); cursor=parent[cursor] }
    path.reverse()
    fun center(r:Int,c:Int)=Offset(c*(cw+gx)+cw/2,r*(ch+gy)+ch/2)
    fun boundary(boxR:Int,boxC:Int,nextR:Int,nextC:Int):Offset {
        val center=center(boxR,boxC); val dx=nextC-boxC; val dy=nextR-boxR
        return when { dx>0 -> Offset(center.x+cw/2,center.y); dx<0 -> Offset(center.x-cw/2,center.y); dy>0 -> Offset(center.x,center.y+ch/2); else -> Offset(center.x,center.y-ch/2) }
    }
    if(path.size==2) return listOf(boundary(startR,startC,endR,endC),boundary(endR,endC,startR,startC))
    val result=mutableListOf(boundary(startR,startC,path[1].first,path[1].second))
    path.drop(1).dropLast(1).forEach { result.add(center(it.first,it.second)) }
    result.add(boundary(endR,endC,path[path.lastIndex-1].first,path[path.lastIndex-1].second))
    return result
}

fun serializeMatrix(data: MatrixData): String = Gson().toJson(data)
fun deserializeMatrix(json: String): MatrixData = try { if(json.isBlank()) MatrixData() else Gson().fromJson(json,MatrixData::class.java) ?: MatrixData() } catch(_:Exception) { MatrixData() }
