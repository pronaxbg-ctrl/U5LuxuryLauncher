@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.u5.carlauncher

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
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

private fun bearingToCompass(bearing: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((bearing % 360f) + 360f) % 360f
    return dirs[((normalized + 22.5f) / 45f).toInt() % 8]
}

/**
 * Hardware-cached static Blue Tech Showroom Grid & Neon Platform Ring.
 * Uses drawWithCache so brushes and coordinates are allocated once per size change,
 * eliminating continuous 60fps GPU overdraw on the Ottocast P3 Pro.
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
                // 1. Ambient Holographic Blue Halo
                drawOval(
                    brush = haloBrush,
                    topLeft = Offset(cx - w * 0.48f, h * 0.14f),
                    size = Size(w * 0.96f, h * 0.78f)
                )

                // 2. Perspective Showroom Platform Grid (5 rows x 11 cols)
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

                // 3. Under-Chassis Shadow & Glowing Cyan Tech Ring
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
    val dateLabel = remember {
        SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date()).uppercase(Locale.US)
    }
    val estRangeKm = ((batteryPct.coerceIn(0, 100) / 100f) * 400f).roundToInt()
    val compassLabel = bearingToCompass(carBearing)

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dateLabel,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.0.sp
                    )
                    Text(
                        text = "•  AIWAYS U5",
                        color = Color(0xFF7DD3FC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }

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

            // MAIN TELEMETRY + BLUE TECH AIWAYS U5 CENTER STAGE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT TELEMETRY: GPS Speed + Compass + Trip
                Column(
                    modifier = Modifier
                        .width(88.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$carSpeedKmh",
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.8).sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "km/h",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = "GPS SPEED",
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Navigation,
                            contentDescription = "Heading",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = compassLabel,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${tripKm.roundToInt()}",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "km",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Text(
                            text = "TRIP",
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // CENTER STAGE: COMPRESSED TRANSPARENT BLUE TECH AIWAYS U5
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
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
                            .fillMaxWidth(0.98f)
                            .fillMaxHeight(0.86f)
                            .padding(bottom = 8.dp)
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

                // RIGHT TELEMETRY: Battery Ring + Est Range + Tire Pressure
                Column(
                    modifier = Modifier
                        .width(88.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier.size(62.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            drawArc(
                                color = Color.White.copy(alpha = 0.14f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = stroke
                            )
                            drawArc(
                                color = Color(0xFF38BDF8),
                                startAngle = -90f,
                                sweepAngle = 360f * (batteryPct.coerceIn(0, 100) / 100f),
                                useCenter = false,
                                style = stroke
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$batteryPct%",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "BATT",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "EST. RANGE",
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$estRangeKm",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "km",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "2.5",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "BAR",
                            color = Color.White.copy(alpha = 0.56f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
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
            ) {
                // LEFT: INTERACTIVE 3D-TILT HOLOGRAPHIC SHOWROOM
                Column(
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF0A1220))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = {
                                        targetTiltY = 0f
                                        targetTiltX = 0f
                                    },
                                    onDragCancel = {
                                        targetTiltY = 0f
                                        targetTiltX = 0f
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    targetTiltY = (targetTiltY + dragAmount.x * 0.14f).coerceIn(-24f, 24f)
                                    targetTiltX = (targetTiltX - dragAmount.y * 0.10f).coerceIn(-12f, 12f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BlueTechShowroomStage(
                            accentColor = theme.accent,
                            accentGlow = theme.accentGlow,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationY = tiltY * 0.45f
                                    rotationX = tiltX * 0.45f
                                    cameraDistance = 14f * density
                                }
                        )

                        Image(
                            painter = painterResource(id = paintColor.drawableRes),
                            contentDescription = "Aiways U5 ${paintColor.label}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .fillMaxHeight(0.86f)
                                .graphicsLayer {
                                    rotationY = tiltY
                                    rotationX = tiltX
                                    cameraDistance = 14f * density
                                }
                        )
                    }

                    // Render Style Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            U5PaintColor.entries.forEach { p ->
                                val selected = p == paintColor
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) Color(0xFF0EA5E9).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            1.dp,
                                            if (selected) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.16f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectPaint(p) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(p.swatch)
                                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    )
                                    Text(
                                        text = p.label,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // RIGHT: LIVE TELEMETRY & VERIFIED 2021 AIWAYS U5 SPECS
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "LIVE TELEMETRY & SYNC",
                            color = Color(0xFF7DD3FC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Battery: $batteryPct%  •  Est. $estRangeKm km",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Speed: $carSpeedKmh km/h  •  Trip: ${tripKm.roundToInt()} km",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmallSyncChip("-5%") { onAdjustBattery(-5) }
                                SmallSyncChip("+5%") { onAdjustBattery(5) }
                                SmallSyncChip("Reset Trip") { onResetTrip() }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VehicleSpecTile("BATTERY", "63 kWh", "60 kWh Usable • 400V", theme, Modifier.weight(1f))
                            VehicleSpecTile("WLTP RANGE", "400 km", "17.0 kWh / 100 km", theme, Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VehicleSpecTile("POWERTRAIN", "150 kW / 204 PS", "310 Nm • FWD", theme, Modifier.weight(1f))
                            VehicleSpecTile("PERFORMANCE", "0-100 in 7.5s", "Top Speed 150 km/h", theme, Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VehicleSpecTile("DC FAST CHARGE", "90 kW CCS", "20–80% in ~35 min", theme, Modifier.weight(1f))
                            VehicleSpecTile("DIMENSIONS", "4680 × 1865 mm", "Height 1700 • Boot 432L", theme, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallSyncChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VehicleSpecTile(
    label: String,
    value: String,
    sub: String,
    theme: LuxuryThemeType,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF7DD3FC),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = sub,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
