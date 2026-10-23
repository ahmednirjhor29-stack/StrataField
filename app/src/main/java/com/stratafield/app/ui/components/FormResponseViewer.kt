package com.stratafield.app.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.JsonParser
import com.stratafield.app.data.model.DataStatus
import com.stratafield.app.data.model.FieldType
import com.stratafield.app.data.model.FormFieldDefinition
import com.stratafield.app.data.model.FormResponse
import com.stratafield.app.data.model.FormTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private data class ViewerField(
    val id: String,
    val label: String,
    val section: String,
    val type: FieldType?,
    val value: String,
    val visualData: String = ""
)

private fun FieldType?.hasVisualPreview() = this in setOf(FieldType.GRAPH_PAPER, FieldType.MATRIX, FieldType.DRAWING)

@Composable
fun FormResponseViewer(
    response: FormResponse,
    template: FormTemplate?,
    data: Map<String, String>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageIndex by remember(response.id) { mutableIntStateOf(0) }
    val fields = remember(response.id, template, data) { buildViewerFields(template, data) }
    val numericFields = remember(fields) {
        fields.filter { it.type == FieldType.NUMBER && it.value.toDoubleOrNull() != null }
    }
    val pages = remember(fields) { paginateViewerFields(fields) }
    val safePageIndex = pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    val createPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { writeViewerPdf(context, uri, response, fields, pages, numericFields) }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "PDF saved" else "Unable to save PDF: ${result.exceptionOrNull()?.localizedMessage ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pageIndex = (safePageIndex - 1).coerceAtLeast(0) }, enabled = safePageIndex > 0) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
                    }
                    Text("Page ${safePageIndex + 1} of ${pages.size}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { pageIndex = (safePageIndex + 1).coerceAtMost(pages.lastIndex) }, enabled = safePageIndex < pages.lastIndex) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { createPdfLauncher.launch("${safePdfName(response.formName)}.pdf") }) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Create PDF")
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close viewer") }
                }

                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val paperWidth = minOf(maxWidth - 24.dp, (maxHeight - 20.dp) * (595f / 842f))
                    Surface(
                        Modifier.width(paperWidth.coerceAtLeast(100.dp)).aspectRatio(595f / 842f),
                        shape = RoundedCornerShape(3.dp),
                        color = Color(0xFFFCFCFA),
                        shadowElevation = 5.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        ViewerPaperPage(
                            response = response,
                            fields = pages.getOrElse(safePageIndex) { emptyList() },
                            pageIndex = safePageIndex,
                            pageCount = pages.size,
                            numericFields = if (safePageIndex == 0) numericFields else emptyList()
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onEdit, shape = RoundedCornerShape(10.dp)) { Text("Edit Form") }
                }
            }
        }
    }
}

private fun buildViewerFields(template: FormTemplate?, data: Map<String, String>): List<ViewerField> {
    val definitions = runCatching {
        template?.let { com.google.gson.Gson().fromJson(it.fieldsJson, Array<FormFieldDefinition>::class.java)?.toList() }.orEmpty()
    }.getOrDefault(emptyList())
    val orderedIds = definitions.map { it.id }
    val orderedData = data.entries.sortedBy { entry -> orderedIds.indexOf(entry.key).let { if (it < 0) Int.MAX_VALUE else it } }
    return orderedData.mapNotNull { (id, rawValue) ->
        if (rawValue.isBlank()) return@mapNotNull null
        val definition = definitions.firstOrNull { it.id == id }
        val type = definition?.fieldType
        val value = readableViewerValue(rawValue, type)
        (if (type.hasVisualPreview()) listOf(value) else splitLongValue(value)).mapIndexed { index, part ->
            ViewerField(
                id = id,
                label = (definition?.label?.takeIf { it.isNotBlank() } ?: id) + if (index > 0) " (continued)" else "",
                section = definition?.section.orEmpty().ifBlank { "Recorded Details" },
                type = type,
                value = part,
                visualData = if (type.hasVisualPreview()) rawValue else ""
            )
        }
    }.flatten()
}

private fun readableViewerValue(value: String, type: FieldType?): String {
    if (type == FieldType.GPS) {
        val obj = runCatching { JsonParser.parseString(value).takeIf { it.isJsonObject }?.asJsonObject }.getOrNull()
            ?: return value
        val parts = mutableListOf<String>()
        obj.get("siteName")?.asString?.takeIf { it.isNotBlank() }?.let { parts += "Site: $it" }
        obj.get("descriptiveLocation")?.asString?.takeIf { it.isNotBlank() }?.let { parts += "Description: $it" }
        val latitude = obj.get("latitude")?.takeIf { it.isJsonPrimitive }?.asDouble
        val longitude = obj.get("longitude")?.takeIf { it.isJsonPrimitive }?.asDouble
        if (latitude != null) parts += "Latitude: ${String.format(Locale.US, "%.8f° %s", kotlin.math.abs(latitude), if (latitude >= 0) "N" else "S")}"
        if (longitude != null) parts += "Longitude: ${String.format(Locale.US, "%.8f° %s", kotlin.math.abs(longitude), if (longitude >= 0) "E" else "W")}"
        obj.get("gnssElevation")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.let { parts += "GNSS elevation: ${String.format(Locale.US, "%.2f m", it)}" }
        obj.get("seaLevelElevation")?.asString?.takeIf { it.isNotBlank() }?.let { parts += "Sea level elevation: $it" }
        return parts.joinToString("\n").ifBlank { "No location details recorded" }
    }
    if (type == FieldType.GRAPH_PAPER) {
        val obj = runCatching { JsonParser.parseString(value).takeIf { it.isJsonObject }?.asJsonObject }.getOrNull() ?: return value
        val pointCount = obj.getAsJsonArray("points")?.size() ?: 0
        val lineCount = obj.getAsJsonArray("connections")?.size() ?: 0
        val strokeCount = obj.getAsJsonArray("inkStrokes")?.size() ?: 0
        return "$pointCount points · $lineCount connections · $strokeCount freehand strokes"
    }
    if (type == FieldType.MATRIX) {
        val matrix = runCatching { com.google.gson.Gson().fromJson(value, MatrixData::class.java) }.getOrNull() ?: return "Stratigraphic matrix"
        val units = matrix.cells.count { it != "\u0000" }
        return "$units units · ${matrix.connections.size} connections"
    }
    if (type == FieldType.DRAWING) {
        val strokes = runCatching { com.google.gson.Gson().fromJson(value, Array<StrokeData>::class.java) }.getOrNull()
        return "${strokes?.size ?: 0} freehand strokes"
    }
    if (type == FieldType.IMAGE) {
        val obj = runCatching { JsonParser.parseString(value).takeIf { it.isJsonObject }?.asJsonObject }.getOrNull() ?: return value
        val count = obj.getAsJsonArray("uris")?.size() ?: 0
        return "$count attached ${if (count == 1) "image" else "images"}"
    }
    return value
}

private fun splitLongValue(value: String, maxChars: Int = 260): List<String> {
    if (value.length <= maxChars) return listOf(value)
    val result = mutableListOf<String>()
    var remaining = value
    while (remaining.length > maxChars) {
        var cut = remaining.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 } ?: maxChars
        val line = remaining.substring(0, cut).trim()
        result += line
        remaining = remaining.substring(cut).trimStart()
    }
    if (remaining.isNotEmpty()) result += remaining
    return result
}

private fun paginateViewerFields(fields: List<ViewerField>): List<List<ViewerField>> {
    if (fields.isEmpty()) return listOf(emptyList())
    val result = mutableListOf<MutableList<ViewerField>>()
    var page = mutableListOf<ViewerField>()
    var weight = 0
    var capacity = 6
    fields.forEach { field ->
        val fieldWeight = if (field.type.hasVisualPreview()) 4 else maxOf(1, ceil(field.value.length / 110.0).toInt())
        if (page.isNotEmpty() && weight + fieldWeight > capacity) {
            result += page
            page = mutableListOf()
            weight = 0
            capacity = 10
        }
        page += field
        weight += fieldWeight
    }
    if (page.isNotEmpty()) result += page
    return result
}

@Composable
private fun ViewerPaperPage(
    response: FormResponse,
    fields: List<ViewerField>,
    pageIndex: Int,
    pageCount: Int,
    numericFields: List<ViewerField>
) {
    val sections = fields.groupBy { it.section }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("STRATAFIELD  /  ${response.formType.name}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4F6F68), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(response.formName, style = MaterialTheme.typography.titleLarge, color = Color(0xFF172823), fontWeight = FontWeight.Bold)
            }
            Text(response.status.name, style = MaterialTheme.typography.labelSmall, color = Color(0xFF28685A), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text("Recorded ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(response.createdAt))}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF68736E))
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFDDE5E1))
        if (pageIndex == 0) {
            val highlights = fields.filter { it.value.length < 50 }.take(3)
            if (highlights.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    highlights.forEachIndexed { index, field ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (index == 0) Color(0xFF194F44) else Color(0xFF2C6B5D))
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                        ) {
                            Text(field.value, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(field.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFD8E8E2), maxLines = 2)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
            if (numericFields.size >= 2) {
                Text("Measurements", style = MaterialTheme.typography.labelMedium, color = Color(0xFF263C35), fontWeight = FontWeight.SemiBold)
                NumericComparisonChart(numericFields.take(4), Modifier.fillMaxWidth().height(45.dp))
            }
        }
        if (fields.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No recorded field data", color = Color(0xFF68736E))
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                sections.forEach { (section, entries) ->
                    Text(section.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4F6F68), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp, bottom = 1.dp))
                    entries.forEach { field ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(field.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF68736E), fontWeight = FontWeight.SemiBold)
                            if (field.type.hasVisualPreview()) {
                                Text(field.value, style = MaterialTheme.typography.labelSmall, color = Color(0xFF68736E))
                                ViewerVisualPreview(field, Modifier.fillMaxWidth().height(150.dp))
                            } else Text(field.value, style = MaterialTheme.typography.bodySmall, color = Color(0xFF172823))
                            HorizontalDivider(Modifier.padding(top = 4.dp), color = Color(0xFFE5EAE7))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${fields.size} entries", style = MaterialTheme.typography.labelSmall, color = Color(0xFF68736E))
            Text("${pageIndex + 1} / $pageCount", style = MaterialTheme.typography.labelSmall, color = Color(0xFF68736E))
        }
    }
}

@Composable
private fun ViewerVisualPreview(field: ViewerField, modifier: Modifier = Modifier) {
    val graph = remember(field.visualData) { runCatching { deserializeGraphPaper(field.visualData) }.getOrNull() }
    val matrix = remember(field.visualData) { runCatching { deserializeMatrix(field.visualData) }.getOrNull() }
    val strokes = remember(field.visualData) { runCatching { deserializeStrokes(field.visualData) }.getOrDefault(emptyList()) }
    Canvas(modifier.border(1.dp, Color(0xFFDDE5E1), RoundedCornerShape(4.dp)).background(Color.White, RoundedCornerShape(4.dp))) {
        val margin = 30f // keep the drawing inside a clear border in both the viewer and A4 export
        val left = margin; val top = margin
        val width = (size.width - margin * 2).coerceAtLeast(1f)
        val height = (size.height - margin * 2).coerceAtLeast(1f)
        drawRoundRect(Color(0xFFFDFEFD), topLeft = Offset(0f, 0f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f))
        when (field.type) {
            FieldType.GRAPH_PAPER -> graph?.let { data ->
                val all = data.points.map { Offset(it.x, it.y) } + data.inkStrokes.orEmpty().flatMap { s -> s.points.map { Offset(it.x, it.y) } }
                var minX = all.minOfOrNull { it.x } ?: -1f; var maxX = all.maxOfOrNull { it.x } ?: 1f
                var minY = all.minOfOrNull { it.y } ?: -1f; var maxY = all.maxOfOrNull { it.y } ?: 1f
                if (maxX - minX < 0.0001f) { minX -= 1f; maxX += 1f }; if (maxY - minY < 0.0001f) { minY -= 1f; maxY += 1f }
                val scale = min(width / (maxX - minX), height / (maxY - minY))
                val centerX=(minX+maxX)/2f; val centerY=(minY+maxY)/2f
                fun px(x:Float)=left+width/2f+(x-centerX)*scale
                fun py(y:Float)=top+height/2f-(y-centerY)*scale
                val majorUnit=data.scaleRatio.takeIf { it.isFinite() && it>0f }?:1f
                val unit=majorUnit/10f
                var step=unit
                val tickCount=(max((maxX-minX)/step,(maxY-minY)/step)).toInt()
                if(tickCount>80) step*=ceil(tickCount/80f)
                var tick=kotlin.math.floor(minX/step)*step
                while(tick<=maxX && tick.isFinite()) { val x=px(tick); drawLine(Color(0xFFE8EEEB),Offset(x,top),Offset(x,top+height),1f); tick+=step }
                tick=kotlin.math.floor(minY/step)*step
                while(tick<=maxY && tick.isFinite()) { val y=py(tick); drawLine(Color(0xFFE8EEEB),Offset(left,y),Offset(left+width,y),1f); tick+=step }
                if(minY<=0 && maxY>=0) drawLine(Color(0xFF596A63),Offset(left,py(0f)),Offset(left+width,py(0f)),1.5f)
                if(minX<=0 && maxX>=0) drawLine(Color(0xFF596A63),Offset(px(0f),top),Offset(px(0f),top+height),1.5f)
                val pointMap=data.points.associateBy { it.id }
                data.connections.forEach { c ->
                    val a=pointMap[c.fromId]; val b=pointMap[c.toId]
                    if(a!=null && b!=null) drawLine(Color(0xFF40534B),Offset(px(a.x),py(a.y)),Offset(px(b.x),py(b.y)),2f)
                }
                data.inkStrokes.orEmpty().forEach { stroke ->
                    if(stroke.points.size>1) {
                        val path=Path().apply { stroke.points.forEachIndexed { i,p -> if(i==0) moveTo(px(p.x),py(p.y)) else lineTo(px(p.x),py(p.y)) } }
                        drawPath(path,Color(stroke.color.toULong()),style=Stroke((stroke.width*scale).coerceIn(1f,5f)))
                    }
                }
                data.points.forEach { p -> drawCircle(if(p.color==0L) Color(0xFF327D6D) else Color(p.color.toULong()),4f,Offset(px(p.x),py(p.y))) }
                val labelPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(70,85,78); textSize=11f }
                val nc=drawContext.canvas.nativeCanvas
                val yTicks=ceil((maxY-minY)/step).toInt().coerceAtMost(80)
                repeat(yTicks+1) { i -> val value=kotlin.math.floor(minY/step)*step+i*step; if(value<=maxY) nc.drawText(String.format(Locale.US,"%.2f",value),2f,py(value)-2f,labelPaint) }
                val xTicks=ceil((maxX-minX)/step).toInt().coerceAtMost(80)
                repeat(xTicks+1) { i -> val value=kotlin.math.floor(minX/step)*step+i*step; if(value<=maxX) nc.drawText(String.format(Locale.US,"%.2f",value),px(value)+2f,size.height-3f,labelPaint) }
            }
            FieldType.MATRIX -> matrix?.let { data ->
                val cols=data.columns.coerceIn(1,20); val rows=((data.cells.size+cols-1)/cols).coerceIn(1,20)
                val cells=List(rows*cols) { i -> data.cells.getOrNull(i) ?: "\u0000" }
                val cw=68f; val ch=36f; val gx=28f; val gy=28f
                val contentW=cw*cols+gx*(cols-1); val contentH=ch*rows+gy*(rows-1)
                val scale=min(width/contentW.coerceAtLeast(1f),height/contentH.coerceAtLeast(1f))
                val ox=left+(width-contentW*scale)/2f; val oy=top+(height-contentH*scale)/2f
                val valid=data.connections.mapNotNull { pair -> if(pair.size==2&&pair.all { it in cells.indices&&cells[it]!="\u0000" }) pair else null }
                val prior=mutableListOf<List<Offset>>()
                valid.forEach { pair ->
                    val route=matrixConnectionRoute(pair[0],pair[1],cells,rows,cols,cw,ch,gx,gy,3f,prior)
                    if(route.size>1) prior+=route
                    val path=Path().apply { route.forEachIndexed { i,p -> if(i==0) moveTo(ox+p.x*scale,oy+p.y*scale) else lineTo(ox+p.x*scale,oy+p.y*scale) } }
                    drawPath(path,Color(0xFF65766E),style=Stroke(2f))
                }
                cells.forEachIndexed { index,text -> if(text!="\u0000") {
                    val x=ox+(index%cols)*(cw+gx)*scale; val y=oy+(index/cols)*(ch+gy)*scale
                    drawRoundRect(Color.White,Offset(x,y),androidx.compose.ui.geometry.Size(cw*scale,ch*scale),androidx.compose.ui.geometry.CornerRadius(3f))
                    drawRoundRect(Color(0xFF53675E),Offset(x,y),androidx.compose.ui.geometry.Size(cw*scale,ch*scale),androidx.compose.ui.geometry.CornerRadius(3f),style=Stroke(1.2f))
                    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(35,51,45); textSize=(11f*scale).coerceAtLeast(7f); textAlign=android.graphics.Paint.Align.CENTER }
                    drawContext.canvas.nativeCanvas.drawText(text.take(24),x+cw*scale/2f,y+ch*scale/2f+paint.textSize/3f,paint)
                } }
            }
            FieldType.DRAWING -> {
                val all=strokes.flatMap { it.points }
                if(all.isNotEmpty()) {
                    var minX=all.minOf { it.x }; var maxX=all.maxOf { it.x }; var minY=all.minOf { it.y }; var maxY=all.maxOf { it.y }
                    if(maxX-minX<1f) { minX-=1f; maxX+=1f }; if(maxY-minY<1f) { minY-=1f; maxY+=1f }
                    val scale=min(width/(maxX-minX),height/(maxY-minY))
                    fun px(x:Float)=left+(width-(maxX-minX)*scale)/2f+(x-minX)*scale
                    fun py(y:Float)=top+(height-(maxY-minY)*scale)/2f+(y-minY)*scale
                    strokes.forEach { stroke -> if(stroke.points.size>1) {
                        val path=Path().apply { stroke.points.forEachIndexed { i,p -> if(i==0) moveTo(px(p.x),py(p.y)) else lineTo(px(p.x),py(p.y)) } }
                        drawPath(path,Color(stroke.color.toULong()),style=Stroke((stroke.width*scale).coerceIn(1f,6f),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
                    } }
                }
            }
            else -> Unit
        }
        drawRoundRect(Color(0xFFBBC9C2),topLeft=Offset.Zero,size=size,cornerRadius=androidx.compose.ui.geometry.CornerRadius(5f),style=Stroke(1f))
    }
}

@Composable
private fun NumericComparisonChart(fields: List<ViewerField>, modifier: Modifier = Modifier) {
    val numbers = fields.mapNotNull { it.value.toFloatOrNull()?.let { value -> it.label to value } }
    if (numbers.size < 2) return
    val largest = numbers.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(0.0001f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        numbers.forEachIndexed { index, (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val barHeight = size.height * (kotlin.math.abs(value) / largest).coerceIn(0.08f, 1f)
                    drawRoundRect(
                        color = if (index % 2 == 0) Color(0xFF327D6D) else Color(0xFF83AFA4),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.5f, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
                    )
                }
                Text(label.take(10), style = MaterialTheme.typography.labelSmall, color = Color(0xFF68736E), maxLines = 1)
            }
        }
    }
}

private fun safePdfName(name: String): String = name.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "StrataField Form" }

private fun writeViewerPdf(
    context: Context,
    uri: Uri,
    response: FormResponse,
    fields: List<ViewerField>,
    pages: List<List<ViewerField>>,
    numericFields: List<ViewerField>
) {
    val document = PdfDocument()
    val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(99, 114, 107); textSize = 9f }
    val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(23, 40, 35); textSize = 20f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(48, 112, 97); textSize = 10f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(222, 230, 225); strokeWidth = 0.7f }
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(30, 89, 75) }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 9f }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(35, 51, 45); textSize = 10f }
    val dateLabel = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(response.createdAt))
    try {
        pages.forEachIndexed { index, pageFields ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(android.graphics.Color.rgb(252, 252, 250))
            var y = 42f
            canvas.drawText("STRATAFIELD  /  ${response.formType.name}", 42f, y, sectionPaint); y += 27f
            canvas.drawText(response.formName.take(48), 42f, y, heading); y += 18f
            canvas.drawText("${response.status.name}    ·    Recorded $dateLabel    ·    Page ${index + 1} of ${pages.size}", 42f, y, muted); y += 13f
            canvas.drawLine(42f, y, 553f, y, line); y += 18f
            if (index == 0) {
                val highlights = fields.filter { it.value.length < 50 }.take(3)
                if (highlights.isNotEmpty()) {
                    val cardWidth = (511f - (highlights.size - 1) * 8f) / highlights.size
                    highlights.forEachIndexed { cardIndex, field ->
                        val left = 42f + cardIndex * (cardWidth + 8f)
                        canvas.drawRoundRect(left, y, left + cardWidth, y + 52f, 5f, 5f, cardPaint)
                        canvas.drawText(field.value.take(24), left + 8f, y + 20f, white.apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD })
                        canvas.drawText(field.label.take(28), left + 8f, y + 38f, white.apply { textSize = 8f; typeface = Typeface.DEFAULT })
                    }
                    y += 66f
                }
                if (numericFields.size >= 2) {
                    canvas.drawText("MEASUREMENTS", 42f, y, sectionPaint); y += 9f
                    val chartValues = numericFields.take(4).mapNotNull { it.value.toFloatOrNull()?.let { v -> it to v } }
                    val max = chartValues.maxOfOrNull { kotlin.math.abs(it.second) }?.coerceAtLeast(0.0001f) ?: 1f
                    chartValues.forEachIndexed { chartIndex, (field, value) ->
                        val top = y + chartIndex * 14f
                        canvas.drawText(field.label.take(18), 42f, top + 8f, muted)
                        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (chartIndex % 2 == 0) android.graphics.Color.rgb(50, 125, 109) else android.graphics.Color.rgb(131, 175, 164) }
                        canvas.drawRoundRect(170f, top, 170f + 280f * (kotlin.math.abs(value) / max), top + 8f, 3f, 3f, barPaint)
                    }
                    y += chartValues.size * 14f + 10f
                }
            }
            var lastSection = ""
            pageFields.forEach { field ->
                if (field.section != lastSection) {
                    y += 6f
                    canvas.drawText(field.section.uppercase().take(48), 42f, y, sectionPaint)
                    y += 16f
                    lastSection = field.section
                }
                canvas.drawText(field.label.take(72), 42f, y, muted)
                y += 14f
                if (field.type.hasVisualPreview()) {
                    canvas.drawText(field.value.take(80), 42f, y, muted)
                    y += 6f
                    drawPdfVisual(canvas, field, 42f, y, 511f, 150f)
                    y += 160f
                    canvas.drawLine(42f, y + 2f, 553f, y + 2f, line)
                    y += 8f
                    return@forEach
                }
                val wrapped = wrapPdfText(field.value, body, 505f)
                wrapped.forEach { textLine ->
                    canvas.drawText(textLine, 42f, y, body)
                    y += 13f
                }
                canvas.drawLine(42f, y + 2f, 553f, y + 2f, line)
                y += 8f
            }
            if (pageFields.isEmpty()) canvas.drawText("No recorded field data", 42f, y, muted)
            canvas.drawText("StrataField · ${fields.size} recorded entries", 42f, 810f, muted)
            document.finishPage(page)
        }
        context.contentResolver.openOutputStream(uri)?.use(document::writeTo)
            ?: error("Could not open the selected document")
    } finally {
        document.close()
    }
}

private fun drawPdfVisual(canvas: android.graphics.Canvas, field: ViewerField, left:Float, top:Float, width:Float, height:Float) {
    val border=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(221,229,225); style=Paint.Style.STROKE; strokeWidth=1f }
    val grid=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(232,238,235); strokeWidth=0.6f }
    val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(54,74,64); strokeWidth=1.5f; style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    val label=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(86,101,93); textSize=8f }
    val margin=30f
    val plotLeft=left+margin; val plotTop=top+margin; val plotW=(width-2*margin).coerceAtLeast(1f); val plotH=(height-2*margin).coerceAtLeast(1f)
    canvas.drawRoundRect(left,top,left+width,top+height,4f,4f,border)
    when(field.type) {
        FieldType.GRAPH_PAPER -> {
            val data=runCatching { deserializeGraphPaper(field.visualData) }.getOrNull() ?: return
            val all=data.points.map { it.x to it.y }+data.inkStrokes.orEmpty().flatMap { s -> s.points.map { it.x to it.y } }
            var minX=all.minOfOrNull { it.first }?:-1f; var maxX=all.maxOfOrNull { it.first }?:1f
            var minY=all.minOfOrNull { it.second }?:-1f; var maxY=all.maxOfOrNull { it.second }?:1f
            if(maxX-minX<0.0001f){minX-=1f;maxX+=1f}; if(maxY-minY<0.0001f){minY-=1f;maxY+=1f}
            val scale=min(plotW/(maxX-minX),plotH/(maxY-minY)); val cx=(minX+maxX)/2f; val cy=(minY+maxY)/2f
            fun px(x:Float)=plotLeft+plotW/2f+(x-cx)*scale
            fun py(y:Float)=plotTop+plotH/2f-(y-cy)*scale
            var step=(data.scaleRatio.takeIf { it.isFinite()&&it>0f }?:1f)/10f
            val tickCount=max((maxX-minX)/step,(maxY-minY)/step)
            if(tickCount>80f) step*=ceil(tickCount/80f)
            var xTick=kotlin.math.floor(minX/step)*step; var limit=0
            while(xTick<=maxX&&limit++<80){canvas.drawLine(px(xTick),plotTop,px(xTick),plotTop+plotH,grid); canvas.drawText(String.format(Locale.US,"%.2f",xTick),px(xTick),top+height-3f,label);xTick+=step}
            var yTick=kotlin.math.floor(minY/step)*step; limit=0
            while(yTick<=maxY&&limit++<80){canvas.drawLine(plotLeft,py(yTick),plotLeft+plotW,py(yTick),grid);canvas.drawText(String.format(Locale.US,"%.2f",yTick),left+1f,py(yTick)-2f,label);yTick+=step}
            if(minX<=0&&maxX>=0)canvas.drawLine(px(0f),plotTop,px(0f),plotTop+plotH,ink)
            if(minY<=0&&maxY>=0)canvas.drawLine(plotLeft,py(0f),plotLeft+plotW,py(0f),ink)
            val points=data.points.associateBy { it.id }
            data.connections.forEach { c -> val a=points[c.fromId];val b=points[c.toId];if(a!=null&&b!=null)canvas.drawLine(px(a.x),py(a.y),px(b.x),py(b.y),ink) }
            data.inkStrokes.orEmpty().forEach { stroke -> if(stroke.points.size>1){val path=android.graphics.Path();stroke.points.forEachIndexed{i,p->if(i==0)path.moveTo(px(p.x),py(p.y))else path.lineTo(px(p.x),py(p.y))};ink.color=Color(stroke.color.toULong()).toArgb();ink.strokeWidth=(stroke.width*scale).coerceIn(1f,5f);canvas.drawPath(path,ink)} }
            data.points.forEach { p -> val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=if(p.color==0L)android.graphics.Color.rgb(50,125,109) else Color(p.color.toULong()).toArgb() };canvas.drawCircle(px(p.x),py(p.y),3f,paint) }
            label.textSize=7f; canvas.drawText(data.squareUnit,plotLeft+plotW-22f,top+height-3f,label)
        }
        FieldType.MATRIX -> {
            val data=runCatching { deserializeMatrix(field.visualData) }.getOrNull()?:return
            val cols=data.columns.coerceIn(1,20);val rows=((data.cells.size+cols-1)/cols).coerceIn(1,20);val cells=List(rows*cols){data.cells.getOrNull(it)?:MATRIX_HOLE}
            val cw=68f;val ch=36f;val gx=28f;val gy=28f;val contentW=cw*cols+gx*(cols-1);val contentH=ch*rows+gy*(rows-1)
            val scale=min(plotW/contentW.coerceAtLeast(1f),plotH/contentH.coerceAtLeast(1f));val ox=plotLeft+(plotW-contentW*scale)/2f;val oy=plotTop+(plotH-contentH*scale)/2f
            val routePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(96,115,105);strokeWidth=1.2f;style=Paint.Style.STROKE }
            val prior=mutableListOf<List<Offset>>()
            data.connections.forEach { pair ->if(pair.size==2&&pair.all{it in cells.indices&&cells[it]!=MATRIX_HOLE}){
                val route=matrixConnectionRoute(pair[0],pair[1],cells,rows,cols,cw,ch,gx,gy,3f,prior);if(route.size>1)prior+=route
                val path=android.graphics.Path();route.forEachIndexed{i,p->val x=ox+p.x*scale;val y=oy+p.y*scale;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};canvas.drawPath(path,routePaint)
            }}
            val box=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE;style=Paint.Style.FILL };val boxBorder=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(83,103,94);style=Paint.Style.STROKE;strokeWidth=1f };val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(35,51,45);textAlign=Paint.Align.CENTER;textSize=9f }
            cells.forEachIndexed{i,value->if(value!=MATRIX_HOLE){val x=ox+(i%cols)*(cw+gx)*scale;val y=oy+(i/cols)*(ch+gy)*scale;canvas.drawRoundRect(x,y,x+cw*scale,y+ch*scale,3f,3f,box);canvas.drawRoundRect(x,y,x+cw*scale,y+ch*scale,3f,3f,boxBorder);canvas.drawText(value.take(25),x+cw*scale/2f,y+ch*scale/2f+3f,text)}}
        }
        FieldType.DRAWING -> {
            val strokes=runCatching { deserializeStrokes(field.visualData) }.getOrDefault(emptyList());val all=strokes.flatMap{it.points};if(all.isEmpty())return
            var minX=all.minOf{it.x};var maxX=all.maxOf{it.x};var minY=all.minOf{it.y};var maxY=all.maxOf{it.y};if(maxX-minX<1f){minX-=1f;maxX+=1f};if(maxY-minY<1f){minY-=1f;maxY+=1f}
            val scale=min(plotW/(maxX-minX),plotH/(maxY-minY));val ox=plotLeft+(plotW-(maxX-minX)*scale)/2f;val oy=plotTop+(plotH-(maxY-minY)*scale)/2f
            strokes.forEach{s->if(s.points.size>1){val path=android.graphics.Path();s.points.forEachIndexed{i,p->val x=ox+(p.x-minX)*scale;val y=oy+(p.y-minY)*scale;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};ink.color=Color(s.color.toULong()).toArgb();ink.strokeWidth=(s.width*scale).coerceIn(1f,5f);canvas.drawPath(path,ink)}}
        }
        else -> Unit
    }
}

private fun wrapPdfText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    text.split(Regex("\\s+")).forEach { word ->
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
            lines += current.toString()
            current = StringBuilder(word)
        } else current = StringBuilder(candidate)
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines.ifEmpty { listOf("") }
}
