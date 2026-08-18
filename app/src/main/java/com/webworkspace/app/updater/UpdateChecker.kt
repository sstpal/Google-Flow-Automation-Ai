package com.webworkspace.app.updater

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/sstpal/Google-Flow-Automation-Ai/releases/latest"
    private var downloadId: Long = -1L
    private var currentContext: Context? = null

    // Broadcast receiver to automatically trigger install when download completes
    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                Toast.makeText(context, "Update Downloaded. Starting Install...", Toast.LENGTH_SHORT).show()
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val uri = dm.getUriForDownloadedFile(downloadId)
                if (uri != null) {
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(installIntent)
                } else {
                    Toast.makeText(context, "Install failed: Could not parse downloaded file.", Toast.LENGTH_SHORT).show()
                }
                
                // Try to unregister to avoid memory leaks
                try {
                    currentContext?.unregisterReceiver(this)
                } catch (e: Exception) {}
            }
        }
    }

    suspend fun checkForUpdates(activity: Activity, currentVersionName: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    
                    val tagName = json.optString("tag_name", "").replace("v", "")
                    val releaseNotes = json.optString("body", "No release notes provided.")
                    
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    if (assets != null && assets.length() > 0) {
                        downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                    }
                    
                    if (tagName.isNotEmpty() && isNewerVersion(currentVersionName, tagName) && downloadUrl.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            showUpdateDialog(activity, tagName, releaseNotes, downloadUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            val currParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLen = maxOf(currParts.size, latestParts.size)
            for (i in 0 until maxLen) {
                val c = currParts.getOrElse(i) { 0 }
                val l = latestParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (c > l) return false
            }
        } catch (e: Exception) {
            return false
        }
        return false
    }

    private fun showUpdateDialog(activity: Activity, newVersion: String, releaseNotes: String, downloadUrl: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("New Update Available! (v$newVersion)")
            .setMessage("Do you want to install the latest version?\n\nRelease Notes:\n$releaseNotes")
            .setPositiveButton("Update Now") { _, _ ->
                startDownloadAndInstall(activity, downloadUrl, newVersion)
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun startDownloadAndInstall(context: Context, downloadUrl: String, version: String) {
        currentContext = context.applicationContext
        
        // Register receiver for when download is complete
        try {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            // For Android 13+ (API 33), specify RECEIVER_EXPORTED
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                currentContext?.registerReceiver(onDownloadComplete, filter, Context.RECEIVER_EXPORTED)
            } else {
                currentContext?.registerReceiver(onDownloadComplete, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("NexaFlow Update v$version")
                setDescription("Downloading the latest update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NexaFlow_Update_v$version.apk")
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)
            Toast.makeText(context, "Downloading update in background...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to start download.", Toast.LENGTH_SHORT).show()
        }
    }
}
