@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.u5.carlauncher

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class U5PaintColor(
    val id: String,
    val label: String,
    val swatch: Color,
    @DrawableRes val drawableRes: Int
) {
    TECH_BLUE(
        id = "tech_blue",
        label = "Hologram Blue",
        swatch = Color(0xFF0EA5E9),
        drawableRes = R.drawable.u5_tech_blue
    ),
    ELECTRIC_BLUE(
        id = "blue",
        label = "Electric Blue",
        swatch = Color(0xFF2563EB),
        drawableRes = R.drawable.u5_studio_blue
    ),
    GLACIER_WHITE(
        id = "white",
        label = "Glacier White",
        swatch = Color(0xFFF1F5F9),
        drawableRes = R.drawable.u5_studio_white
    );

    companion object {
        fun fromId(id: String?): U5PaintColor =
            entries.firstOrNull { it.id == id } ?: TECH_BLUE
    }
}

/**
 * Hardware-cached static Blue Tech Showroom Grid & Neon Platform Ring.
 */
@Composable
fun BlueTechShowroomStage(
    accentColor: Color,
    accentGlow: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val horizonY = h * 0.56f
            val bottomY = h * 0.94f
            val cx = w / 2f

            val techCyan = Color(0xFF38BDF8)
            val deepTechBlue = Color(0xFF0284C7)

            val haloBrush = Brush.radialGradient(
                colors = listOf(
                    techCyan.copy(alpha = 0.28f),
                    deepTechBlue.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(cx, h * 0.54f),
                radius = (w * 0.48f).coerceAtLeast(1f)
            )
            val shadowBrush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                center = Offset(cx, h * 0.77f),
                radius = (w * 0.41f).coerceAtLeast(1f)
            )
            val ringStrokeBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    techCyan.copy(alpha = 0.52f),
                    accentGlow.copy(alpha = 0.58f),
                    techCyan.copy(alpha = 0.52f),
                    Color.Transparent
                )
            )

            val ringRectTopLeft = Offset(cx - w * 0.43f, h * 0.65f)
            val ringRectSize = Size(w * 0.86f, h * 0.24f)

            onDrawBehind {
                drawOval(
                    brush = haloBrush,
                    topLeft = Offset(cx - w * 0.48f, h * 0.14f),
                    size = Size(w * 0.96f, h * 0.78f)
                )

                val rows = 5
                for (i in 0..rows) {
                    val t = i.toFloat() / rows
                    val eased = t * t
                    val y = horizonY + (bottomY - horizonY) * eased
                    val halfSpan = w * (0.28f + 0.22f * eased)
                    drawLine(
                        color = techCyan.copy(alpha = 0.12f + 0.24f * t),
                        start = Offset(cx - halfSpan, y),
                        end = Offset(cx + halfSpan, y),
                        strokeWidth = 1.1f
                    )
                }

                val cols = 5
                for (i in -cols..cols) {
                    val frac = i.toFloat() / cols
                    val topX = cx + frac * w * 0.28f
                    val botX = cx + frac * w * 0.50f
                    drawLine(
                        color = techCyan.copy(alpha = 0.20f),
                        start = Offset(topX, horizonY),
                        end = Offset(botX, bottomY),
                        strokeWidth = 1.1f
                    )
                }

                drawOval(
                    brush = shadowBrush,
                    topLeft = ringRectTopLeft,
                    size = ringRectSize
                )
                drawOval(
                    brush = ringStrokeBrush,
                    topLeft = ringRectTopLeft,
                    size = ringRectSize,
                    style = Stroke(width = 2.0f)
                )
            }
        }
    )
}

@Composable
fun AiwaysU5CockpitCard(
    carSpeedKmh: Int,
    carBearing: Float,
    tripKm: Float,
    batteryPct: Int,
    paintColor: U5PaintColor,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    onLongPress3D: () -> Unit,
    onSwitchToWatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .luxuryCardSurface(theme, finish, cornerRadius = 26.dp)
            .combinedClickable(
                onClick = { },
                onLongClick = onLongPress3D
            )
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP HEADER ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AIWAYS U5",
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.18f))
                            .border(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                            .clickable { onLongPress3D() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "U5 3D",
                            color = Color(0xFFBAE6FD),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                            .clickable { onSwitchToWatch() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "WATCH",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // CENTER STAGE: CLEAN AIWAYS U5 VISUAL (No km/h, battery %, range km, or bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                BlueTechShowroomStage(
                    accentColor = theme.accent,
                    accentGlow = theme.accentGlow,
                    modifier = Modifier.fillMaxSize()
                )

                Image(
                    painter = painterResource(id = paintColor.drawableRes),
                    contentDescription = "Aiways U5 ${paintColor.label}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.88f)
                        .padding(bottom = 6.dp)
                )

                Text(
                    text = "HOLD FOR 3D VEHICLE INFO",
                    color = Color(0xFF7DD3FC).copy(alpha = 0.68f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 1.dp)
                )
            }
        }
    }
}

/**
 * Press-and-Hold Realistic Studio Aiways U5 Showcase + Interactive 3D Tilt & Full Specs
 */
@Composable
fun AiwaysU53DInspectorOverlay(
    batteryPct: Int,
    tripKm: Float,
    carSpeedKmh: Int,
    paintColor: U5PaintColor,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    onAdjustBattery: (Int) -> Unit,
    onResetTrip: () -> Unit,
    onSelectPaint: (U5PaintColor) -> Unit,
    onClose: () -> Unit
) {
    var targetTiltY by remember { mutableFloatStateOf(0f) }
    var targetTiltX by remember { mutableFloatStateOf(0f) }
    val tiltY by animateFloatAsState(targetValue = targetTiltY, animationSpec = spring(dampingRatio = 0.72f), label = "tiltY")
    val tiltX by animateFloatAsState(targetValue = targetTiltX, animationSpec = spring(dampingRatio = 0.72f), label = "tiltX")

    val estRangeKm = ((batteryPct.coerceIn(0, 100) / 100f) * 400f).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF206080D))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .luxuryCardSurface(theme, finish, cornerRadius = 28.dp)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF1D4ED8)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DirectionsCar,
                            contentDescription = "Aiways U5",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AIWAYS U5  •  3D TECH SHOWCASE",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.0.sp
                        )
                        Text(
                            text = "2021 Electric SUV  •  Drag car for 3D perspective tilt",
                            color = Color(0xFF7DD3FC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                CarPlayDockBadge(
                    icon = Icons.Rounded.Close,
                    gradient = listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)),
                    onClick = onClose
                )
            }
Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
Box(
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFF16202C), Color(0xFF0B0F17))
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AIWAYS U5",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "ELECTRIC SUV  •  READY",
                            color = Color(0xFF7DD3FC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "POWERTRAIN & ARCHITECTURE",
                        color = Color(0xFF7DD3FC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "63 kWh CATL Sandwich Battery • 150 kW (204 HP) Front Motor • 310 Nm Torque • 400 km WLTP Range",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "MAS Steel-Aluminum Body • Panoramic Sunroof • 12.3\" Central Touchscreen • 1920x720 Widescreen Mode",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}
