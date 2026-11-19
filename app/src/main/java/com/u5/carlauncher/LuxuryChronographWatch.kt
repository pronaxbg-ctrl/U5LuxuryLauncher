package com.u5.carlauncher

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun LuxuryChronographCard(
    isWhiteDial: Boolean,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    onToggleDial: () -> Unit,
    onSwitchToCar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(100L)
        }
    }

    val calendar = remember(nowMillis) {
        Calendar.getInstance().apply { timeInMillis = nowMillis }
    }
    val dateLabel = remember(nowMillis / 60_000L) {
        SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(nowMillis)).uppercase(Locale.US)
    }

    Box(
        modifier = modifier
            .luxuryCardSurface(theme, finish, cornerRadius = 26.dp)
            .clickable { onToggleDial() }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(theme.accent.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
        )

        Text(
            text = dateLabel,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 4.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .clickable { onSwitchToCar() }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "AIWAYS U5",
                color = theme.accentGlow,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val radius = min(size.width, size.height) * 0.485f
            val center = Offset(size.width / 2f, size.height / 2f)

            val dialBg = if (isWhiteDial) Color(0xFFECEEF2) else Color(0xFF101319)
            val dialBgEdge = if (isWhiteDial) Color(0xFFD2D6DF) else Color(0xFF07090C)
            val subDialBg = if (isWhiteDial) Color(0xFFE0E3EB) else Color(0xFF171B24)
            val indexColor = if (isWhiteDial) Color(0xFF161922) else Color(0xFFF3F4F6)
            val minorTickColor = if (isWhiteDial) Color(0xFF475569) else Color(0xFF94A3B8)
            val handColor = if (isWhiteDial) Color(0xFF11141C) else Color(0xFFF8FAFC)
            val handLume = if (isWhiteDial) Color(0xFFFFFFFF) else Color(0xFFE0F2FE)
            val accentRed = Color(0xFFDC2626)

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFE2E8F0), Color(0xFF64748B), Color(0xFFF1F5F9), Color(0xFF475569)),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                ),
                radius = radius,
                center = center
            )

            val bezelOuter = radius * 0.955f
            val dialRadius = radius * 0.815f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF232730), Color(0xFF0B0D11)),
                    center = center,
                    radius = bezelOuter
                ),
                radius = bezelOuter,
                center = center
            )

            val bezelMid = (bezelOuter + dialRadius) / 2f
            for (i in 0 until 60) {
                if (i % 5 == 0 || i == 1 || i == 2 || i == 3) {
                    val angleRad = (i * 6.0 - 90.0) * PI / 180.0
                    val dotPos = Offset(
                        center.x + (cos(angleRad) * bezelMid).toFloat(),
                        center.y + (sin(angleRad) * bezelMid).toFloat()
                    )
                    drawCircle(
                        color = Color(0xFFE2E8F0).copy(alpha = if (i % 5 == 0) 0.85f else 0.45f),
                        radius = if (i % 5 == 0) radius * 0.015f else radius * 0.008f,
                        center = dotPos
                    )
                }
            }

            drawCircle(color = Color(0xFF94A3B8), radius = dialRadius + 1.5f, center = center, style = Stroke(width = 2.5f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(dialBg, dialBgEdge),
                    center = Offset(center.x, center.y - dialRadius * 0.15f),
                    radius = dialRadius * 1.1f
                ),
                radius = dialRadius,
                center = center
            )

            for (i in 0 until 60) {
                val angleRad = (i * 6.0 - 90.0) * PI / 180.0
                val isHour = i % 5 == 0
                val outerR = dialRadius * 0.94f
                val innerR = if (isHour) dialRadius * 0.78f else dialRadius * 0.88f

                if (i == 0) {
                    val perpX = -sin(angleRad).toFloat()
                    val perpY = cos(angleRad).toFloat()
                    val sep = radius * 0.032f
                    for (sign in listOf(-1f, 1f)) {
                        val s = Offset(center.x + (cos(angleRad) * innerR).toFloat() + perpX * sep * sign, center.y + (sin(angleRad) * innerR).toFloat() + perpY * sep * sign)
                        val e = Offset(center.x + (cos(angleRad) * outerR).toFloat() + perpX * sep * sign, center.y + (sin(angleRad) * outerR).toFloat() + perpY * sep * sign)
                        drawLine(indexColor, s, e, strokeWidth = radius * 0.026f, cap = StrokeCap.Round)
                    }
                } else {
                    val start = Offset(center.x + (cos(angleRad) * innerR).toFloat(), center.y + (sin(angleRad) * innerR).toFloat())
                    val end = Offset(center.x + (cos(angleRad) * outerR).toFloat(), center.y + (sin(angleRad) * outerR).toFloat())
                    drawLine(
                        color = if (isHour) indexColor else minorTickColor.copy(alpha = 0.75f),
                        start = start,
                        end = end,
                        strokeWidth = if (isHour) radius * 0.032f else radius * 0.011f,
                        cap = StrokeCap.Round
                    )
                }
            }

            val brandPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                color = if (isWhiteDial) android.graphics.Color.parseColor("#11141C") else android.graphics.Color.WHITE
                textSize = radius * 0.13f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                letterSpacing = 0.18f
            }
            val subBrandPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                color = android.graphics.Color.parseColor("#DC2626")
                textSize = radius * 0.072f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                letterSpacing = 0.22f
            }
            drawContext.canvas.nativeCanvas.drawText("U5", center.x, center.y - dialRadius * 0.36f, brandPaint)
            drawContext.canvas.nativeCanvas.drawText("CHRONOGRAPH", center.x, center.y - dialRadius * 0.24f, subBrandPaint)

            val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
            val hour12 = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)
            val millis = calendar.get(Calendar.MILLISECOND)

            val smoothSec = second + millis / 1000f
            val secAngle = smoothSec * 6f
            val minAngle = (minute + smoothSec / 60f) * 6f
            val hourAngle = (hour12 + minute / 60f) * 30f
            val hour24Angle = (hour24 + minute / 60f) * 15f

            val subR = dialRadius * 0.225f
            val subOffset = dialRadius * 0.42f
            drawChronoSubdial(Offset(center.x - subOffset, center.y), subR, subDialBg, minorTickColor, indexColor, secAngle)
            drawChronoSubdial(Offset(center.x + subOffset, center.y), subR, subDialBg, minorTickColor, indexColor, hour24Angle)
            drawChronoSubdial(Offset(center.x, center.y + subOffset), subR, subDialBg, minorTickColor, accentRed, minAngle)

            rotate(degrees = hourAngle, pivot = center) {
                val hourPath = Path().apply {
                    moveTo(center.x, center.y - dialRadius * 0.52f)
                    lineTo(center.x + radius * 0.042f, center.y - dialRadius * 0.10f)
                    lineTo(center.x + radius * 0.025f, center.y + dialRadius * 0.12f)
                    lineTo(center.x - radius * 0.025f, center.y + dialRadius * 0.12f)
                    lineTo(center.x - radius * 0.042f, center.y - dialRadius * 0.10f)
                    close()
                }
                drawPath(hourPath, color = handColor)
                drawLine(handLume.copy(alpha = 0.9f), Offset(center.x, center.y - dialRadius * 0.43f), Offset(center.x, center.y - dialRadius * 0.14f), strokeWidth = radius * 0.020f, cap = StrokeCap.Round)
            }

            rotate(degrees = minAngle, pivot = center) {
                val minPath = Path().apply {
                    moveTo(center.x, center.y - dialRadius * 0.78f)
                    lineTo(center.x + radius * 0.032f, center.y - dialRadius * 0.10f)
                    lineTo(center.x + radius * 0.020f, center.y + dialRadius * 0.15f)
                    lineTo(center.x - radius * 0.020f, center.y + dialRadius * 0.15f)
                    lineTo(center.x - radius * 0.032f, center.y - dialRadius * 0.10f)
                    close()
                }
                drawPath(minPath, color = handColor)
                drawLine(handLume.copy(alpha = 0.9f), Offset(center.x, center.y - dialRadius * 0.66f), Offset(center.x, center.y - dialRadius * 0.16f), strokeWidth = radius * 0.015f, cap = StrokeCap.Round)
            }

            rotate(degrees = secAngle, pivot = center) {
                drawLine(accentRed, Offset(center.x, center.y + dialRadius * 0.22f), Offset(center.x, center.y - dialRadius * 0.84f), strokeWidth = radius * 0.014f, cap = StrokeCap.Round)
                drawCircle(accentRed, radius = radius * 0.026f, center = Offset(center.x, center.y + dialRadius * 0.18f))
            }

            drawCircle(color = handColor, radius = radius * 0.048f, center = center)
            drawCircle(color = Color(0xFFE2E8F0), radius = radius * 0.020f, center = center)
        }
    }
}

private fun DrawScope.drawChronoSubdial(
    subCenter: Offset,
    subRadius: Float,
    bg: Color,
    ringColor: Color,
    handColor: Color,
    angleDeg: Float
) {
    drawCircle(color = bg, radius = subRadius, center = subCenter)
    drawCircle(color = ringColor.copy(alpha = 0.45f), radius = subRadius, center = subCenter, style = Stroke(width = 1.6f))
    for (i in 0 until 12) {
        val a = (i * 30.0 - 90.0) * PI / 180.0
        val major = i % 3 == 0
        val rOut = subRadius * 0.90f
        val rIn = if (major) subRadius * 0.66f else subRadius * 0.78f
        drawLine(
            color = ringColor.copy(alpha = if (major) 0.9f else 0.55f),
            start = Offset(subCenter.x + (cos(a) * rIn).toFloat(), subCenter.y + (sin(a) * rIn).toFloat()),
            end = Offset(subCenter.x + (cos(a) * rOut).toFloat(), subCenter.y + (sin(a) * rOut).toFloat()),
            strokeWidth = if (major) 2.0f else 1.1f,
            cap = StrokeCap.Round
        )
    }
    rotate(degrees = angleDeg, pivot = subCenter) {
        drawLine(handColor, Offset(subCenter.x, subCenter.y + subRadius * 0.18f), Offset(subCenter.x, subCenter.y - subRadius * 0.76f), strokeWidth = 2.4f, cap = StrokeCap.Round)
    }
    drawCircle(color = handColor, radius = subRadius * 0.13f, center = subCenter)
}
