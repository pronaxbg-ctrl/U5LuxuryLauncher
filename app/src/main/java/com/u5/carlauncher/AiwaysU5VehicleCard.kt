@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.u5.carlauncher

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

enum class U5PaintColor(
    val id: String,
    val label: String,
    val bodyLight: Color,
    val bodyMid: Color,
    val bodyDark: Color,
    val trimGlow: Color
) {
    ELECTRIC_BLUE(
        id = "blue",
        label = "Electric Blue",
        bodyLight = Color(0xFF4B7BB8),
        bodyMid = Color(0xFF214372),
        bodyDark = Color(0xFF0E1D36),
        trimGlow = Color(0xFF7DD3FC)
    ),
    GLACIER_WHITE(
        id = "white",
        label = "Glacier White",
        bodyLight = Color(0xFFF1F5F9),
        bodyMid = Color(0xFFCBD5E1),
        bodyDark = Color(0xFF64748B),
        trimGlow = Color(0xFFE0F2FE)
    ),
    OBSIDIAN_BLACK(
        id = "black",
        label = "Obsidian",
        bodyLight = Color(0xFF475569),
        bodyMid = Color(0xFF1E293B),
        bodyDark = Color(0xFF090D16),
        trimGlow = Color(0xFF94A3B8)
    ),
    AUBERGINE_SILVER(
        id = "aubergine",
        label = "Aubergine",
        bodyLight = Color(0xFF7C5A7E),
        bodyMid = Color(0xFF462C49),
        bodyDark = Color(0xFF1F1121),
        trimGlow = Color(0xFFE9D5FF)
    );

    companion object {
        fun fromId(id: String?): U5PaintColor =
            entries.firstOrNull { it.id == id } ?: ELECTRIC_BLUE
    }
}

private fun bearingToCompass(bearing: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((bearing % 360f) + 360f) % 360f
    return dirs[((normalized + 22.5f) / 45f).toInt() % 8]
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
                onClick = { /* Short tap kept calm; long-press opens 3D view */ },
                onLongClick = onLongPress3D
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Subtle showroom radial spotlight behind the Aiways U5
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            theme.accent.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP HEADER ROW: Date (left) + AIWAYS U5 / WATCH pill (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateLabel,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.0.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.8.dp, theme.border, RoundedCornerShape(10.dp))
                            .clickable { onLongPress3D() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "U5 3D",
                            color = theme.accentGlow,
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

            // MAIN TELEMETRY + CENTER AIWAYS U5 SUV STAGE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT TELEMETRY COLUMN: GPS Speed + Compass + Trip
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$carSpeedKmh",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.8).sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "km/h",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 12.sp,
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

                    // Compass Heading
                    Column(horizontalAlignment = Alignment.Start) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Navigation,
                                contentDescription = "Heading",
                                tint = theme.accentGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = compassLabel,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Trip Odometer
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
                                fontSize = 12.sp,
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

                // CENTER STAGE: 2021 AIWAYS U5 ELECTRIC SUV ON ILLUMINATED PLATFORM
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    AiwaysU53DCanvas(
                        yawDeg = -32f,
                        pitchDeg = 13f,
                        paintColor = paintColor,
                        accentColor = theme.accent,
                        showGrid = true,
                        modifier = Modifier.fillMaxSize()
                    )

                    Text(
                        text = "AIWAYS U5  •  HOLD FOR 3D",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 1.dp)
                    )
                }

                // RIGHT TELEMETRY COLUMN: Battery Ring + Est Range + Tire Pressure
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Circular Battery Ring
                    Box(
                        modifier = Modifier.size(64.dp),
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
                                brush = Brush.sweepGradient(
                                    colors = listOf(theme.accent, theme.accentGlow, theme.accent)
                                ),
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

                    // Estimated WLTP Range
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

                    // Tire Pressure (Aiways U5 Standard 2.5 BAR)
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
 * Press-and-Hold 3D Interactive Aiways U5 Popup Modal
 * Allows 360-degree touch rotation, paint color switching, battery sync, trip reset,
 * and full 2021 Aiways U5 vehicle specifications.
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
    var yawDeg by remember { mutableFloatStateOf(-35f) }
    var pitchDeg by remember { mutableFloatStateOf(16f) }
    var autoSpin by remember { mutableStateOf(true) }

    // Smooth 3D turntable rotation until user drags manually
    LaunchedEffect(autoSpin) {
        while (autoSpin) {
            yawDeg = (yawDeg + 0.8f) % 360f
            delay(30L)
        }
    }

    val estRangeKm = ((batteryPct.coerceIn(0, 100) / 100f) * 400f).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF006080D))
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
            // Header
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
                            .background(Brush.linearGradient(listOf(theme.accent, theme.accentSecondary))),
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
                            text = "AIWAYS U5  •  3D VEHICLE VIEW",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.0.sp
                        )
                        Text(
                            text = "2021 Electric SUV  •  Drag to rotate 360°",
                            color = theme.accentGlow.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (autoSpin) theme.accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                            .border(1.dp, if (autoSpin) theme.accent else Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .clickable { autoSpin = !autoSpin }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (autoSpin) "Auto-Spin: ON" else "Auto-Spin: OFF",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CarPlayDockBadge(
                        icon = Icons.Rounded.Close,
                        gradient = listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)),
                        onClick = onClose
                    )
                }
            }

            // Main Split: Left 3D Turntable Stage | Right Vehicle Specs & Telemetry Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // LEFT: INTERACTIVE 3D AIWAYS U5 TURNTABLE
                Column(
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF162235), Color(0xFF090D14))
                            )
                        )
                        .border(1.dp, theme.border, RoundedCornerShape(22.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    autoSpin = false
                                    yawDeg = (yawDeg + dragAmount.x * 0.55f) % 360f
                                    pitchDeg = (pitchDeg + dragAmount.y * 0.25f).coerceIn(4f, 38f)
                                }
                            }
                    ) {
                        AiwaysU53DCanvas(
                            yawDeg = yawDeg,
                            pitchDeg = pitchDeg,
                            paintColor = paintColor,
                            accentColor = theme.accent,
                            showGrid = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Paint Finish Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            U5PaintColor.entries.forEach { p ->
                                val selected = p == paintColor
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                        .border(
                                            1.dp,
                                            if (selected) theme.accentGlow else Color.White.copy(alpha = 0.15f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectPaint(p) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(p.bodyLight)
                                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                    )
                                    Text(
                                        text = p.label,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // RIGHT: VEHICLE SPECIFICATIONS & LIVE DASHBOARD SYNC
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Live Telemetry Sync Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, theme.border, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "LIVE TELEMETRY & SYNC",
                            color = theme.accentGlow,
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

                    // 2021 Aiways U5 Verified Technical Specs Grid
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
            color = theme.accentGlow,
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

// ============================================================================
// TRUE 3D PERSPECTIVE AIWAYS U5 ELECTRIC SUV RENDERER (PURE KOTLIN / COMPOSE)
// ============================================================================

private data class Vec3(val x: Float, val y: Float, val z: Float)

private data class Face3D(
    val points: List<Vec3>,
    val fill: Color,
    val stroke: Color = Color.White.copy(alpha = 0.18f),
    val strokeWidth: Float = 1.4f,
    val isEmissive: Boolean = false
)

@Composable
fun AiwaysU53DCanvas(
    yawDeg: Float,
    pitchDeg: Float,
    paintColor: U5PaintColor,
    accentColor: Color,
    showGrid: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.56f
        val scale = min(w, h) * 0.62f

        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val cosY = cos(yaw); val sinY = sin(yaw)
        val cosP = cos(pitch); val sinP = sin(pitch)

        fun transform(v: Vec3): Vec3 {
            val rx = v.x * cosY + v.z * sinY
            val rz1 = -v.x * sinY + v.z * cosY
            val ry = v.y * cosP - rz1 * sinP
            val rz = v.y * sinP + rz1 * cosP
            return Vec3(rx, ry, rz)
        }

        fun project(v: Vec3): Offset {
            val camDist = 4.6f
            val f = camDist / (camDist + v.z).coerceAtLeast(1.2f)
            return Offset(cx + v.x * scale * f, cy - v.y * scale * f)
        }

        // 1. Illuminated Showroom Perspective Platform Grid
        if (showGrid) {
            val gridY = -0.34f
            val gridColor = accentColor.copy(alpha = 0.28f)
            for (i in -3..3) {
                val t = i * 0.36f
                val p1 = project(transform(Vec3(-1.35f, gridY, t)))
                val p2 = project(transform(Vec3(1.35f, gridY, t)))
                drawLine(gridColor, p1, p2, strokeWidth = 1.2f)

                val q1 = project(transform(Vec3(t * 1.25f, gridY, -1.08f)))
                val q2 = project(transform(Vec3(t * 1.25f, gridY, 1.08f)))
                drawLine(gridColor, q1, q2, strokeWidth = 1.2f)
            }

            // Soft under-car neon shadow oval
            val shadowCenter = project(transform(Vec3(0f, gridY + 0.01f, 0f)))
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    center = shadowCenter,
                    radius = scale * 0.95f
                ),
                topLeft = Offset(shadowCenter.x - scale * 0.95f, shadowCenter.y - scale * 0.30f),
                size = Size(scale * 1.90f, scale * 0.60f)
            )
        }

        // 2. Build 2021 Aiways U5 Electric SUV 3D Geometry (4.68m x 1.86m x 1.70m proportions)
        // Coordinate system: X = length (-1.12 rear .. +1.12 front), Y = height (-0.34 ground .. +0.46 roof), Z = width (-0.48 .. +0.48)
        val faces = mutableListOf<Face3D>()
        val cLight = paintColor.bodyLight
        val cMid = paintColor.bodyMid
        val cDark = paintColor.bodyDark
        val glassColor = Color(0xFF0C1524)
        val roofColor = Color(0xFF0A0D13) // Iconic Aiways U5 floating black roof
        val trimColor = Color(0xFF141820)
        val headlightColor = Color(0xFFE0F2FE)
        val taillightColor = Color(0xFFFF2A3C)

        // Helper to add a quad face
        fun addQuad(p1: Vec3, p2: Vec3, p3: Vec3, p4: Vec3, fill: Color, stroke: Color = paintColor.trimGlow.copy(alpha = 0.25f), sw: Float = 1.3f, emissive: Boolean = false) {
            faces.add(Face3D(listOf(p1, p2, p3, p4), fill, stroke, sw, emissive))
        }

        val wBody = 0.48f
        val wRoof = 0.37f
        val yLow = -0.18f
        val yBelt = 0.14f
        val yHoodFront = 0.06f
        val yRoof = 0.46f

        // Lower body sides (Left Z=+wBody, Right Z=-wBody)
        for (side in listOf(-1f, 1f)) {
            val zB = side * wBody
            val zR = side * wRoof
            val sideShade = if (side > 0) cMid else cDark

            // Main side body panel (SUV tall shoulder line)
            addQuad(
                Vec3(-1.08f, yLow, zB),
                Vec3(1.06f, yLow, zB),
                Vec3(1.10f, yHoodFront, zB * 0.96f),
                Vec3(-1.05f, yBelt, zB * 0.98f),
                sideShade
            )
            // Upper shoulder contour from hood to rear
            addQuad(
                Vec3(-1.05f, yBelt, zB * 0.98f),
                Vec3(0.46f, yBelt + 0.02f, zB * 0.98f),
                Vec3(1.10f, yHoodFront, zB * 0.96f),
                Vec3(0.50f, yLow, zB),
                sideShade
            )
            // Side greenhouse windows (floating black glasshouse)
            addQuad(
                Vec3(-0.92f, yBelt + 0.01f, zB * 0.95f),
                Vec3(0.42f, yBelt + 0.02f, zB * 0.95f),
                Vec3(0.16f, yRoof - 0.01f, zR),
                Vec3(-0.76f, yRoof - 0.01f, zR),
                glassColor,
                stroke = Color.White.copy(alpha = 0.35f)
            )
            // Signature Aiways U5 vertical side air-curtain intake on front bumper
            addQuad(
                Vec3(1.03f, yLow + 0.02f, zB * 0.97f),
                Vec3(1.10f, yLow + 0.04f, zB * 0.92f),
                Vec3(1.08f, yHoodFront - 0.02f, zB * 0.92f),
                Vec3(0.99f, yHoodFront - 0.01f, zB * 0.97f),
                trimColor,
                stroke = paintColor.trimGlow.copy(alpha = 0.45f)
            )
        }

        // Front closed EV fascia (Aiways U5 signature grille-less nose)
        addQuad(
            Vec3(1.06f, yLow, -wBody * 0.95f),
            Vec3(1.06f, yLow, wBody * 0.95f),
            Vec3(1.10f, yHoodFront, wBody * 0.92f),
            Vec3(1.10f, yHoodFront, -wBody * 0.92f),
            cMid
        )
        // Lower front black diffuser lip
        addQuad(
            Vec3(1.05f, yLow - 0.03f, -wBody * 0.85f),
            Vec3(1.05f, yLow - 0.03f, wBody * 0.85f),
            Vec3(1.07f, yLow + 0.05f, wBody * 0.88f),
            Vec3(1.07f, yLow + 0.05f, -wBody * 0.88f),
            trimColor
        )
        // Slim sharp Aiways U5 LED Headlights (Left & Right)
        for (side in listOf(-1f, 1f)) {
            val z1 = side * wBody * 0.46f
            val z2 = side * wBody * 0.90f
            addQuad(
                Vec3(1.11f, yHoodFront - 0.05f, min(z1, z2)),
                Vec3(1.11f, yHoodFront - 0.05f, max(z1, z2)),
                Vec3(1.09f, yHoodFront + 0.01f, max(z1, z2)),
                Vec3(1.09f, yHoodFront + 0.01f, min(z1, z2)),
                headlightColor,
                stroke = Color.White,
                sw = 2.0f,
                emissive = true
            )
        }

        // Sculpted SUV Hood
        addQuad(
            Vec3(0.42f, yBelt + 0.02f, -wBody * 0.95f),
            Vec3(0.42f, yBelt + 0.02f, wBody * 0.95f),
            Vec3(1.10f, yHoodFront, wBody * 0.92f),
            Vec3(1.10f, yHoodFront, -wBody * 0.92f),
            cLight
        )

        // Panoramic Front Windshield
        addQuad(
            Vec3(0.42f, yBelt + 0.02f, -wBody * 0.93f),
            Vec3(0.42f, yBelt + 0.02f, wBody * 0.93f),
            Vec3(0.16f, yRoof, wRoof),
            Vec3(0.16f, yRoof, -wRoof),
            glassColor,
            stroke = Color.White.copy(alpha = 0.40f)
        )

        // Floating Black Panoramic Roof
        addQuad(
            Vec3(-0.76f, yRoof, -wRoof),
            Vec3(-0.76f, yRoof, wRoof),
            Vec3(0.16f, yRoof, wRoof),
            Vec3(0.16f, yRoof, -wRoof),
            roofColor,
            stroke = Color.White.copy(alpha = 0.30f)
        )

        // Rear Windshield (sloped SUV tailgate)
        addQuad(
            Vec3(-1.05f, yBelt, -wBody * 0.93f),
            Vec3(-1.05f, yBelt, wBody * 0.93f),
            Vec3(-0.76f, yRoof, wRoof),
            Vec3(-0.76f, yRoof, -wRoof),
            glassColor,
            stroke = Color.White.copy(alpha = 0.32f)
        )

        // Rear Tailgate Body
        addQuad(
            Vec3(-1.08f, yLow, -wBody * 0.95f),
            Vec3(-1.08f, yLow, wBody * 0.95f),
            Vec3(-1.05f, yBelt, wBody * 0.95f),
            Vec3(-1.05f, yBelt, -wBody * 0.95f),
            cDark
        )

        // Signature Aiways U5 Full-Width Rear LED Light Bar!
        addQuad(
            Vec3(-1.075f, yBelt - 0.06f, -wBody * 0.90f),
            Vec3(-1.075f, yBelt - 0.06f, wBody * 0.90f),
            Vec3(-1.065f, yBelt - 0.01f, wBody * 0.90f),
            Vec3(-1.065f, yBelt - 0.01f, -wBody * 0.90f),
            taillightColor,
            stroke = Color(0xFFFF8A95),
            sw = 2.0f,
            emissive = true
        )

        // 4 Wheels (19-inch alloy octagons positioned at Aiways U5 2800mm wheelbase)
        val wheelPositions = listOf(
            Vec3(0.68f, -0.19f, wBody + 0.01f),
            Vec3(-0.68f, -0.19f, wBody + 0.01f),
            Vec3(0.68f, -0.19f, -wBody - 0.01f),
            Vec3(-0.68f, -0.19f, -wBody - 0.01f)
        )
        for (wp in wheelPositions) {
            val tirePts = mutableListOf<Vec3>()
            val rimPts = mutableListOf<Vec3>()
            for (k in 0 until 12) {
                val a = (k * 30.0) * PI / 180.0
                tirePts.add(Vec3(wp.x + cos(a).toFloat() * 0.175f, wp.y + sin(a).toFloat() * 0.175f, wp.z))
                rimPts.add(Vec3(wp.x + cos(a).toFloat() * 0.105f, wp.y + sin(a).toFloat() * 0.105f, wp.z + (if (wp.z > 0) 0.005f else -0.005f)))
            }
            faces.add(Face3D(tirePts, Color(0xFF0E1117), Color(0xFF475569), 1.8f))
            faces.add(Face3D(rimPts, Color(0xFF94A3B8), Color.White.copy(alpha = 0.85f), 1.6f))
        }

        // 3. Depth-sort faces (Painter's Algorithm) and render!
        val sorted = faces.map { f ->
            val tPts = f.points.map { transform(it) }
            val avgZ = tPts.map { it.z }.average().toFloat()
            Triple(f, tPts, avgZ)
        }.sortedByDescending { it.third }

        for ((face, tPts, _) in sorted) {
            val proj = tPts.map { project(it) }
            val path = Path().apply {
                moveTo(proj.first().x, proj.first().y)
                for (i in 1 until proj.size) {
                    lineTo(proj[i].x, proj[i].y)
                }
                close()
            }
            drawPath(path = path, color = face.fill)
            drawPath(path = path, color = face.stroke, style = Stroke(width = face.strokeWidth))
        }
    }
}
