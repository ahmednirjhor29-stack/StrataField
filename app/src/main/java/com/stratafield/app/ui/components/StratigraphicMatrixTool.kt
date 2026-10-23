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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.stratafield.app.ui.theme.NothingRed
import java.util.PriorityQueue

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
            val routes=remember(matrix,grid,rows,cols,cellWidth,cellHeight,gapX,gapY) {
                matrix.connections.associateWith { pair ->
                    if(pair.size==2 && pair.all { it in grid.indices && grid[it]!=MATRIX_HOLE })
                        matrixConnectionRoute(pair[0],pair[1],grid,rows,cols,cellWidth.value,cellHeight.value,gapX.value,gapY.value)
                    else emptyList()
                }
            }
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll).verticalScroll(vScroll)) {
                Box(Modifier.width(contentWidth).height(contentHeight)) {
                    val lineColor=MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(Modifier.fillMaxSize()) {
                        matrix.connections.forEach { pair ->
                            if(pair.size==2 && pair.all { it in grid.indices && grid[it]!=MATRIX_HOLE }) {
                                val route=orthogonalizeRoute(routes[pair].orEmpty().map { Offset(it.x*density,it.y*density) })
                                if(route.size>=2) {
                                    val path=Path().apply { moveTo(route.first().x,route.first().y); route.drop(1).forEach { lineTo(it.x,it.y) } }
                                    drawPath(path,lineColor,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Butt,join=androidx.compose.ui.graphics.StrokeJoin.Miter))
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

/** Inserts an elbow wherever a route segment is diagonal, so every connector is strictly Manhattan. */
private fun orthogonalizeRoute(route: List<Offset>): List<Offset> {
    if(route.size<2) return route
    val result=ArrayList<Offset>(route.size*2)
    result.add(route.first())
    for(next in route.drop(1)) {
        val previous=result.last()
        val dx=next.x-previous.x; val dy=next.y-previous.y
        if(kotlin.math.abs(dx)>0.01f && kotlin.math.abs(dy)>0.01f) result.add(Offset(next.x,previous.y))
        if(kotlin.math.abs(next.x-result.last().x)>0.01f || kotlin.math.abs(next.y-result.last().y)>0.01f) result.add(next)
    }
    return result
}

@Composable
private fun SideAddButton(modifier: Modifier=Modifier,description: String,onClick:()->Unit) {
    IconButton(onClick=onClick,modifier=modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(50))) {
        Icon(Icons.Default.Add,description,tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(16.dp))
    }
}

private fun matrixConnectionRoute(a:Int,b:Int,cells:List<String>,rows:Int,cols:Int,cw:Float,ch:Float,gx:Float,gy:Float):List<Offset> {
    if(a !in cells.indices || b !in cells.indices || a==b) return emptyList()
    val xs=sortedSetOf<Float>(); val ys=sortedSetOf<Float>()
    for(i in cells.indices) if(cells[i]!=MATRIX_HOLE) {
        val row=i/cols; val col=i%cols
        val left=col*(cw+gx); val top=row*(ch+gy)
        xs.add(left); xs.add(left+cw/2); xs.add(left+cw)
        ys.add(top); ys.add(top+ch/2); ys.add(top+ch)
    }
    if(xs.isEmpty() || ys.isEmpty()) return emptyList()
    val x=xs.toList(); val y=ys.toList(); val nx=x.size; val ny=y.size; val count=nx*ny
    fun id(ix:Int,iy:Int)=iy*nx+ix
    fun point(node:Int)=Offset(x[node%nx],y[node/nx])
    fun blocked(px:Float,py:Float):Boolean {
        val c=kotlin.math.floor(px/(cw+gx)).toInt(); val r=kotlin.math.floor(py/(ch+gy)).toInt()
        if(r !in 0 until rows || c !in 0 until cols) return false
        if(cells.getOrNull(r*cols+c)==MATRIX_HOLE) return false
        val left=c*(cw+gx); val top=r*(ch+gy)
        return px>left+0.01f && px<left+cw-0.01f && py>top+0.01f && py<top+ch-0.01f
    }
    val free=BooleanArray(count) { n -> val p=point(n); !blocked(p.x,p.y) }
    val adjacency=Array(count) { mutableListOf<Pair<Int,Float>>() }
    for(iy in 0 until ny) for(ix in 0 until nx) {
        val n=id(ix,iy); if(!free[n]) continue
        if(ix+1<nx) {
            val next=id(ix+1,iy); val midX=(x[ix]+x[ix+1])/2f
            if(free[next] && !blocked(midX,y[iy])) {
                val cost=x[ix+1]-x[ix]; adjacency[n].add(next to cost); adjacency[next].add(n to cost)
            }
        }
        if(iy+1<ny) {
            val next=id(ix,iy+1); val midY=(y[iy]+y[iy+1])/2f
            if(free[next] && !blocked(x[ix],midY)) {
                val cost=y[iy+1]-y[iy]; adjacency[n].add(next to cost); adjacency[next].add(n to cost)
            }
        }
    }
    fun boundaryNodes(index:Int):List<Int> {
        val r=index/cols; val c=index%cols; val l=c*(cw+gx); val t=r*(ch+gy)
        val xi=listOf(x.indexOf(l),x.indexOf(l+cw/2),x.indexOf(l+cw)).filter { it>=0 }
        val yi=listOf(y.indexOf(t),y.indexOf(t+ch/2),y.indexOf(t+ch)).filter { it>=0 }
        return buildList {
            yi.forEach { iy -> xi.forEach { ix -> val n=id(ix,iy); if(free[n]) add(n) } }
        }
    }
    val starts=boundaryNodes(a); val goals=boundaryNodes(b).toSet()
    if(starts.isEmpty() || goals.isEmpty()) return emptyList()
    val distance=FloatArray(count) { Float.POSITIVE_INFINITY }; val previous=IntArray(count) { -1 }
    val queue=PriorityQueue<Pair<Int,Float>>(compareBy { it.second })
    starts.forEach { distance[it]=0f; queue.add(it to 0f) }
    var found=-1
    while(queue.isNotEmpty()) {
        val (current,d)=queue.remove(); if(d!=distance[current]) continue
        if(current in goals) { found=current; break }
        for((next,cost) in adjacency[current]) {
            val candidate=d+cost
            if(candidate<distance[next]) { distance[next]=candidate; previous[next]=current; queue.add(next to candidate) }
        }
    }
    if(found<0) return emptyList()
    val route=mutableListOf<Offset>(); var node=found
    while(node>=0) { route.add(point(node)); node=previous[node] }
    route.reverse()
    // Drop collinear vertices to keep rendering and persistence lightweight.
    if(route.size<3) return route
    val simplified=mutableListOf(route.first())
    for(i in 1 until route.lastIndex) {
        val p=simplified.last(); val c=route[i]; val n=route[i+1]
        if(kotlin.math.abs((c.x-p.x)*(n.y-c.y)-(c.y-p.y)*(n.x-c.x))>0.01f) simplified.add(c)
    }
    simplified.add(route.last())
    return simplified
}

fun serializeMatrix(data: MatrixData): String = Gson().toJson(data)
fun deserializeMatrix(json: String): MatrixData = try { if(json.isBlank()) MatrixData() else Gson().fromJson(json,MatrixData::class.java) ?: MatrixData() } catch(_:Exception) { MatrixData() }
