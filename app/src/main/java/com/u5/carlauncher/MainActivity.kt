package com.u5.carlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
    var currentTime by remember { mutableStateOf("12:00") }
    var currentDate by remember { mutableStateOf("THU • NOV 19") }

    // Live clock + periodic media session poll
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE • MMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTime = timeFmt.format(now)
            currentDate = dateFmt.format(now).uppercase(Locale.getDefault())
            MediaRepository.refreshSessions(context)
            delay(1500)
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
        // Main 1760x720 Ultrawide Cockpit Row
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. LEFT SCULPTED DOCK
            LuxuryDock(
                time = currentTime,
                theme = theme,
                finish = finish,
                onMapsClick = { launchPackage(context, "com.google.android.apps.maps") },
                onSpotifyClick = { launchPackage(context, "com.spotify.music") },
                onYtMusicClick = { launchPackage(context, "com.google.android.apps.youtube.music") },
                onAppsClick = { showAppDrawer = true },
                onThemeClick = { showThemeModal = true }
            )

            // 2. CENTER-LEFT SAMSUNG MUSIC WAVE PLAYER
            SamsungMusicLuxuryCard(
                mediaState = mediaState,
                dateLabel = currentDate,
                theme = theme,
                finish = finish,
                modifier = Modifier
                    .weight(1.22f)
                    .fillMaxHeight(),
                onPlayPause = { MediaRepository.togglePlayPause(context) },
                onPrev = { MediaRepository.skipPrevious(context) },
                onNext = { MediaRepository.skipNext(context) },
                onOpenAccess = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                },
                onOpenMusicApp = {
                    if (mediaState.sourceApp == "YT MUSIC") {
                        launchPackage(context, "com.google.android.apps.youtube.music")
                    } else {
                        launchPackage(context, "com.spotify.music")
                    }
                }
            )

            // 3. RIGHT LUXURY QUICK-LAUNCH COCKPIT (Maps, Spotify, YT Music, SMS, Settings)
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero Navigation Tile — Google Maps
                LuxuryHeroNavTile(
                    theme = theme,
                    finish = finish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.05f),
                    onClick = { launchPackage(context, "com.google.android.apps.maps") }
                )

                // Row 1: Spotify & YouTube Music
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LuxuryAppTile(
                        title = "Spotify",
                        subtitle = "STREAMING",
                        icon = Icons.Rounded.GraphicEq,
                        ledColor = Color(0xFF1DB954),
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = { launchPackage(context, "com.spotify.music") }
                    )
                    LuxuryAppTile(
                        title = "YT Music",
                        subtitle = "AUDIO",
                        icon = Icons.Rounded.PlayCircle,
                        ledColor = Color(0xFFFF3B30),
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = { launchPackage(context, "com.google.android.apps.youtube.music") }
                    )
                }

                // Row 2: SMS & Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LuxuryAppTile(
                        title = "SMS",
                        subtitle = "MESSAGES",
                        icon = Icons.Rounded.Sms,
                        ledColor = Color(0xFF38BDF8),
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = { launchSmsApp(context) }
                    )
                    LuxuryAppTile(
                        title = "Settings",
                        subtitle = "SYSTEM",
                        icon = Icons.Rounded.Settings,
                        ledColor = theme.accent,
                        theme = theme,
                        finish = finish,
                        modifier = Modifier.weight(1f),
                        onClick = { launchPackage(context, "com.android.settings") }
                    )
                }
            }
        }

        // OVERLAY: ALL APPS DRAWER (Auto-themed luxury bezels)
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

        // OVERLAY: THEME & BUTTON FINISH STUDIO
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
fun LuxuryDock(
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
            .luxuryCardSurface(theme, finish, cornerRadius = 26.dp)
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Bold Clock
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = theme.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .width(28.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(theme.accent)
            )
        }

        // Quick Dock Buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DockIconButton(Icons.Rounded.Navigation, theme, finish, onMapsClick)
            DockIconButton(Icons.Rounded.GraphicEq, theme, finish, onSpotifyClick)
            DockIconButton(Icons.Rounded.PlayCircle, theme, finish, onYtMusicClick)
            DockIconButton(Icons.Rounded.Apps, theme, finish, onAppsClick)
        }

        // Theme Switcher Button
        DockIconButton(
            icon = Icons.Rounded.Palette,
            theme = theme,
            finish = finish,
            onClick = onThemeClick,
            highlighted = true
        )
    }
}

@Composable
fun DockIconButton(
    icon: ImageVector,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .luxuryButtonSurface(theme, finish, cornerRadius = 18.dp, highlighted = highlighted)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) theme.accentGlow else theme.textPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun SamsungMusicLuxuryCard(
    mediaState: LiveMediaState,
    dateLabel: String,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenAccess: () -> Unit,
    onOpenMusicApp: () -> Unit
) {
    Box(
        modifier = modifier
            .luxuryCardSurface(theme, finish, cornerRadius = 28.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        // Samsung Music Flowing Audio Waves Background
        SamsungMusicWaveVisualizer(
            isPlaying = mediaState.isPlaying,
            theme = theme,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (mediaState.isPlaying) theme.accentGlow else theme.textSecondary)
                    )
                    Text(
                        text = "${mediaState.sourceApp}  •  $dateLabel",
                        color = theme.accentGlow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                if (!mediaState.hasListenerAccess) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.accent.copy(alpha = 0.22f))
                            .border(1.dp, theme.accent, RoundedCornerShape(12.dp))
                            .clickable { onOpenAccess() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "ENABLE LIVE SYNC",
                            color = theme.accentGlow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Middle: Album Art + Track Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMusicApp() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(theme.surfaceElevated)
                        .border(1.5.dp, theme.border, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mediaState.albumArt != null) {
                        Image(
                            bitmap = mediaState.albumArt.asImageBitmap(),
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaState.title,
                        color = theme.textPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 34.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = mediaState.artist,
                        color = theme.textSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom: Sculpted Luxury Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportButton(
                    icon = Icons.Rounded.SkipPrevious,
                    size = 64.dp,
                    iconSize = 32.dp,
                    theme = theme,
                    finish = finish,
                    highlighted = false,
                    onClick = onPrev
                )
                TransportButton(
                    icon = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    size = 78.dp,
                    iconSize = 40.dp,
                    theme = theme,
                    finish = finish,
                    highlighted = true,
                    onClick = onPlayPause
                )
                TransportButton(
                    icon = Icons.Rounded.SkipNext,
                    size = 64.dp,
                    iconSize = 32.dp,
                    theme = theme,
                    finish = finish,
                    highlighted = false,
                    onClick = onNext
                )
            }
        }
    }
}

@Composable
fun TransportButton(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .luxuryButtonSurface(theme, finish, cornerRadius = size / 2, highlighted = highlighted)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) theme.accentGlow else theme.textPrimary,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun LuxuryHeroNavTile(
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .luxuryButtonSurface(theme, finish, cornerRadius = 24.dp, highlighted = true)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "NAVIGATION",
                    color = theme.accentGlow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Google Maps",
                    color = theme.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.accent.copy(alpha = 0.22f))
                    .border(1.2.dp, theme.accent, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = "Google Maps",
                    tint = theme.accentGlow,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
fun LuxuryAppTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    ledColor: Color,
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .luxuryButtonSurface(theme, finish, cornerRadius = 22.dp)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = theme.textPrimary,
                    modifier = Modifier.size(32.dp)
                )
                // Micro-LED Accent Bar
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(ledColor)
                )
            }

            Column {
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
            .background(Color(0xE806070A))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALL APPLICATIONS",
                    color = theme.accentGlow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.6.sp
                )
                DockIconButton(Icons.Rounded.Close, theme, finish, onClose, highlighted = true)
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
                            .luxuryButtonSurface(theme, finish, cornerRadius = 20.dp)
                            .clickable { onAppClick(app.packageName) }
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = app.label,
                            color = theme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
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
            .background(Color(0xE806070A))
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
                    text = "COCKPIT THEME STUDIO",
                    color = currentTheme.accentGlow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )
                DockIconButton(Icons.Rounded.Close, currentTheme, currentFinish, onClose, highlighted = true)
            }

            Text(
                text = "LUXURY CABIN THEME",
                color = currentTheme.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

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
                            color = t.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                    }
                }
            }

            Text(
                text = "BUTTON FINISH",
                color = currentTheme.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

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
                            color = if (selected) currentTheme.accentGlow else currentTheme.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
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
        ButtonFinish.SCULPTED_METAL -> Brush.linearGradient(
            listOf(theme.surfaceElevated, theme.surface)
        )
        ButtonFinish.SMOKED_GLASS -> Brush.linearGradient(
            listOf(theme.surface.copy(alpha = 0.78f), theme.bgTop.copy(alpha = 0.88f))
        )
        ButtonFinish.AMBIENT_EDGE -> Brush.linearGradient(
            listOf(theme.bgTop, theme.surface)
        )
    }
    val borderWidth = if (finish == ButtonFinish.AMBIENT_EDGE) 1.5.dp else 1.dp
    return this
        .clip(shape)
        .background(bgBrush)
        .border(borderWidth, theme.border, shape)
}

fun Modifier.luxuryButtonSurface(
    theme: LuxuryThemeType,
    finish: ButtonFinish,
    cornerRadius: Dp,
    highlighted: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = when (finish) {
        ButtonFinish.SCULPTED_METAL -> Brush.verticalGradient(
            listOf(
                if (highlighted) theme.surfaceElevated else theme.surfaceElevated.copy(alpha = 0.92f),
                theme.surface
            )
        )
        ButtonFinish.SMOKED_GLASS -> Brush.verticalGradient(
            listOf(
                theme.surfaceElevated.copy(alpha = 0.58f),
                theme.bgTop.copy(alpha = 0.78f)
            )
        )
        ButtonFinish.AMBIENT_EDGE -> Brush.verticalGradient(
            listOf(theme.surface, theme.bgTop)
        )
    }
    val borderColor = when {
        highlighted -> theme.accentGlow.copy(alpha = 0.85f)
        finish == ButtonFinish.AMBIENT_EDGE -> theme.accent.copy(alpha = 0.65f)
        else -> theme.border
    }
    val borderWidth = if (highlighted || finish == ButtonFinish.AMBIENT_EDGE) 1.6.dp else 1.dp
    return this
        .clip(shape)
        .background(bgBrush)
        .border(borderWidth, borderColor, shape)
}

fun launchPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

fun launchSmsApp(context: Context) {
    // 1. Try system default Messaging / SMS app
    val defaultSms = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MESSAGING)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (defaultSms.resolveActivity(context.packageManager) != null) {
        context.startActivity(defaultSms)
        return
    }
    // 2. Fallback to common SMS packages
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
