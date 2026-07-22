package com.example.simpleview.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun CropScreen(
    uri: Uri,
    onCropConfirmed: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var displaySize by remember { mutableStateOf(Size.Zero) }
    
    LaunchedEffect(uri) {
        context.contentResolver.openInputStream(uri)?.use { 
            bitmap = BitmapFactory.decodeStream(it)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { resultUri: Uri? ->
        if (resultUri != null && bitmap != null && cropRect != null) {
            saveCroppedBitmap(context, bitmap!!, cropRect!!, displaySize, resultUri)
            onCropConfirmed(resultUri)
        }
    }

    if (bitmap == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Carregando...", color = Color.White)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Recortar Imagem",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            
            val imgWidth = bitmap!!.width.toFloat()
            val imgHeight = bitmap!!.height.toFloat()
            
            val scale = minOf(containerWidth / imgWidth, containerHeight / imgHeight)
            val displayWidth = imgWidth * scale
            val displayHeight = imgHeight * scale
            
            if (displaySize == Size.Zero) {
                displaySize = Size(displayWidth, displayHeight)
            }

            if (cropRect == null) {
                cropRect = androidx.compose.ui.geometry.Rect(
                    offset = Offset.Zero,
                    size = Size(displayWidth, displayHeight)
                )
            }

            val density = LocalDensity.current
            val boxWidth = with(density) { displayWidth.toDp() }
            val boxHeight = with(density) { displayHeight.toDp() }

            Box(modifier = Modifier.size(boxWidth, boxHeight)) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                cropRect?.let { rect ->
                    CropOverlay(
                        rect = rect,
                        onRectChange = { cropRect = it },
                        maxWidth = displayWidth,
                        maxHeight = displayHeight
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onCancel) {
                Text("Cancelar")
            }
            Button(onClick = {
                val fileName = "cropped_${System.currentTimeMillis()}.jpg"
                saveLauncher.launch(fileName)
            }) {
                Text("Confirmar")
            }
        }
    }
}

@Composable
fun CropOverlay(
    rect: androidx.compose.ui.geometry.Rect,
    onRectChange: (androidx.compose.ui.geometry.Rect) -> Unit,
    maxWidth: Float,
    maxHeight: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            color = Color.White,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = 2.dp.toPx())
        )
        
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset.Zero,
            size = Size(maxWidth, rect.top)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, rect.bottom),
            size = Size(maxWidth, maxHeight - rect.bottom)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, rect.top),
            size = Size(rect.left, rect.height)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(rect.right, rect.top),
            size = Size(maxWidth - rect.right, rect.height)
        )
    }

    CropHandle(
        offset = rect.topLeft,
        onDrag = { dragAmount ->
            val newTop = (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - 50f)
            val newLeft = (rect.left + dragAmount.x).coerceIn(0f, rect.right - 50f)
            onRectChange(androidx.compose.ui.geometry.Rect(newLeft, newTop, rect.right, rect.bottom))
        }
    )
    
    CropHandle(
        offset = rect.topRight,
        onDrag = { dragAmount ->
            val newTop = (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - 50f)
            val newRight = (rect.right + dragAmount.x).coerceIn(rect.left + 50f, maxWidth)
            onRectChange(androidx.compose.ui.geometry.Rect(rect.left, newTop, newRight, rect.bottom))
        }
    )
    
    CropHandle(
        offset = rect.bottomLeft,
        onDrag = { dragAmount ->
            val newBottom = (rect.bottom + dragAmount.y).coerceIn(rect.top + 50f, maxHeight)
            val newLeft = (rect.left + dragAmount.x).coerceIn(0f, rect.right - 50f)
            onRectChange(androidx.compose.ui.geometry.Rect(newLeft, rect.top, rect.right, newBottom))
        }
    )
    
    CropHandle(
        offset = rect.bottomRight,
        onDrag = { dragAmount ->
            val newBottom = (rect.bottom + dragAmount.y).coerceIn(rect.top + 50f, maxHeight)
            val newRight = (rect.right + dragAmount.x).coerceIn(rect.left + 50f, maxWidth)
            onRectChange(androidx.compose.ui.geometry.Rect(rect.left, rect.top, newRight, newBottom))
        }
    )
}

@Composable
fun CropHandle(
    offset: Offset,
    onDrag: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val sizePx = with(density) { 32.dp.toPx() }
    val halfSizePx = sizePx / 2
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .offset { 
                IntOffset(
                    (offset.x - halfSizePx).roundToInt(),
                    (offset.y - halfSizePx).roundToInt()
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.White, CircleShape)
        )
    }
}

fun saveCroppedBitmap(context: Context, source: Bitmap, displayRect: androidx.compose.ui.geometry.Rect, displaySize: Size, destUri: Uri) {
    val scaleX = source.width / displaySize.width
    val scaleY = source.height / displaySize.height
    
    val cropX = (displayRect.left * scaleX).roundToInt().coerceIn(0, source.width - 1)
    val cropY = (displayRect.top * scaleY).roundToInt().coerceIn(0, source.height - 1)
    val cropWidth = (displayRect.width * scaleX).roundToInt().coerceIn(1, source.width - cropX)
    val cropHeight = (displayRect.height * scaleY).roundToInt().coerceIn(1, source.height - cropY)
    
    val cropped = Bitmap.createBitmap(source, cropX, cropY, cropWidth, cropHeight)
    
    context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
        cropped.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    }
}
