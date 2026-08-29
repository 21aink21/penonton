package com.donghuaz.ui.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Custom Modifier untuk menggambar bayangan blur di belakang komponen Image / Card
 * Memanfaatkan objek Paint native Android dengan BlurMaskFilter (Hardware GPU Accelerated)
 */
fun Modifier.coloredShadow(
    color: Color,
    alpha: Float = 0.4f,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 25.dp,
    offsetY: Dp = 12.dp,
    offsetX: Dp = 0.dp
) = this.drawBehind {
    val shadowColor = color.copy(alpha = alpha).toArgb()

    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()

        // Mengaktifkan efek blur mask native Android pada GPU
        frameworkPaint.color = shadowColor
        frameworkPaint.maskFilter = BlurMaskFilter(
            blurRadius.toPx().coerceAtLeast(1f),
            BlurMaskFilter.Blur.NORMAL
        )

        // Menggambar bayangan sesuai ukuran komponen asli
        canvas.drawRoundRect(
            left = offsetX.toPx(),
            top = offsetY.toPx(),
            right = size.width + offsetX.toPx(),
            bottom = size.height + offsetY.toPx(),
            radiusX = borderRadius.toPx(),
            radiusY = borderRadius.toPx(),
            paint = paint
        )
    }
}

/**
 * Ekstraksi warna dominan dari Bitmap menggunakan Palette API Android
 */
fun extractDominantColor(bitmap: Bitmap, defaultColor: Color = Color(0xFFFC6F01)): Color {
    val palette = Palette.from(bitmap).generate()
    val dominantSwatch = palette.dominantSwatch
        ?: palette.vibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.darkVibrantSwatch
    return dominantSwatch?.rgb?.let { Color(it) } ?: defaultColor
}

/**
 * Composable helper untuk memuat gambar dari URL dan mengekstrak warna dominan secara asinkron
 */
@Composable
fun rememberDominantColor(
    imageUrl: String,
    defaultColor: Color = Color(0xFFFC6F01)
): State<Color> {
    val context = LocalContext.current
    val dominantColorState = remember(imageUrl) { mutableStateOf(defaultColor) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false) // Required for Palette software bitmap access
                    .build()
                val result = (loader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val color = extractDominantColor(bitmap, defaultColor)
                    dominantColorState.value = color
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    return dominantColorState
}
