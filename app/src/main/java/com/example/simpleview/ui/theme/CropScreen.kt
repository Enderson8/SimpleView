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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt

import androidx.compose.foundation.layout.safeDrawingPadding

private enum class Corner { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Composable
fun CropScreen(
    uri: Uri,
    onCropConfirmed: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var imageDisplaySize by remember { mutableStateOf(Size.Zero) }

    // Carrega o Bitmap da imagem original
    LaunchedEffect(uri) {
        context.contentResolver.openInputStream(uri)?.use { 
            bitmap = BitmapFactory.decodeStream(it)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { resultUri: Uri? ->
        if (resultUri != null && bitmap != null && cropRect != null) {
            saveCroppedBitmap(context, bitmap!!, cropRect!!, imageDisplaySize, resultUri)
            onCropConfirmed(resultUri)
        } else {
            // Se o usuário cancelar o diálogo de salvamento, voltamos mesmo assim
            onCancel()
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
            .safeDrawingPadding()
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
            
            // Inicializa o tamanho e o retângulo apenas uma vez
            if (imageDisplaySize == Size.Zero) {
                imageDisplaySize = Size(displayWidth, displayHeight)
                cropRect = Rect(offset = Offset.Zero, size = imageDisplaySize)
            }

            val density = LocalDensity.current
            val boxWidthDp = with(density) { displayWidth.toDp() }
            val boxHeightDp = with(density) { displayHeight.toDp() }

            Box(
                modifier = Modifier
                    .size(boxWidthDp, boxHeightDp)
            ) {
                // Imagem estática (sem zoom/pan)
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                cropRect?.let { rect ->
                    var activeCorner by remember { mutableStateOf(Corner.NONE) }
                    val touchAreaPx = with(density) { 48.dp.toPx() }
                    val minSize = 100f

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(rect) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        activeCorner = when {
                                            (startOffset - rect.topLeft).getDistance() < touchAreaPx -> Corner.TOP_LEFT
                                            (startOffset - rect.topRight).getDistance() < touchAreaPx -> Corner.TOP_RIGHT
                                            (startOffset - rect.bottomLeft).getDistance() < touchAreaPx -> Corner.BOTTOM_LEFT
                                            (startOffset - rect.bottomRight).getDistance() < touchAreaPx -> Corner.BOTTOM_RIGHT
                                            else -> Corner.NONE
                                        }
                                    },
                                    onDragEnd = { activeCorner = Corner.NONE },
                                    onDragCancel = { activeCorner = Corner.NONE },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (activeCorner != Corner.NONE) {
                                            val newRect = when (activeCorner) {
                                                Corner.TOP_LEFT -> {
                                                    val left = (rect.left + dragAmount.x).coerceIn(0f, rect.right - minSize)
                                                    val top = (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - minSize)
                                                    Rect(left, top, rect.right, rect.bottom)
                                                }
                                                Corner.TOP_RIGHT -> {
                                                    val right = (rect.right + dragAmount.x).coerceIn(rect.left + minSize, displayWidth)
                                                    val top = (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - minSize)
                                                    Rect(rect.left, top, right, rect.bottom)
                                                }
                                                Corner.BOTTOM_LEFT -> {
                                                    val left = (rect.left + dragAmount.x).coerceIn(0f, rect.right - minSize)
                                                    val bottom = (rect.bottom + dragAmount.y).coerceIn(rect.top + minSize, displayHeight)
                                                    Rect(left, rect.top, rect.right, bottom)
                                                }
                                                Corner.BOTTOM_RIGHT -> {
                                                    val right = (rect.right + dragAmount.x).coerceIn(rect.left + minSize, displayWidth)
                                                    val bottom = (rect.bottom + dragAmount.y).coerceIn(rect.top + minSize, displayHeight)
                                                    Rect(rect.left, rect.top, right, bottom)
                                                }
                                                else -> rect
                                            }
                                            cropRect = newRect
                                        }
                                    }
                                )
                            }
                    ) {
                        // 1. Escurece a área fora do recorte
                        // Top
                        drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset.Zero, size = Size(displayWidth, rect.top))
                        // Bottom
                        drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, rect.bottom), size = Size(displayWidth, displayHeight - rect.bottom))
                        // Left
                        drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height))
                        // Right
                        drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(rect.right, rect.top), size = Size(displayWidth - rect.right, rect.height))

                        // 2. Desenha a moldura branca
                        drawRect(
                            color = Color.White,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 3. Desenha as alças (círculos) nos cantos
                        val handleRadius = 8.dp.toPx()
                        drawCircle(Color.White, radius = handleRadius, center = rect.topLeft)
                        drawCircle(Color.White, radius = handleRadius, center = rect.topRight)
                        drawCircle(Color.White, radius = handleRadius, center = rect.bottomLeft)
                        drawCircle(Color.White, radius = handleRadius, center = rect.bottomRight)
                    }
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

private fun saveCroppedBitmap(context: Context, source: Bitmap, rect: Rect, displaySize: Size, destUri: Uri) {
    if (displaySize.width <= 0 || displaySize.height <= 0) return

    val scaleX = source.width / displaySize.width
    val scaleY = source.height / displaySize.height

    val x = (rect.left * scaleX).roundToInt().coerceIn(0, source.width - 1)
    val y = (rect.top * scaleY).roundToInt().coerceIn(0, source.height - 1)
    val width = (rect.width * scaleX).roundToInt().coerceIn(1, source.width - x)
    val height = (rect.height * scaleY).roundToInt().coerceIn(1, source.height - y)

    val cropped = Bitmap.createBitmap(source, x, y, width, height)

    context.contentResolver.openOutputStream(destUri)?.use { out ->
        cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
}