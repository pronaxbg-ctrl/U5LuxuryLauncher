package com.u5.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            U5LuxuryCockpit()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        MediaRepository.refreshSessions(this)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@SuppressLint("MissingPermission")
@Composable
fun U5LuxuryCockpit() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("u5_luxury_prefs", Context.MODE_PRIVATE) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var theme by remember {
        mutableStateOf(LuxuryThemeType.fromId(prefs.getString("theme_id", null)))
    }
    var finish by remember {
        mutableStateOf(ButtonFinish.fromId(prefs.getString("finish_id", null)))
    }
    var isWhiteWatchDial by remember {
        mutableStateOf(prefs.getBoolean("watch_white_dial", true))
    }
    var showAppDrawer by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }
    var isNightDimmed by remember { mutableStateOf(false) }
    var isSatellite by remember { mutableStateOf(false) }

    // Live audio volume percentage for the OEM bottom bar
    fun readVolumePct(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((cur * 100f) / max).roundToInt().coerceIn(0, 100)
    }

    var volumePct by remember { mutableIntStateOf(readVolumePct()) }
    var isMuted by remember { mutableStateOf(volumePct == 0) }
    var preMuteVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(3)) }

    // Shared Live GPS State (Map + Speedometer + Rotating Arrow + Weather)
    var carLat by remember { mutableDoubleStateOf(55.7093) }
    var carLon by remember { mutableDoubleStateOf(9.5357) }
    var carSpeedKmh by remember { mutableIntStateOf(0) }
    var carBearing by remember { mutableFloatStateOf(0f) }
    var liveWeather by remember { mutableStateOf<LiveWeather?>(null) }

    DisposableEffect(context) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                carLat = location.latitude
                carLon = location.longitude
                if (location.hasSpeed()) {
                    carSpeedKmh = (location.speed * 3.6f).roundToInt().coerceAtLeast(0)
                }
                if (location.hasBearing() && location.speed > 1.2f) {
                    carBearing = location.bearing
                }
            }
        }

        if ((hasFine || hasCoarse) && locManager != null) {
            runCatching {
                val best = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (best != null) {
                    carLat = best.latitude
                    carLon = best.longitude
                }
                if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 2f, listener)
                }
                if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 15f, listener)
                }
            }
        }

        onDispose {
            runCatching { locManager?.removeUpdates(listener) }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val w = WeatherService.fetchCurrentWeather(carLat, carLon)
            if (w != null) liveWeather = w
            delay(15 * 60 * 1000L)
        }
    }

    val mediaState by MediaRepository.state.collectAsState()
    var currentTime by remember { mutableStateOf("18:10") }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = timeFmt.format(Date())
            MediaRepository.refreshSessions(context)
            volumePct = readVolumePct()
            isMuted = volumePct == 0
            delay(1000)
        }
    }

    val installedApps = remember { loadInstalledApps(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(theme.bgTop, theme.bgBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // MAIN COCKPIT ROW (Dock + Native Map + Right Stack: Music & Luxury Watch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. LEFT CARPLAY-STYLE DOCK
                CarPlayLuxuryDock(
                    time = currentTime,
                    weather = liveWeather,
                    theme = theme,
                    finish = finish,
                    onMapsClick = { launchPackage(context, "com.google.android.apps.maps") },
                    onSpotifyClick = { launchPackage(context, "com.spotify.music") },
                    onYtMusicClick = { launchPackage(context, "com.google.android.apps.youtube.music") },
                    onAppsClick = { showAppDrawer = true },
                    onThemeClick = { showThemeModal = true }
                )

                // 2. CENTER-LEFT: NATIVE GOOGLE MAPS + SPEEDOMETER + NAV SHORTCUTS
                EmbeddedNavigationMapCard(
                    carLat = carLat,
                    carLon = carLon,
                    carSpeedKmh = carSpeedKmh,
                    carBearing = carBearing,
                    isSatellite = isSatellite,
                    onToggleSatellite = { isSatellite = !isSatellite },
                    theme = theme,
                    finish = finish,
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight(),
                    onOpenGoogleMaps = { launchPackage(context, "com.google.android.apps.maps") },
                    onNavigateQuery = { query -> launchGoogleMapsNavigation(context, query) }
                )

                // 3. RIGHT COLUMN: ONE UI MUSIC PLAYER (TOP) + LUXURY CHRONOGRAPH WATCH (BOTTOM)
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OneUiStyleMusicCard(
                        mediaState = mediaState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.02f),
                        onPlayPause = { MediaRepository.togglePlayPause(context) },
                        onPrev = { MediaRepository.skipPrevious(context) },
                        onNext = { MediaRepository.skipNext(context) },
                        onSeekFraction = { frac -> MediaRepository.seekToFraction(context, frac) },
                        onOpenAccess = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        },
                        onOpenMusicApp = {
                            if (mediaState.sourceApp.contains("YT", ignoreCase = true)) {
                                launchPackage(context, "com.google.android.apps.youtube.music")
                            } else {
                                launchPackage(context, "com.spotify.music")
                            }
                        }
                    )

                    LuxuryChronographCard(
                        isWhiteDial = isWhiteWatchDial,
                        theme = theme,
                        finish = finish,
                        onToggleDial = {
                            isWhiteWatchDial = !isWhiteWatchDial
                            prefs.edit().putBoolean("watch_white_dial", isWhiteWatchDial).apply()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.10f)
                    )
                }
            }

            // 4. SLEEK OEM-STYLE AUTOMOTIVE BOTTOM CONTROL BAR (Inspired by uploaded climate bar)
            OemAutomotiveControlBar(
                volumePct = volumePct,
                isMuted = isMuted,
                isNightDimmed = isNightDimmed,
                isSatellite = isSatellite,
                isWhiteWatchDial = isWhiteWatchDial,
                theme = theme,
                onVolDown = {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                    volumePct = readVolumePct()
                    isMuted = volumePct == 0
                },
                onVolUp = {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                    volumePct = readVolumePct()
                    isMuted = volumePct == 0
                },
                onToggleMute = {
                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (cur > 0) {
                        preMuteVolume = cur
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    } else {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, preMuteVolume.coerceAtLeast(3), 0)
                    }
                    volumePct = readVolumePct()
                    isMuted = volumePct == 0
                },
                onToggleNightDim = { isNightDimmed = !isNightDimmed },
                onToggleSatellite = { isSatellite = !isSatellite },
                onToggleWatchDial = {
                    isWhiteWatchDial = !isWhiteWatchDial
                    prefs.edit().putBoolean("watch_white_dial", isWhiteWatchDial).apply()
                },
                onOpenApps = { showAppDrawer = true },
                onOpenSettings = { launchPackage(context, "com.android.settings") },
                onOpenTheme = { showThemeModal = true }
            )
        }

        if (showAppDrawer) {
            AllAppsLuxuryOverlay(
                title = "Applications",
                apps = installedApps,
                theme = theme,
                finish = finish,
                onAppClick = { pkg ->
                    showAppDrawer = false
                    launchPackage(context, pkg)
                },
                onClose = { showAppDrawer = false }
            )
        }

        if (showThemeModal) {
            ThemeStudioOverlay(
                currentTheme = theme,
                currentFinish = finish,
                isWhiteWatchDial = isWhiteWatchDial,
                onSelectTheme = { selected ->
                    theme = selected
                    prefs.edit().putString("theme_id", selected.id).apply()
                },
                onSelectFinish = { selected ->
                    finish = selected
                    prefs.edit().putString("finish_id", selected.id).apply()
                },
                onToggleWatchDial = {
                    isWhiteWatchDial = !isWhiteWatchDial
                    prefs.edit().putBoolean("watch_white_dial", isWhiteWatchDial).apply()
                },
                onClose = { showThemeModal = false }
            )
        }

        // 1-Tap Night Dimming Filter Overlay (non-blocking visual dim for night driving)
        if (isNightDimmed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
            )
        }
    }
}

@Composable
fun OemAutomotiveControlBar(
    volumePct: Int,
    isMuted: Boolean,
    isNightDimmed: Boolean,
    isSatellite: Boolean,
    isWhiteWatchDial: Boolean,
    theme: LuxuryThemeType,
    onVolDown: () -> Unit,
    onVolUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleNightDim: () -> Unit,
    onToggleSatellite: () -> Unit,
    onToggleWatchDial: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTheme: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xE824272E), Color(0xF514161B))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT CLUSTER: Mute + OEM Blue (-) / Volume Readout / Red (+)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                OemThinAction(
                    icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    label = "MUTE",
                    active = isMuted,
                    activeColor = Color(0xFFEF4444),
                    onClick = onToggleMute
                )

                // Classic Automotive Cool-Blue (-) | Digital Level | Warm-Red (+)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onVolDown() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            color = Color(0xFF4F8FF7),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = if (isMuted) "OFF" else "$volumePct%",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onVolUp() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = Color(0xFFE05252),
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                OemStatusTextButton(
                    label = "DIM",
                    active = isNightDimmed,
                    activeColor = theme.accentGlow,
                    onClick = onToggleNightDim
                )
            }

            // CENTER ILLUMINATED OEM CAPSULE (Matches the center highlighted module in uploaded bar)
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.05f),
                                Color.White.copy(alpha = 0.14f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.34f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onOpenApps() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.75f))
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = "All Apps",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // RIGHT CLUSTER: Map Mode (SAT), Watch Dial (DIAL), Theme Studio, Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OemStatusTextButton(
                    label = if (isSatellite) "SAT" else "DARK",
                    active = isSatellite,
                    activeColor = Color(0xFF38BDF8),
                    onClick = onToggleSatellite
                )

                OemStatusTextButton(
                    label = if (isWhiteWatchDial) "SILVER" else "BLACK",
                    active = isWhiteWatchDial,
                    activeColor = Color.White,
                    onClick = onToggleWatchDial
                )

                OemThinAction(
                    icon = Icons.Rounded.Palette,
                    label = "THEME",
                    active = false,
                    activeColor = theme.accent,
                    onClick = onOpenTheme
                )

                OemThinAction(
                    icon = Icons.Rounded.Settings,
                    label = "SETUP",
                    active = false,
                    activeColor = Color.White,
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
fun OemThinAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) activeColor else Color.White.copy(alpha = 0.80f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) activeColor else Color.White.copy(alpha = 0.22f)
                        )
                )
            }
        }
    }
}

@Composable
fun OemStatusTextButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.78f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) activeColor else Color.White.copy(alpha = 0.22f)
                        )
                )
            }
        }
    }
}

@Composable
fun CarPlayLuxuryDock(
    time: String,
    weather: LiveWeather?,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    onMapsClick: () -> Unit,
    onSpotifyClick: () -> Unit,
    onYtMusicClick: () -> Unit,
    onAppsClick: () -> Unit,
    onThemeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(86.dp)
            .fillMaxHeight()
            .luxuryCardSurface(theme, finish, cornerRadius = 26.dp)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            if (weather?.tempC != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = weather.icon,
                        contentDescription = "Weather",
                        tint = theme.accentGlow,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${weather.tempC}°",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .width(24.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(theme.accent)
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CarPlayDockBadge(
                icon = Icons.Rounded.Navigation,
                gradient = listOf(Color(0xFF34A853), Color(0xFF1A73E8)),
                onClick = onMapsClick
            )
            CarPlayDockBadge(
                icon = Icons.Rounded.GraphicEq,
                gradient = listOf(Color(0xFF1ED760), Color(0xFF15803D)),
                onClick = onSpotifyClick
            )
            CarPlayDockBadge(
                icon = Icons.Rounded.PlayArrow,
                gradient = listOf(Color(0xFFFF3B30), Color(0xFFB91C1C)),
                onClick = onYtMusicClick
            )
            CarPlayDockBadge(
                icon = Icons.Rounded.GridView,
                gradient = listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)),
                onClick = onAppsClick
            )
        }

        CarPlayDockBadge(
            icon = Icons.Rounded.Palette,
            gradient = listOf(theme.accent, theme.accentSecondary),
            onClick = onThemeClick
        )
    }
}

@Composable
fun CarPlayDockBadge(
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
fun EmbeddedNavigationMapCard(
    carLat: Double,
    carLon: Double,
    carSpeedKmh: Int,
    carBearing: Float,
    isSatellite: Boolean,
    onToggleSatellite: () -> Unit,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    modifier: Modifier = Modifier,
    onOpenGoogleMaps: () -> Unit,
    onNavigateQuery: (String) -> Unit
) {
    var zoom by remember { mutableIntStateOf(15) }
    var followCar by remember { mutableStateOf(true) }
    var centerLat by remember { mutableDoubleStateOf(carLat) }
    var centerLon by remember { mutableDoubleStateOf(carLon) }

    LaunchedEffect(carLat, carLon, followCar) {
        if (followCar) {
            centerLat = carLat
            centerLon = carLon
        }
    }

    Box(
        modifier = modifier
            .luxuryCardSurface(theme, finish, cornerRadius = 26.dp)
            .clip(RoundedCornerShape(26.dp))
    ) {
        NativeGoogleMapView(
            centerLat = centerLat,
            centerLon = centerLon,
            carLat = carLat,
            carLon = carLon,
            carBearing = carBearing,
            zoom = zoom,
            isSatellite = isSatellite,
            onPan = { newLat, newLon ->
                followCar = false
                centerLat = newLat
                centerLon = newLon
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Floating Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xE011141B))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                    .clickable { onOpenGoogleMaps() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = "Google Maps",
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Google Maps",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE011141B))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                        .clickable { onToggleSatellite() }
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isSatellite) "Dark" else "Sat",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xEB1A73E8))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                        .clickable { onOpenGoogleMaps() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "Navigate",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Navigate",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom-Left: Speedometer Pill + 1-Tap Nav Shortcuts
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE60B0F17))
                    .border(1.dp, theme.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "$carSpeedKmh",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "km/h",
                    color = theme.accentGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NavShortcutPill(icon = Icons.Rounded.Home, label = "Home") {
                    onNavigateQuery("Home")
                }
                NavShortcutPill(icon = Icons.Rounded.Work, label = "Work") {
                    onNavigateQuery("Work")
                }
                NavShortcutPill(icon = Icons.Rounded.EvStation, label = "EV", tint = Color(0xFF4ADE80)) {
                    onNavigateQuery("EV charging station")
                }
            }
        }

        // Bottom-Right Floating Map Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControlPill(icon = Icons.Rounded.Add) {
                zoom = (zoom + 1).coerceAtMost(19)
            }
            MapControlPill(icon = Icons.Rounded.Remove) {
                zoom = (zoom - 1).coerceAtLeast(4)
            }
            MapControlPill(icon = Icons.Rounded.MyLocation, accent = Color(0xFF38BDF8)) {
                followCar = true
                centerLat = carLat
                centerLon = carLon
            }
        }
    }
}

@Composable
fun NavShortcutPill(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE011141B))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MapControlPill(
    icon: ImageVector,
    accent: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE011141B))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun OneUiStyleMusicCard(
    mediaState: LiveMediaState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onOpenAccess: () -> Unit,
    onOpenMusicApp: () -> Unit
) {
    val progress = if (mediaState.durationMs > 0L) {
        (mediaState.positionMs.toFloat() / mediaState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val (wavePrimary, waveGlow) = remember(mediaState.albumArt) {
        extractVibrantWaveColors(mediaState.albumArt)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF141214))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
    ) {
        if (mediaState.albumArt != null) {
            Image(
                bitmap = mediaState.albumArt.asImageBitmap(),
                contentDescription = "Album Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xB80A090B),
                            Color(0xCC0D0A0C),
                            Color(0xE6080709)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mediaState.sourceApp,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!mediaState.hasListenerAccess) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF2D20).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFFFF3B30), RoundedCornerShape(10.dp))
                            .clickable { onOpenAccess() }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "ENABLE SYNC",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Speaker,
                        contentDescription = "Audio Output",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(19.dp)
                            .clickable { onOpenMusicApp() }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMusicApp() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "${mediaState.title}  •  ${mediaState.artist}",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                OneUiWaveProgressBar(
                    progress = progress,
                    isPlaying = mediaState.isPlaying,
                    wavePrimary = wavePrimary,
                    waveGlow = waveGlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    onSeekFraction = onSeekFraction
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(mediaState.positionMs),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDuration(mediaState.durationMs),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ThumbUpOffAlt,
                    contentDescription = "Like",
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onOpenMusicApp() }
                )

                OneUiCircleButton(
                    icon = Icons.Rounded.SkipPrevious,
                    size = 44.dp,
                    iconSize = 24.dp,
                    onClick = onPrev
                )

                OneUiCircleButton(
                    icon = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    size = 50.dp,
                    iconSize = 28.dp,
                    onClick = onPlayPause
                )

                OneUiCircleButton(
                    icon = Icons.Rounded.SkipNext,
                    size = 44.dp,
                    iconSize = 24.dp,
                    onClick = onNext
                )

                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onOpenMusicApp() }
                )
            }
        }
    }
}

@Composable
fun OneUiCircleButton(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun AllAppsLuxuryOverlay(
    title: String,
    apps: List<InstalledApp>,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    onAppClick: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB07080B))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                CarPlayDockBadge(
                    icon = Icons.Rounded.Close,
                    gradient = listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)),
                    onClick = onClose
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(apps) { app ->
                    Column(
                        modifier = Modifier
                            .luxuryButtonSurface(theme, finish, cornerRadius = 22.dp)
                            .clickable { onAppClick(app.packageName) }
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeStudioOverlay(
    currentTheme: LuxuryThemeType,
    currentFinish: ButtonFinish,
    isWhiteWatchDial: Boolean,
    onSelectTheme: (LuxuryThemeType) -> Unit,
    onSelectFinish: (ButtonFinish) -> Unit,
    onToggleWatchDial: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedVersion = remember { LauncherUpdater.getCurrentVersionName(context) }
    var updateStatus by remember { mutableStateOf("Installed: v$installedVersion  •  Tap watch to switch Silver/Black dial") }
    var isUpdating by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB07080B))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .luxuryCardSurface(currentTheme, currentFinish, cornerRadius = 28.dp)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cockpit Theme Studio",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, currentTheme.border, RoundedCornerShape(14.dp))
                            .clickable { onToggleWatchDial() }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isWhiteWatchDial) "Watch: Silver Dial" else "Watch: Black Dial",
                            color = Color.White,
                            fontSize = 14.sp,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LuxuryThemeType.entries.forEach { t ->
                    val selected = t == currentTheme
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(t.surfaceElevated)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) t.accentGlow else t.border,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onSelectTheme(t) }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(t.accent, t.accentGlow)))
                        )
                        Text(
                            text = t.displayName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ButtonFinish.entries.forEach { f ->
                    val selected = f == currentFinish
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .luxuryButtonSurface(currentTheme, f, cornerRadius = 16.dp, highlighted = selected)
                            .clickable { onSelectFinish(f) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = f.displayName,
                            color = if (selected) currentTheme.accentGlow else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 1-Tap Built-In OTA Updater Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, currentTheme.border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOFTWARE UPDATE",
                        color = currentTheme.accentGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = updateStatus,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(currentTheme.accent, currentTheme.accentSecondary)
                            )
                        )
                        .clickable(enabled = !isUpdating) {
                            isUpdating = true
                            scope.launch {
                                LauncherUpdater.checkAndInstallUpdate(context) { status ->
                                    updateStatus = status
                                }
                                isUpdating = false
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = "Check for Updates",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = if (isUpdating) "Updating..." else "Check for Updates",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun Modifier.luxuryCardSurface(
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    cornerRadius: Dp
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = when (finish) {
        ButtonFinish.SCULPTED_METAL -> Brush.linearGradient(listOf(theme.surfaceElevated, theme.surface))
        ButtonFinish.SMOKED_GLASS -> Brush.linearGradient(
            listOf(theme.surface.copy(alpha = 0.82f), theme.bgTop.copy(alpha = 0.90f))
        )
        ButtonFinish.AMBIENT_EDGE -> Brush.linearGradient(listOf(theme.bgTop, theme.surface))
    }
    return this
        .clip(shape)
        .background(bgBrush)
        .border(1.dp, theme.border, shape)
}

fun Modifier.luxuryButtonSurface(
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    cornerRadius: Dp,
    highlighted: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = when (finish) {
        ButtonFinish.SCULPTED_METAL -> Brush.verticalGradient(listOf(theme.surfaceElevated, theme.surface))
        ButtonFinish.SMOKED_GLASS -> Brush.verticalGradient(
            listOf(theme.surfaceElevated.copy(alpha = 0.68f), theme.bgTop.copy(alpha = 0.84f))
        )
        ButtonFinish.AMBIENT_EDGE -> Brush.verticalGradient(listOf(theme.surface, theme.bgTop))
    }
    val borderColor = when {
        highlighted -> theme.accentGlow.copy(alpha = 0.85f)
        finish == ButtonFinish.AMBIENT_EDGE -> theme.accent.copy(alpha = 0.65f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    return this
        .clip(shape)
        .background(bgBrush)
        .border(1.dp, borderColor, shape)
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

fun launchGoogleMapsNavigation(context: Context, query: String) {
    val uri = Uri.parse("google.navigation:q=" + Uri.encode(query))
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        launchPackage(context, "com.google.android.apps.maps")
    }
}

fun launchPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolved = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
    return resolved
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            InstalledApp(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm).toImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
