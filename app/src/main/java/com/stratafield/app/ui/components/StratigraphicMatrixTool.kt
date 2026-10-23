package com.stratafield.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
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
    val density = LocalDensity.current.density
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
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

        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min=180.dp).height(720.dp).clipToBounds()) {
            val contentWidth=cellWidth*cols+gapX*(cols-1)
            val contentHeight=cellHeight*rows+gapY*(rows-1)
            val viewportWidthPx = constraints.maxWidth.toFloat()
            val viewportHeightPx = constraints.maxHeight.toFloat()
            val routes=remember(matrix,grid,rows,cols,cellWidth,cellHeight,gapX,gapY) {
                val routed=linkedMapOf<List<Int>,List<Offset>>()
                val prior=mutableListOf<List<Offset>>()
                matrix.connections.forEach { pair ->
                    val route=if(pair.size==2 && pair.all { it in grid.indices && grid[it]!=MATRIX_HOLE })
                        matrixConnectionRoute(pair[0],pair[1],grid,rows,cols,cellWidth.value,cellHeight.value,gapX.value,gapY.value,3f/density,prior)
                    else emptyList()
                    routed[pair]=route
                    if(route.size>=2) prior.add(route)
                }
                routed
            }
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val oldZoom = zoom
                    val newZoom = (oldZoom * zoomChange).coerceIn(0.4f, 5f)
                    val center = Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
                    val focal = centroid - center
                    pan = focal - (focal - pan) * (newZoom / oldZoom) + panChange
                    zoom = newZoom
                }
            }) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    scaleX = zoom; scaleY = zoom
                    translationX = pan.x; translationY = pan.y
                }) {
                Box(Modifier.align(Alignment.Center).width(contentWidth).height(contentHeight)) {
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
                                SideAddButton(Modifier.offset(x=x-32.dp,y=y-2.dp),"Add square to left") { addBeside(index,0) }
                                SideAddButton(Modifier.offset(x=x+cellWidth-8.dp,y=y-2.dp),"Add square to right") { addBeside(index,1) }
                                SideAddButton(Modifier.offset(x=x+14.dp,y=y-32.dp),"Add square above") { addBeside(index,2) }
                                SideAddButton(Modifier.offset(x=x+14.dp,y=y+cellHeight-8.dp),"Add square below") { addBeside(index,3) }
                            }
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
    IconButton(onClick=onClick,modifier=modifier.size(40.dp)) {
        Icon(Icons.Default.Add,description,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
    }
}

private fun matrixConnectionRoute(a:Int,b:Int,cells:List<String>,rows:Int,cols:Int,cw:Float,ch:Float,gx:Float,gy:Float,clearance:Float,priorRoutes:List<List<Offset>>):List<Offset> {
    if(a !in cells.indices || b !in cells.indices || a==b) return emptyList()
    data class Port(val anchor:Offset,val outside:Offset)
    fun ports(index:Int):List<Port> {
        val r=index/cols; val c=index%cols; val left=c*(cw+gx); val top=r*(ch+gy)
        val mx=left+cw/2f; val my=top+ch/2f
        // Every connection starts and ends at a wall midpoint, then leaves the wall normally.
        return listOf(
            Port(Offset(left,my),Offset(left-clearance,my)),
            Port(Offset(left+cw,my),Offset(left+cw+clearance,my)),
            Port(Offset(mx,top),Offset(mx,top-clearance)),
            Port(Offset(mx,top+ch),Offset(mx,top+ch+clearance))
        )
    }
    val startPorts=ports(a); val endPorts=ports(b)
    val xs=sortedSetOf<Float>(); val ys=sortedSetOf<Float>()
    for(i in cells.indices) if(cells[i]!=MATRIX_HOLE) {
        val row=i/cols; val col=i%cols
        val left=col*(cw+gx); val top=row*(ch+gy)
        xs.add(left); xs.add(left+cw/2); xs.add(left+cw)
        xs.add(left-clearance); xs.add(left+cw+clearance)
        ys.add(top); ys.add(top+ch/2); ys.add(top+ch)
        ys.add(top-clearance); ys.add(top+ch+clearance)
    }
    if(xs.isEmpty() || ys.isEmpty()) return emptyList()
    val x=xs.toList(); val y=ys.toList(); val nx=x.size; val ny=y.size; val count=nx*ny
    fun id(ix:Int,iy:Int)=iy*nx+ix
    fun point(node:Int)=Offset(x[node%nx],y[node/nx])
    startPorts.forEach { xs.add(it.outside.x); ys.add(it.outside.y) }
    endPorts.forEach { xs.add(it.outside.x); ys.add(it.outside.y) }
    fun blocked(px:Float,py:Float):Boolean {
        for(index in cells.indices) {
            if(cells[index]==MATRIX_HOLE) continue
            val c=index%cols; val r=index/cols
            val left=c*(cw+gx); val top=r*(ch+gy)
            if(px>left-clearance+0.01f && px<left+cw+clearance-0.01f &&
                py>top-clearance+0.01f && py<top+ch+clearance-0.01f) return true
        }
        return false
    }
    fun segmentConflict(p:Offset,q:Offset):Boolean {
        for(route in priorRoutes) for(i in 0 until route.lastIndex) {
            val u=route[i]; val v=route[i+1]
            val oldHorizontal=kotlin.math.abs(u.y-v.y)<0.01f
            val newHorizontal=kotlin.math.abs(p.y-q.y)<0.01f
            if(oldHorizontal==newHorizontal) {
                if(oldHorizontal) {
                    val overlap=minOf(maxOf(p.x,q.x),maxOf(u.x,v.x))-maxOf(minOf(p.x,q.x),minOf(u.x,v.x))
                    if(overlap>0.01f && kotlin.math.abs(p.y-u.y)<clearance) return true
                } else {
                    val overlap=minOf(maxOf(p.y,q.y),maxOf(u.y,v.y))-maxOf(minOf(p.y,q.y),minOf(u.y,v.y))
                    if(overlap>0.01f && kotlin.math.abs(p.x-u.x)<clearance) return true
                }
            } else {
                val horizontal=if(oldHorizontal) u to v else p to q
                val vertical=if(oldHorizontal) p to q else u to v
                val hx0=minOf(horizontal.first.x,horizontal.second.x); val hx1=maxOf(horizontal.first.x,horizontal.second.x)
                val hy=horizontal.first.y
                val vx=vertical.first.x; val vy0=minOf(vertical.first.y,vertical.second.y); val vy1=maxOf(vertical.first.y,vertical.second.y)
                val intersects=vx in hx0..hx1 && hy in vy0..vy1
                if(!intersects) {
                    val dx=when { vx<hx0 -> hx0-vx; vx>hx1 -> vx-hx1; else -> 0f }
                    val dy=when { hy<vy0 -> vy0-hy; hy>vy1 -> hy-vy1; else -> 0f }
                    if(kotlin.math.hypot(dx.toDouble(),dy.toDouble())<clearance) return true
                }
            }
            // Perpendicular intersections are allowed; parallel overlap and near passes are not.
        }
        return false
    }
    val free=BooleanArray(count) { n -> val p=point(n); !blocked(p.x,p.y) }
    val adjacency=Array(count) { mutableListOf<Pair<Int,Float>>() }
    for(iy in 0 until ny) for(ix in 0 until nx) {
        val n=id(ix,iy); if(!free[n]) continue
        if(ix+1<nx) {
            val next=id(ix+1,iy); val midX=(x[ix]+x[ix+1])/2f
            if(free[next] && !blocked(midX,y[iy]) && !segmentConflict(point(n),point(next))) {
                val cost=x[ix+1]-x[ix]; adjacency[n].add(next to cost); adjacency[next].add(n to cost)
            }
        }
        if(iy+1<ny) {
            val next=id(ix,iy+1); val midY=(y[iy]+y[iy+1])/2f
            if(free[next] && !blocked(x[ix],midY) && !segmentConflict(point(n),point(next))) {
                val cost=y[iy+1]-y[iy]; adjacency[n].add(next to cost); adjacency[next].add(n to cost)
            }
        }
    }
    fun nodeAt(p:Offset):Int { val ix=x.indexOf(p.x); val iy=y.indexOf(p.y); return if(ix<0 || iy<0) -1 else id(ix,iy) }
    val startNodes=startPorts.associate { nodeAt(it.outside) to it }.filterKeys { it>=0 }
    val goalNodes=endPorts.associate { nodeAt(it.outside) to it }.filterKeys { it>=0 }
    val starts=startNodes.keys; val goals=goalNodes.keys
    if(starts.isEmpty() || goals.isEmpty()) return emptyList()
    val distance=FloatArray(count) { Float.POSITIVE_INFINITY }; val previous=IntArray(count) { -1 }
    val queue=PriorityQueue<Pair<Int,Float>>(compareBy { it.second })
    starts.filter { free[it] }.forEach { distance[it]=0f; queue.add(it to 0f) }
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
    val startPort=startNodes[route.first().let { id(x.indexOf(it.x),y.indexOf(it.y)) }] ?: return emptyList()
    val endPort=goalNodes[found] ?: return emptyList()
    route.add(0,startPort.anchor)
    route.add(endPort.anchor)
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
