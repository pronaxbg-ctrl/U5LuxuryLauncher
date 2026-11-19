@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.combinedClickable
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

data class DefaultTileConfig(
    val slotKey: String,
    val defaultTitle: String,
    val defaultPkg: String,
    val defaultIcon: ImageVector,
    val defaultGradient: List<Color>,
    val isSmsFallback: Boolean = false
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
    var showAppDrawer by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }
    var customizingSlotKey by remember { mutableStateOf<String?>(null) }
    var isNightDimmed by remember { mutableStateOf(false) }

    // Custom pinned packages for the 4 CarPlay tiles (persisted in SharedPreferences)
    var tile0Pkg by remember { mutableStateOf(prefs.getString("tile_0_pkg", null)) }
    var tile1Pkg by remember { mutableStateOf(prefs.getString("tile_1_pkg", null)) }
    var tile2Pkg by remember { mutableStateOf(prefs.getString("tile_2_pkg", null)) }
    var tile3Pkg by remember { mutableStateOf(prefs.getString("tile_3_pkg", null)) }

    // Shared Live GPS State (feeds Map, Speedometer, Bearing Arrow, and Live Weather)
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

    // Fetch live Open-Meteo weather on start & every 15 minutes
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
            delay(1000)
        }
    }

    val installedApps = remember { loadInstalledApps(context) }
    val appByPkg = remember(installedApps) { installedApps.associateBy { it.packageName } }

    val defaultTiles = remember {
        listOf(
            DefaultTileConfig("tile_0_pkg", "Spotify", "com.spotify.music", Icons.Rounded.GraphicEq, listOf(Color(0xFF1ED760), Color(0xFF15803D))),
            DefaultTileConfig("tile_1_pkg", "YT Music", "com.google.android.apps.youtube.music", Icons.Rounded.PlayArrow, listOf(Color(0xFFFF3B30), Color(0xFFB91C1C))),
            DefaultTileConfig("tile_2_pkg", "Messages", "", Icons.Rounded.ChatBubble, listOf(Color(0xFF34C759), Color(0xFF16A34A)), isSmsFallback = true),
            DefaultTileConfig("tile_3_pkg", "Settings", "com.android.settings", Icons.Rounded.Settings, listOf(Color(0xFF8E8E93), Color(0xFF48484A)))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(theme.bgTop, theme.bgBottom)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. LEFT CARPLAY-STYLE DOCK (Clock + Live Weather + Apps + Theme)
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

            // 2. CENTER-LEFT: NATIVE GOOGLE MAPS + SPEEDOMETER + ROTATING ARROW + 1-TAP NAV SHORTCUTS
            EmbeddedNavigationMapCard(
                carLat = carLat,
                carLon = carLon,
                carSpeedKmh = carSpeedKmh,
                carBearing = carBearing,
                theme = theme,
                finish = finish,
                modifier = Modifier
                    .weight(1.22f)
                    .fillMaxHeight(),
                onOpenGoogleMaps = { launchPackage(context, "com.google.android.apps.maps") },
                onNavigateQuery = { query -> launchGoogleMapsNavigation(context, query) }
            )

            // 3. RIGHT COLUMN: DYNAMIC-COLOR ONE UI MUSIC PLAYER + QUICK CONTROLS + CUSTOMIZABLE 2x2 TILES
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OneUiStyleMusicCard(
                    mediaState = mediaState,
                    theme = theme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.26f),
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

                // Compact Quick Cockpit Strip (Volume - / + & 1-Tap Night Dimming)
                QuickCockpitControlStrip(
                    theme = theme,
                    finish = finish,
                    isNightDimmed = isNightDimmed,
                    onVolDown = {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI
                        )
                    },
                    onVolUp = {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    },
                    onToggleNightDim = { isNightDimmed = !isNightDimmed }
                )

                // Row 1: Customizable CarPlay Tiles (Long-press to swap app!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.76f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CustomizableCarPlayTile(
                        config = defaultTiles[0],
                        customApp = tile0Pkg?.let { appByPkg[it] },
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val pkg = tile0Pkg ?: defaultTiles[0].defaultPkg
                            launchPackage(context, pkg)
                        },
                        onLongClick = { customizingSlotKey = defaultTiles[0].slotKey }
                    )
                    CustomizableCarPlayTile(
                        config = defaultTiles[1],
                        customApp = tile1Pkg?.let { appByPkg[it] },
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val pkg = tile1Pkg ?: defaultTiles[1].defaultPkg
                            launchPackage(context, pkg)
                        },
                        onLongClick = { customizingSlotKey = defaultTiles[1].slotKey }
                    )
                }

                // Row 2: Customizable CarPlay Tiles (Long-press to swap app!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.76f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CustomizableCarPlayTile(
                        config = defaultTiles[2],
                        customApp = tile2Pkg?.let { appByPkg[it] },
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (tile2Pkg != null) launchPackage(context, tile2Pkg!!)
                            else launchSmsApp(context)
                        },
                        onLongClick = { customizingSlotKey = defaultTiles[2].slotKey }
                    )
                    CustomizableCarPlayTile(
                        config = defaultTiles[3],
                        customApp = tile3Pkg?.let { appByPkg[it] },
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val pkg = tile3Pkg ?: defaultTiles[3].defaultPkg
                            launchPackage(context, pkg)
                        },
                        onLongClick = { customizingSlotKey = defaultTiles[3].slotKey }
                    )
                }
            }
        }

        // All Apps Drawer (or Tile Customization Picker when long-pressing a tile)
        if (showAppDrawer || customizingSlotKey != null) {
            val isPickingTile = customizingSlotKey != null
            AllAppsLuxuryOverlay(
                title = if (isPickingTile) "Choose App for Tile" else "Applications",
                apps = installedApps,
                theme = theme,
                finish = finish,
                onAppClick = { pkg ->
                    if (isPickingTile) {
                        val key = customizingSlotKey!!
                        prefs.edit().putString(key, pkg).apply()
                        when (key) {
                            "tile_0_pkg" -> tile0Pkg = pkg
                            "tile_1_pkg" -> tile1Pkg = pkg
                            "tile_2_pkg" -> tile2Pkg = pkg
                            "tile_3_pkg" -> tile3Pkg = pkg
                        }
                        customizingSlotKey = null
                    } else {
                        showAppDrawer = false
                        launchPackage(context, pkg)
                    }
                },
                onClose = {
                    showAppDrawer = false
                    customizingSlotKey = null
                }
            )
        }

        if (showThemeModal) {
            ThemeStudioOverlay(
                currentTheme = theme,
                currentFinish = finish,
                onSelectTheme = { selected ->
                    theme = selected
                    prefs.edit().putString("theme_id", selected.id).apply()
                },
                onSelectFinish = { selected ->
                    finish = selected
                    prefs.edit().putString("finish_id", selected.id).apply()
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
            .width(96.dp)
            .fillMaxHeight()
            .luxuryCardSurface(theme, finish, cornerRadius = 28.dp)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Clock + Live GPS Weather Pill
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            if (weather?.tempC != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = weather.icon,
                        contentDescription = "Weather",
                        tint = theme.accentGlow,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${weather.tempC}°",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(26.dp)
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
            .size(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun QuickCockpitControlStrip(
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    isNightDimmed: Boolean,
    onVolDown: () -> Unit,
    onVolUp: () -> Unit,
    onToggleNightDim: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .luxuryCardSurface(theme, finish, cornerRadius = 18.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickStripChip(icon = Icons.Rounded.VolumeDown, label = "Vol -", onClick = onVolDown)
            QuickStripChip(icon = Icons.Rounded.VolumeUp, label = "Vol +", onClick = onVolUp)
        }

        QuickStripChip(
            icon = if (isNightDimmed) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            label = if (isNightDimmed) "Bright" else "Night Dim",
            highlighted = isNightDimmed,
            accent = theme.accent,
            onClick = onToggleNightDim
        )
    }
}

@Composable
fun QuickStripChip(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    accent: Color = Color(0xFF38BDF8),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (highlighted) accent else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EmbeddedNavigationMapCard(
    carLat: Double,
    carLon: Double,
    carSpeedKmh: Int,
    carBearing: Float,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    modifier: Modifier = Modifier,
    onOpenGoogleMaps: () -> Unit,
    onNavigateQuery: (String) -> Unit
) {
    var isSatellite by remember { mutableStateOf(false) }
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
            .luxuryCardSurface(theme, finish, cornerRadius = 28.dp)
            .clip(RoundedCornerShape(28.dp))
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

        // Top Floating Bar: Google Maps + Sat/Dark + Navigate
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE011141B))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                    .clickable { onOpenGoogleMaps() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = "Google Maps",
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Google Maps",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xE011141B))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                        .clickable { isSatellite = !isSatellite }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isSatellite) "Dark" else "Sat",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xEB1A73E8))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                        .clickable { onOpenGoogleMaps() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "Navigate",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Navigate",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom-Left: Live GPS Speedometer Pill + 1-Tap Nav Shortcuts (Home, Work, EV Charger)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live Speedometer Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xE60B0F17))
                    .border(1.dp, theme.accent.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "$carSpeedKmh",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "km/h",
                    color = theme.accentGlow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // 1-Tap Navigation Shortcuts
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavShortcutPill(icon = Icons.Rounded.Home, label = "Home") {
                    onNavigateQuery("Home")
                }
                NavShortcutPill(icon = Icons.Rounded.Work, label = "Work") {
                    onNavigateQuery("Work")
                }
                NavShortcutPill(icon = Icons.Rounded.EvStation, label = "EV Charger", tint = Color(0xFF4ADE80)) {
                    onNavigateQuery("EV charging station")
                }
            }
        }

        // Bottom-Right Floating Map Controls (+, -, Re-center GPS)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
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
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE011141B))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
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
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE011141B))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun OneUiStyleMusicCard(
    mediaState: LiveMediaState,
    theme: LuxuryThemeType,
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

    // Dynamic album-art dominant color extraction for the glowing wave bar
    val (wavePrimary, waveGlow) = remember(mediaState.albumArt) {
        extractVibrantWaveColors(mediaState.albumArt)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF141214))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
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
                    fontSize = 14.sp,
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
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ENABLE SYNC",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Speaker,
                        contentDescription = "Audio Output",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(20.dp)
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
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${mediaState.title}  •  ${mediaState.artist}",
                    color = Color.White,
                    fontSize = 18.sp,
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
                        .height(28.dp),
                    onSeekFraction = onSeekFraction
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(mediaState.positionMs),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDuration(mediaState.durationMs),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
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
                        .size(24.dp)
                        .clickable { onOpenMusicApp() }
                )

                OneUiCircleButton(
                    icon = Icons.Rounded.SkipPrevious,
                    size = 48.dp,
                    iconSize = 26.dp,
                    onClick = onPrev
                )

                OneUiCircleButton(
                    icon = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    size = 54.dp,
                    iconSize = 30.dp,
                    onClick = onPlayPause
                )

                OneUiCircleButton(
                    icon = Icons.Rounded.SkipNext,
                    size = 48.dp,
                    iconSize = 26.dp,
                    onClick = onNext
                )

                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .size(24.dp)
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
fun CustomizableCarPlayTile(
    config: DefaultTileConfig,
    customApp: InstalledApp?,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .luxuryButtonSurface(theme, finish, cornerRadius = 22.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (customApp != null) {
            Image(
                bitmap = customApp.icon,
                contentDescription = customApp.label,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Text(
                text = customApp.label,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(config.defaultGradient))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = config.defaultIcon,
                    contentDescription = config.defaultTitle,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = config.defaultTitle,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                                .size(58.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 15.sp,
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
    onSelectTheme: (LuxuryThemeType) -> Unit,
    onSelectFinish: (ButtonFinish) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedVersion = remember { LauncherUpdater.getCurrentVersionName(context) }
    var updateStatus by remember { mutableStateOf("Installed: v$installedVersion  •  Long-press any tile to swap apps") }
    var isUpdating by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB07080B))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .luxuryCardSurface(currentTheme, currentFinish, cornerRadius = 28.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cockpit Theme Studio",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                CarPlayDockBadge(
                    icon = Icons.Rounded.Close,
                    gradient = listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)),
                    onClick = onClose
                )
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
                            fontSize = 15.sp,
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
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = f.displayName,
                            color = if (selected) currentTheme.accentGlow else Color.White,
                            fontSize = 16.sp,
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
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOFTWARE UPDATE",
                        color = currentTheme.accentGlow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = updateStatus,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = "Check for Updates",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isUpdating) "Updating..." else "Check for Updates",
                        color = Color.White,
                        fontSize = 15.sp,
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

fun launchSmsApp(context: Context) {
    val defaultSms = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MESSAGING)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (defaultSms.resolveActivity(context.packageManager) != null) {
        context.startActivity(defaultSms)
        return
    }
    val fallbacks = listOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms"
    )
    for (pkg in fallbacks) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
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
