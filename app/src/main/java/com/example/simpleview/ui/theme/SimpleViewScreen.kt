package com.example.simpleview.ui.theme

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImageMetadata(
    val name: String,
    val extension: String,
    val resolution: String,
    val size: String,
    val date: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleViewScreen() {

    val context = LocalContext.current
    val view = LocalView.current
    var allImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var isCropping by remember { mutableStateOf(false) }
    
    val currentUri = if (currentIndex in allImages.indices) allImages[currentIndex] else null

    // System bars control
    LaunchedEffect(isFullScreen) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        
        if (isFullScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val images = fetchImagesInSameFolder(context, it)
            allImages = images
            currentIndex = images.indexOf(it)
        }
    }

    if (isCropping && currentUri != null) {
        BackHandler { isCropping = false }
        CropScreen(
            uri = currentUri,
            onCropConfirmed = { 
                isCropping = false 
                // Refresh images to see the new one if saved in the same folder
                val images = fetchImagesInSameFolder(context, currentUri)
                allImages = images
                currentIndex = images.indexOf(currentUri)
            },
            onCancel = { isCropping = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isFullScreen) Modifier else Modifier.safeDrawingPadding().padding(16.dp)),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (!isFullScreen) {
            Text(
                text = "Simple View",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    launcher.launch("image/*")
                }
            ) {
                Text("Abrir imagem")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (currentUri != null) {
            var scale by remember(currentUri) { mutableFloatStateOf(1f) }
            var offset by remember(currentUri) { mutableStateOf(Offset.Zero) }

            ImageViewer(
                uri = currentUri,
                scale = scale,
                onScaleChange = { scale = it },
                offset = offset,
                onOffsetChange = { offset = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onToggleFullScreen = { isFullScreen = !isFullScreen }
            )
            
            if (!isFullScreen) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentIndex > 0) currentIndex-- },
                        enabled = currentIndex > 0
                    ) {
                        Text("◀")
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(onClick = { showInfo = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Informações da imagem",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(onClick = { isCropping = true }) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Cortar imagem",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, currentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Imagem"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartilhar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(onClick = {
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(currentUri, "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(viewIntent, "Abrir com..."))
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Abrir com...",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Button(
                        onClick = { if (currentIndex < allImages.size - 1) currentIndex++ },
                        enabled = currentIndex < allImages.size - 1
                    ) {
                        Text("▶")
                    }
                }
            }
        } else {
            Text(
                text = "Nenhuma imagem selecionada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showInfo && currentUri != null) {
            val metadata = remember(currentUri) { getImageMetadata(context, currentUri) }
            val sheetState = rememberModalBottomSheetState()
            
            ModalBottomSheet(
                onDismissRequest = { showInfo = false },
                sheetState = sheetState
            ) {
                InfoPanel(metadata)
            }
        }
    }
}

@Composable
fun InfoPanel(metadata: ImageMetadata) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Informações da Imagem",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        InfoRow("Nome", metadata.name)
        InfoRow("Extensão", metadata.extension)
        InfoRow("Resolução", metadata.resolution)
        InfoRow("Tamanho", metadata.size)
        InfoRow("Data", metadata.date)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ImageViewer(
    uri: Uri,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    onToggleFullScreen: () -> Unit
) {
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        onScaleChange(newScale)
        
        if (newScale > 1f) {
            onOffsetChange(offset + offsetChange * newScale)
        } else {
            onOffsetChange(Offset.Zero)
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleFullScreen() }
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .transformable(state = state)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Imagem selecionada",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

fun getImageMetadata(context: Context, uri: Uri): ImageMetadata {
    var name = "Desconhecido"
    var sizeBytes = 0L
    var dateModified = 0L
    
    val contentResolver = context.contentResolver
    
    // 1. Query basic info from ContentResolver
    val projection = arrayOf(
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE,
        MediaStore.Images.Media.DATE_MODIFIED
    )
    
    try {
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1) name = cursor.getString(nameIdx)
                
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
                
                val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                if (dateIdx != -1) dateModified = cursor.getLong(dateIdx) * 1000 // MediaStore stores in seconds
            }
        }
    } catch (_: Exception) {}

    // 2. Get Resolution using BitmapFactory
    var resolution = "Desconhecida"
    try {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            if (options.outWidth != -1 && options.outHeight != -1) {
                resolution = "${options.outWidth} x ${options.outHeight}"
            }
        }
    } catch (_: Exception) {}

    val extension = name.substringAfterLast('.', "")
    val sizeStr = Formatter.formatFileSize(context, sizeBytes)
    val dateStr = if (dateModified > 0) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dateModified))
    } else {
        "Desconhecida"
    }

    return ImageMetadata(name, extension, resolution, sizeStr, dateStr)
}

fun fetchImagesInSameFolder(context: Context, selectedUri: Uri): List<Uri> {
    val images = mutableListOf<Uri>()
    val contentResolver = context.contentResolver

    var bucketId: String? = null
    val projection = arrayOf(MediaStore.Images.Media.BUCKET_ID)
    
    try {
        contentResolver.query(selectedUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                bucketId = cursor.getString(bucketIdColumn)
            }
        }
    } catch (_: Exception) {}

    if (bucketId != null) {
        val externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val queryProjection = arrayOf(MediaStore.Images.Media._ID)

        contentResolver.query(externalUri, queryProjection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = Uri.withAppendedPath(externalUri, id.toString())
                images.add(uri)
            }
        }
    }

    if (images.isEmpty() || !images.contains(selectedUri)) {
        if (!images.contains(selectedUri)) {
            images.add(0, selectedUri)
        }
    }

    return images
}