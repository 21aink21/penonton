package com.lk21official.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Komponen Poster Anime Jetpack Compose dengan Palette API & Custom coloredShadow Modifier
 */
@Composable
fun AnimePosterCard(
    posterUrl: String,
    title: String = "",
    rating: String = "",
    latestEp: String = "",
    width: Dp = 180.dp,
    height: Dp = 270.dp,
    borderRadius: Dp = 16.dp,
    blurRadius: Dp = 30.dp,
    offsetY: Dp = 14.dp,
    shadowAlpha: Float = 0.45f,
    onClick: () -> Unit = {}
) {
    // 1. Ekstrak warna dominan dari gambar menggunakan Palette API Android
    val dominantColor by rememberDominantColor(imageUrl = posterUrl)

    // 2. Terapkan Layout Card dengan Custom Modifier coloredShadow
    Card(
        modifier = Modifier
            .size(width = width, height = height)
            .coloredShadow(
                color = dominantColor,
                alpha = shadowAlpha,
                borderRadius = borderRadius,
                blurRadius = blurRadius,
                offsetY = offsetY
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(borderRadius),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E162A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gambar Poster Anime via AsyncImage Coil
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = title.ifEmpty { "Anime Poster" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradasi bawah untuk kontras teks
            if (title.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC09060F)),
                                startY = 300f
                            )
                        )
                )

                // Info Judul, Episode & Rating
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = title,
                        color = Color(0xFFF3F4F6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (rating.isNotEmpty()) {
                            Text(
                                text = "★ $rating",
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        if (latestEp.isNotEmpty()) {
                            Text(
                                text = latestEp,
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
