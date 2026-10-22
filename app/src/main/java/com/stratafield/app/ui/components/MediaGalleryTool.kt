package com.stratafield.app.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stratafield.app.ui.theme.NothingRed
import java.io.File

@Composable
fun MediaGalleryTool(
    modifier: Modifier = Modifier,
    initialUris: List<String> = emptyList(),
    initialImageNumbers: String = "",
    onChange: (List<String>, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var uris by remember { mutableStateOf(initialUris) }
    var imageNumbers by remember { mutableStateOf(initialImageNumbers) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var capturedPreviewUri by remember { mutableStateOf<Uri?>(null) }

    fun notifyChange() = onChange(uris, imageNumbers)

    fun createImageUri(): Uri {
        val imageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "StrataField")
        imageDir.mkdirs()
        val file = File(imageDir, "field_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val takePhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        if (saved) {
            capturedPreviewUri = pendingCameraUri
        } else {
            pendingCameraUri = null
        }
    }

    fun launchCamera() {
        pendingCameraUri = createImageUri()
        takePhoto.launch(pendingCameraUri)
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        }
    }

    fun startCameraCapture() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { picked ->
        if (picked.isNotEmpty()) {
            picked.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // Photo Picker URIs are already app-scoped and readable without a persisted grant
                }
            }
            uris = uris + picked.map { it.toString() }
            notifyChange()
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uris.size} attached",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { startCameraCapture() }
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Capture image", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(
                    onClick = {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Add media", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        OutlinedTextField(
            value = imageNumbers,
            onValueChange = {
                imageNumbers = it
                notifyChange()
            },
            label = { Text("Image number") },
            placeholder = { Text("Numbers from another device") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            minLines = 1,
            shape = RoundedCornerShape(8.dp)
        )

        if (uris.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media attached",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(uris) { uriString ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = Uri.parse(uriString),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                        IconButton(
                            onClick = {
                                uris = uris.filterNot { it == uriString }
                                notifyChange()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove",
                                tint = NothingRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    capturedPreviewUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { capturedPreviewUri = null },
            title = { Text("Captured Image") },
            text = {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    uris = uris + uri.toString()
                    pendingCameraUri = null
                    capturedPreviewUri = null
                    notifyChange()
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = {
                    capturedPreviewUri = null
                    startCameraCapture()
                }) { Text("Recapture") }
            }
        )
    }
}

// Serialization helpers
data class MediaFieldData(
    val uris: List<String> = emptyList(),
    val imageNumbers: String = ""
)

fun serializeMedia(uris: List<String>, imageNumbers: String): String = Gson().toJson(MediaFieldData(uris, imageNumbers))

fun serializeMediaUris(uris: List<String>): String = serializeMedia(uris, "")

fun deserializeMedia(json: String): MediaFieldData {
    return try {
        if (json.isBlank()) MediaFieldData()
        else if (json.trim().startsWith("[")) MediaFieldData(deserializeMediaUris(json), "")
        else Gson().fromJson(json, MediaFieldData::class.java) ?: MediaFieldData()
    } catch (e: Exception) {
        MediaFieldData()
    }
}

fun deserializeMediaUris(json: String): List<String> {
    return try {
        if (json.isBlank()) emptyList()
        else if (json.trim().startsWith("{")) deserializeMedia(json).uris
        else {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(json, type) ?: emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}
