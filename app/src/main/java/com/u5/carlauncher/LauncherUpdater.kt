package com.u5.carlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object LauncherUpdater {
    private const val REPO_BASE = "https://github.com/pronaxbg-ctrl/U5LuxuryLauncher/releases/download/latest"
    private const val VERSION_URL = "$REPO_BASE/version.json"
    private const val APK_URL = "$REPO_BASE/U5LuxuryLauncher.apk"

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.0.0"
        } catch (_: Exception) {
            "3.0.0"
        }
    }

    suspend fun checkAndInstallUpdate(
        context: Context,
        onStatus: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            onStatus("Allow 'Install unknown apps' first, then tap Update again.")
            withContext(Dispatchers.Main) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            return
        }

        val currentCode = getCurrentVersionCode(context)
        val currentName = getCurrentVersionName(context)

        onStatus("Checking GitHub for updates (installed v$currentName)...")

        val remoteVersion = withContext(Dispatchers.IO) {
            try {
                val conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Cache-Control", "no-cache")
                }
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    Pair(json.optLong("versionCode", 0L), json.optString("versionName", "latest"))
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        if (remoteVersion == null) {
            onStatus("Could not reach GitHub Release. Make sure repo is Public & online.")
            return
        }

        val (remoteCode, remoteName) = remoteVersion
        if (remoteCode <= currentCode) {
            onStatus("Up to date! You are on the latest version (v$currentName).")
            return
        }

        onStatus("Downloading update v$remoteName...")

        val apkFile = withContext(Dispatchers.IO) {
            try {
                val conn = (URL(APK_URL).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                if (conn.responseCode != 200) return@withContext null

                val totalBytes = conn.contentLengthLong
                val outFile = File(context.cacheDir, "U5LuxuryLauncher_update.apk")
                if (outFile.exists()) outFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100) / totalBytes).toInt()
                                if (pct != lastPct && pct % 5 == 0) {
                                    lastPct = pct
                                    withContext(Dispatchers.Main) {
                                        onStatus("Downloading v$remoteName... $pct%")
                                    }
                                }
                            }
                        }
                    }
                }
                outFile
            } catch (_: Exception) {
                null
            }
        }

        if (apkFile == null || !apkFile.exists()) {
            onStatus("Download failed. Check internet connection and try again.")
            return
        }

        onStatus("Launching installer for v$remoteName...")

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }
    }
}
