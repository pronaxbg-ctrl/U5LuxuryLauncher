package com.u5.carlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

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

@Composable
fun U5LuxuryCockpit() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("u5_luxury_prefs", Context.MODE_PRIVATE) }

    var theme by remember {
        mutableStateOf(LuxuryThemeType.fromId(prefs.getString("theme_id", null)))
    }
    var finish by remember {
        mutableStateOf(ButtonFinish.fromId(prefs.getString("finish_id", null)))
    }
    var showAppDrawer by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }

    val mediaState by MediaRepository.state.collectAsState()
    var currentTime by remember { mutableStateOf("18:10") }

    // Live 1-second clock + media progress poll
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTime = timeFmt.format(Date())
            MediaRepository.refreshSessions(context)
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. LEFT CARPLAY-STYLE DOCK
            CarPlayLuxuryDock(
                time = currentTime,
                theme = theme,
                finish = finish,
                onMapsClick = { launchPackage(context, "com.google.android.apps.maps") },
                onSpotifyClick = { launchPackage(context, "com.spotify.music") },
                onYtMusicClick = { launchPackage(context, "com.google.android.apps.youtube.music") },
                onAppsClick = { showAppDrawer = true },
                onThemeClick = { showThemeModal = true }
            )

            // 2. CENTER-LEFT: EMBEDDED LIVE INTERACTIVE MAP CARD
            EmbeddedNavigationMapCard(
                theme = theme,
                finish = finish,
                modifier = Modifier
                    .weight(1.22f)
                    .fillMaxHeight(),
                onOpenGoogleMaps = { launchPackage(context, "com.google.android.apps.maps") }
            )

            // 3. RIGHT COLUMN: ONE UI MUSIC PLAYER (TOP) + CARPLAY 2x2 APPS (BOTTOM)
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top-Right: Samsung One UI / Uploaded Screenshot Style Music Card
                OneUiStyleMusicCard(
                    mediaState = mediaState,
                    theme = theme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.28f),
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

                // Bottom-Right Row 1: CarPlay-style Spotify & YT Music
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.86f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CarPlayAppTile(
                        title = "Spotify",
                        icon = Icons.Rounded.GraphicEq,
                        badgeGradient = listOf(Color(0xFF1ED760), Color(0xFF15803D)),
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = { launchPackage(context, "com.spotify.music") }
                    )
                    CarPlayAppTile(
                        title = "YT Music",
                        icon = Icons.Rounded.PlayArrow,
                        badgeGradient = listOf(Color(0xFFFF3B30), Color(0xFFB91C1C)),
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = { launchPackage(context, "com.google.android.apps.youtube.music") }
                    )
                }

                // Bottom-Right Row 2: CarPlay-style Messages & Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.86f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CarPlayAppTile(
                        title = "Messages",
                        icon = Icons.Rounded.ChatBubble,
                        badgeGradient = listOf(Color(0xFF34C759), Color(0xFF16A34A)),
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = { launchSmsApp(context) }
                    )
                    CarPlayAppTile(
                        title = "Settings",
                        icon = Icons.Rounded.Settings,
                        badgeGradient = listOf(Color(0xFF8E8E93), Color(0xFF48484A)),
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = { launchPackage(context, "com.android.settings") }
                    )
                }
            }
        }

        // OVERLAY: ALL APPS DRAWER
        if (showAppDrawer) {
            AllAppsLuxuryOverlay(
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

        // OVERLAY: THEME STUDIO
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
    }
}

@Composable
fun CarPlayLuxuryDock(
    time: String,
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
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Crisp CarPlay Clock
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .width(26.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(theme.accent)
            )
        }

        // CarPlay Rounded-Square App Icons
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

        // Theme Switcher
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
            .size(56.dp)
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
            modifier = Modifier.size(30.dp)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedNavigationMapCard(
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    modifier: Modifier = Modifier,
    onOpenGoogleMaps: () -> Unit
) {
    Box(
        modifier = modifier
            .luxuryCardSurface(theme, finish, cornerRadius = 28.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        // Interactive Dark-Mode Map WebView (pan/zoom directly inside the launcher)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setGeolocationEnabled(true)
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?
                        ) {
                            callback?.invoke(origin, true, false)
                        }
                    }
                    loadDataWithBaseURL(
                        "https://unpkg.com",
                        DARK_COCKPIT_MAP_HTML,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )

        // Top Floating CarPlay-style Navigation Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xD911141B))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                    .clickable { onOpenGoogleMaps() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = "Maps",
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Google Maps",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Fullscreen Google Maps Launch Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE61A73E8))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                    .clickable { onOpenGoogleMaps() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = "Open Full Maps",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Navigate",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF141214))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
    ) {
        // Full-bleed Album Art Background (matching uploaded Samsung widget)
        if (mediaState.albumArt != null) {
            Image(
                bitmap = mediaState.albumArt.asImageBitmap(),
                contentDescription = "Album Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Smooth dark cinematic gradient overlay for high legibility
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
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Device/App Name + Audio Output Icon (or Live Sync Prompt)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mediaState.sourceApp,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 15.sp,
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
                            .size(22.dp)
                            .clickable { onOpenMusicApp() }
                    )
                }
            }

            // Center Row: Play Badge + "Title • Artist"
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${mediaState.title}  •  ${mediaState.artist}",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Glowing One UI Wave Progress Bar + Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                OneUiWaveProgressBar(
                    progress = progress,
                    isPlaying = mediaState.isPlaying,
                    wavePrimary = Color(0xFFFF1E00),
                    waveGlow = Color(0xFFFF8A3D),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    onSeekFraction = onSeekFraction
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(mediaState.positionMs),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDuration(mediaState.durationMs),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Bottom Row: Frosted Circular Transport Controls (matching uploaded widget)
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
                        .size(26.dp)
                        .clickable { onOpenMusicApp() }
                )

                OneUiCircleButton(
                    icon = Icons.Rounded.SkipPrevious,
                    size = 52.dp,
                    iconSize = 28.dp,
                    onClick = onPrev
                )

                OneUiCircleButton(
                    icon = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    size = 58.dp,
                    iconSize = 32.dp,
                    onClick = onPlayPause
                )

                OneUiCircleButton(
                    icon = Icons.Rounded.SkipNext,
                    size = 52.dp,
                    iconSize = 28.dp,
                    onClick = onNext
                )

                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .size(26.dp)
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
fun CarPlayAppTile(
    title: String,
    icon: ImageVector,
    badgeGradient: List<Color>,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .luxuryButtonSurface(theme, finish, cornerRadius = 24.dp)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Glossy iOS/CarPlay Rounded-Square Icon Badge
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(badgeGradient))
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AllAppsLuxuryOverlay(
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
                    text = "Applications",
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB07080B))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .luxuryCardSurface(currentTheme, currentFinish, cornerRadius = 28.dp)
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                            .padding(vertical = 16.dp),
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

private const val DARK_COCKPIT_MAP_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0f141c; }
  .leaflet-control-attribution { display: none !important; }
  .leaflet-control-zoom { margin-top: 74px !important; margin-left: 16px !important; border: none !important; }
  .leaflet-control-zoom a {
    background: rgba(17, 20, 27, 0.88) !important;
    color: #fff !important;
    border: 1px solid rgba(255,255,255,0.18) !important;
    width: 38px !important; height: 38px !important; line-height: 38px !important;
    font-size: 20px !important; border-radius: 12px !important; margin-bottom: 6px !important;
  }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var map = L.map('map', { zoomControl: true }).setView([55.7093, 9.5357], 14);
  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
    maxZoom: 19,
    subdomains: 'abcd'
  }).addTo(map);

  var carMarker = L.circleMarker([55.7093, 9.5357], {
    radius: 9,
    fillColor: '#38BDF8',
    color: '#ffffff',
    weight: 3,
    opacity: 1,
    fillOpacity: 1
  }).addTo(map);

  if (navigator.geolocation) {
    navigator.geolocation.watchPosition(function(pos) {
      var latlng = [pos.coords.latitude, pos.coords.longitude];
      carMarker.setLatLng(latlng);
      map.panTo(latlng);
    }, function() {}, { enableHighAccuracy: true });
  }
</script>
</body>
</html>
"""
