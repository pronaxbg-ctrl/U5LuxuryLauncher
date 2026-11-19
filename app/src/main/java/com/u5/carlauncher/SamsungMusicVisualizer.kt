package com.u5.carlauncher

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.sin

fun extractVibrantWaveColors(bitmap: Bitmap?): Pair<Color, Color> {
    val defaultPair = Pair(Color(0xFF00D2FF), Color(0xFF80F2FF))
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return defaultPair

    return try {
        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        var bestScore = -1f
        var bestColor = AndroidColor.rgb(0, 210, 255)
        val hsv = FloatArray(3)

        for (y in 0 until scaled.height step 2) {
            for (x in 0 until scaled.width step 2) {
                val pixel = scaled.getPixel(x, y)
                AndroidColor.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val value = hsv[2]
                if (sat > 0.35f && value > 0.35f && value < 0.96f) {
                    val score = sat * 1.4f + value
                    if (score > bestScore) {
                        bestScore = score
                        bestColor = pixel
                    }
                }
            }
        }
        if (scaled != bitmap) scaled.recycle()

        if (bestScore < 0f) return defaultPair

        AndroidColor.colorToHSV(bestColor, hsv)
        val primaryHsv = floatArrayOf(hsv[0], hsv[1].coerceAtLeast(0.75f), hsv[2].coerceAtLeast(0.85f))
        val glowHsv = floatArrayOf((hsv[0] + 22f) % 360f, (hsv[1] * 0.85f).coerceAtLeast(0.55f), 1.0f)

        Pair(
            Color(AndroidColor.HSVToColor(primaryHsv)),
            Color(AndroidColor.HSVToColor(glowHsv))
        )
    } catch (_: Exception) {
        defaultPair
    }
}

@Composable
fun OneUiWaveProgressBar(
    progress: Float,
    isPlaying: Boolean,
    wavePrimary: Color,
    waveGlow: Color,
    modifier: Modifier = Modifier,
    onSeekFraction: (Float) -> Unit = {}
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "oneui_wave")

    val phaseA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseA"
    )

    val phaseB by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseB"
    )

    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.15f,
        animationSpec = tween(durationMillis = 450),
        label = "energy"
    )

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                if (size.width > 0) {
                    onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val trackY = h * 0.68f
        val thumbX = (w * clampedProgress).coerceIn(10f, (w - 10f).coerceAtLeast(10f))

        drawLine(
            color = Color.White.copy(alpha = 0.28f),
            start = Offset(0f, trackY),
            end = Offset(w, trackY),
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )

        if (thumbX > 18f) {
            val steps = 48
            val maxAmp = h * 0.48f * energy

            val waveFill1 = Path().apply {
                moveTo(0f, trackY)
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = t * thumbX
                    val envelope = sin(t * PI).toFloat()
                    val yOffset = (
                        0.55f * (sin(t * 2.5 * PI + phaseA).toFloat() * 0.5f + 0.5f) +
                        0.45f * (sin(t * 4.5 * PI - phaseB).toFloat() * 0.5f + 0.5f)
                    ) * maxAmp * envelope
                    lineTo(x, trackY - yOffset)
                }
                lineTo(thumbX, trackY)
                close()
            }

            val waveFill2 = Path().apply {
                moveTo(0f, trackY)
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = t * thumbX
                    val envelope = sin(t * PI).toFloat()
                    val yOffset = (
                        0.6f * (sin(t * 3.2 * PI - phaseA * 1.2f).toFloat() * 0.5f + 0.5f) +
                        0.4f * (sin(t * 5.2 * PI + phaseB).toFloat() * 0.5f + 0.5f)
                    ) * (maxAmp * 0.72f) * envelope
                    lineTo(x, trackY - yOffset)
                }
                lineTo(thumbX, trackY)
                close()
            }

            drawPath(
                path = waveFill1,
                brush = Brush.verticalGradient(
                    colors = listOf(waveGlow.copy(alpha = 0.85f), wavePrimary.copy(alpha = 0.92f)),
                    startY = trackY - maxAmp,
                    endY = trackY
                )
            )

            drawPath(
                path = waveFill2,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), waveGlow.copy(alpha = 0.75f)),
                    startY = trackY - maxAmp * 0.75f,
                    endY = trackY
                )
            )
        }

        drawLine(
            brush = Brush.horizontalGradient(colors = listOf(wavePrimary, waveGlow), startX = 0f, endX = thumbX),
            start = Offset(0f, trackY),
            end = Offset(thumbX, trackY),
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )

        drawCircle(color = wavePrimary.copy(alpha = 0.45f), radius = 18f, center = Offset(thumbX, trackY))
        drawCircle(color = wavePrimary, radius = 11f, center = Offset(thumbX, trackY))
        drawCircle(color = Color.White, radius = 11f, center = Offset(thumbX, trackY), style = Stroke(width = 3.5f))
    }
}
