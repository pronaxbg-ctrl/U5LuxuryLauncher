package com.u5.carlauncher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Samsung Music-inspired layered flowing audio wave & subtle spectrum visualizer.
 * Reacts smoothly between active playback (energetic multi-harmonic waves) and paused idle state.
 */
@Composable
fun SamsungMusicWaveVisualizer(
    isPlaying: Boolean,
    theme: LuxuryThemeType,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "samsung_wave")

    val phasePrimary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phasePrimary"
    )

    val phaseSecondary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseSecondary"
    )

    val phasePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phasePulse"
    )

    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.14f,
        animationSpec = tween(durationMillis = 600),
        label = "waveEnergy"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.56f

        // 1. Soft Samsung Music ambient glow behind waves
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    theme.accent.copy(alpha = 0.22f * energy + 0.05f),
                    theme.accentSecondary.copy(alpha = 0.08f * energy),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, centerY),
                radius = w * 0.65f
            )
        )

        // 2. Subtle vertical spectrum bars (Samsung Music style center-outward pulse)
        val barCount = 36
        val barSpacing = w / barCount
        val barWidth = (barSpacing * 0.36f).coerceAtLeast(3f)

        for (i in 0 until barCount) {
            val normX = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            val envelope = sin(normX * PI).toFloat()
            val harmonic =
                0.5f * abs(sin(normX * 7.5f + phasePrimary * 1.4f)) +
                0.35f * abs(sin(normX * 13f - phaseSecondary * 1.8f)) +
                0.15f * abs(sin(normX * 4f + phasePulse * 2f))

            val barHeight = (h * 0.42f * envelope * harmonic * energy).coerceAtLeast(4f)
            val x = i * barSpacing + (barSpacing - barWidth) / 2f
            val topY = centerY - barHeight / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        theme.accentGlow.copy(alpha = 0.45f * energy + 0.06f),
                        theme.accent.copy(alpha = 0.20f * energy + 0.04f)
                    ),
                    startY = topY,
                    endY = topY + barHeight
                ),
                topLeft = Offset(x, topY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // 3. Layered translucent flowing sine-wave ribbons
        drawWaveRibbon(
            width = w,
            height = h,
            centerY = centerY,
            amplitude = h * 0.26f * energy,
            frequency = 2.4f,
            phase = phasePrimary,
            secondaryPhase = phasePulse,
            fillBrush = Brush.verticalGradient(
                colors = listOf(
                    theme.accent.copy(alpha = 0.30f * energy + 0.04f),
                    Color.Transparent
                ),
                startY = centerY - h * 0.28f,
                endY = h
            ),
            strokeColor = theme.accentGlow.copy(alpha = 0.85f * energy + 0.15f),
            strokeWidth = 3.5f
        )

        drawWaveRibbon(
            width = w,
            height = h,
            centerY = centerY + 6f,
            amplitude = h * 0.19f * energy,
            frequency = 3.2f,
            phase = -phaseSecondary * 1.2f,
            secondaryPhase = phasePrimary,
            fillBrush = Brush.verticalGradient(
                colors = listOf(
                    theme.accentSecondary.copy(alpha = 0.26f * energy + 0.03f),
                    Color.Transparent
                ),
                startY = centerY - h * 0.22f,
                endY = h
            ),
            strokeColor = theme.accent.copy(alpha = 0.70f * energy + 0.12f),
            strokeWidth = 2.5f
        )

        drawWaveRibbon(
            width = w,
            height = h,
            centerY = centerY - 4f,
            amplitude = h * 0.14f * energy,
            frequency = 4.6f,
            phase = phasePrimary * 1.6f + 1.2f,
            secondaryPhase = -phaseSecondary,
            fillBrush = Brush.verticalGradient(
                colors = listOf(
                    theme.accentGlow.copy(alpha = 0.16f * energy),
                    Color.Transparent
                ),
                startY = centerY - h * 0.18f,
                endY = h
            ),
            strokeColor = Color.White.copy(alpha = 0.45f * energy + 0.08f),
            strokeWidth = 1.8f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveRibbon(
    width: Float,
    height: Float,
    centerY: Float,
    amplitude: Float,
    frequency: Float,
    phase: Float,
    secondaryPhase: Float,
    fillBrush: Brush,
    strokeColor: Color,
    strokeWidth: Float
) {
    val strokePath = Path()
    val fillPath = Path()
    val steps = 80

    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val x = t * width
        // Taper edges cleanly to 0 like Samsung Music waves
        val edgeTaper = sin(t * PI).toFloat()
        val wave = sin(t * frequency * 2.0 * PI + phase).toFloat() * 0.7f +
            sin(t * (frequency * 1.7f) * 2.0 * PI + secondaryPhase).toFloat() * 0.3f
        val y = centerY + wave * amplitude * edgeTaper

        if (i == 0) {
            strokePath.moveTo(x, y)
            fillPath.moveTo(x, height)
            fillPath.lineTo(x, y)
        } else {
            strokePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }

    fillPath.lineTo(width, height)
    fillPath.close()

    drawPath(path = fillPath, brush = fillBrush)
    drawPath(
        path = strokePath,
        color = strokeColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}
