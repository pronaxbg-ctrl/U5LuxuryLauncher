package com.u5.carlauncher

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import kotlin.math.*

object NativeTileCache {
    private val cache = object : LruCache<String, ImageBitmap>(180) {}
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun isLoading(key: String): Boolean = inFlight.contains(key)

    suspend fun fetchTile(
        key: String,
        x: Int,
        y: Int,
        z: Int,
        satellite: Boolean
    ): ImageBitmap? {
        cache.get(key)?.let { return it }
        if (!inFlight.add(key)) return null

        return try {
            withContext(Dispatchers.IO) {
                val s = abs(x + y) % 4
                val googleUrl = if (satellite) {
                    "https://mt$s.google.com/vt/lyrs=y&x=$x&y=$y&z=$z"
                } else {
                    "https://mt$s.google.com/vt/lyrs=m&x=$x&y=$y&z=$z"
                }
                val bmp = downloadBitmap(googleUrl)
                    ?: downloadBitmap("https://tile.openstreetmap.org/$z/$x/$y.png")

                val img = bmp?.asImageBitmap()
                if (img != null) {
                    cache.put(key, img)
                }
                img
            }
        } finally {
            inFlight.remove(key)
        }
    }

    private fun downloadBitmap(urlStr: String): android.graphics.Bitmap? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

private const val NATIVE_TILE_PX = 384f // Crisp upscaled tile size for car/TV screens

private fun latLonToWorldPx(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
    val scale = NATIVE_TILE_PX * 2.0.pow(zoom)
    val x = (lon + 180.0) / 360.0 * scale
    val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val y = (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * scale
    return Pair(x, y)
}

private fun worldPxToLatLon(x: Double, y: Double, zoom: Int): Pair<Double, Double> {
    val scale = NATIVE_TILE_PX * 2.0.pow(zoom)
    val lon = x / scale * 360.0 - 180.0
    val n = PI - 2.0 * PI * y / scale
    val lat = Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    return Pair(lat.coerceIn(-85.0511, 85.0511), lon)
}

// Smooth dark cockpit color matrix (inverts bright map roads into sleek dark-navy theme)
private val DARK_MAP_COLOR_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.86f, 0f, 0f, 0f, 228f,
            0f, -0.84f, 0f, 0f, 232f,
            0f, 0f, -0.78f, 0f, 240f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

@Composable
fun NativeGoogleMapView(
    centerLat: Double,
    centerLon: Double,
    carLat: Double,
    carLon: Double,
    zoom: Int,
    isSatellite: Boolean,
    onPan: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var canvasSize by remember { mutableStateOf(IntSize(800, 600)) }
    var tileVersion by remember { mutableIntStateOf(0) }

    // Keep latest coordinates for drag gesture math
    val latestCenterLat by rememberUpdatedState(centerLat)
    val latestCenterLon by rememberUpdatedState(centerLon)
    val latestZoom by rememberUpdatedState(zoom)

    // Trigger missing tile fetches whenever viewport changes
    LaunchedEffect(centerLat, centerLon, zoom, isSatellite, canvasSize) {
        val w = canvasSize.width.coerceAtLeast(256)
        val h = canvasSize.height.coerceAtLeast(256)
        val (centerX, centerY) = latLonToWorldPx(centerLat, centerLon, zoom)
        val topLeftX = centerX - w / 2.0
        val topLeftY = centerY - h / 2.0

        val maxTile = 1 shl zoom
        val startCol = floor(topLeftX / NATIVE_TILE_PX).toInt() - 1
        val endCol = floor((topLeftX + w) / NATIVE_TILE_PX).toInt() + 1
        val startRow = floor(topLeftY / NATIVE_TILE_PX).toInt() - 1
        val endRow = floor((topLeftY + h) / NATIVE_TILE_PX).toInt() + 1

        for (col in startCol..endCol) {
            for (row in startRow..endRow) {
                if (row < 0 || row >= maxTile) continue
                val wrappedCol = ((col % maxTile) + maxTile) % maxTile
                val styleTag = if (isSatellite) "sat" else "dark"
                val key = "${styleTag}_${zoom}_${wrappedCol}_${row}"
                if (NativeTileCache.get(key) == null && !NativeTileCache.isLoading(key)) {
                    scope.launch {
                        val loaded = NativeTileCache.fetchTile(key, wrappedCol, row, zoom, isSatellite)
                        if (loaded != null) {
                            tileVersion++
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0F141C))
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val (cx, cy) = latLonToWorldPx(latestCenterLat, latestCenterLon, latestZoom)
                    val (newLat, newLon) = worldPxToLatLon(
                        cx - dragAmount.x.toDouble(),
                        cy - dragAmount.y.toDouble(),
                        latestZoom
                    )
                    onPan(newLat, newLon)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read tileVersion so Compose redraws as soon as each tile finishes downloading
            @Suppress("UNUSED_VARIABLE")
            val v = tileVersion

            val w = size.width
            val h = size.height
            val (centerX, centerY) = latLonToWorldPx(centerLat, centerLon, zoom)
            val topLeftX = centerX - w / 2.0
            val topLeftY = centerY - h / 2.0

            val maxTile = 1 shl zoom
            val startCol = floor(topLeftX / NATIVE_TILE_PX).toInt() - 1
            val endCol = floor((topLeftX + w) / NATIVE_TILE_PX).toInt() + 1
            val startRow = floor(topLeftY / NATIVE_TILE_PX).toInt() - 1
            val endRow = floor((topLeftY + h) / NATIVE_TILE_PX).toInt() + 1

            val tileSizeInt = IntSize(ceil(NATIVE_TILE_PX).toInt() + 1, ceil(NATIVE_TILE_PX).toInt() + 1)
            val colorFilter = if (isSatellite) null else DARK_MAP_COLOR_FILTER

            for (col in startCol..endCol) {
                for (row in startRow..endRow) {
                    if (row < 0 || row >= maxTile) continue
                    val wrappedCol = ((col % maxTile) + maxTile) % maxTile
                    val styleTag = if (isSatellite) "sat" else "dark"
                    val key = "${styleTag}_${zoom}_${wrappedCol}_${row}"
                    val bmp = NativeTileCache.get(key)
                    if (bmp != null) {
                        val px = (col * NATIVE_TILE_PX - topLeftX).roundToInt()
                        val py = (row * NATIVE_TILE_PX - topLeftY).roundToInt()
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(px, py),
                            dstSize = tileSizeInt,
                            colorFilter = colorFilter
                        )
                    }
                }
            }

            // Glowing Car GPS Marker
            val (carX, carY) = latLonToWorldPx(carLat, carLon, zoom)
            val markerPos = Offset((carX - topLeftX).toFloat(), (carY - topLeftY).toFloat())
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.28f),
                radius = 28f,
                center = markerPos
            )
            drawCircle(
                color = Color(0xFF38BDF8),
                radius = 12f,
                center = markerPos
            )
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = markerPos,
                style = Stroke(width = 3.5f)
            )
        }
    }
}
