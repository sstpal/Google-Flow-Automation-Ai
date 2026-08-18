package com.webworkspace.app.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.webworkspace.app.R
import com.webworkspace.app.data.AppDatabase
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

class WorkspaceActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDesktopToggle: ImageButton
    private lateinit var tvWorkspaceProfileName: TextView

    private val database by lazy { AppDatabase.getDatabase(this) }

    private var currentProfileId: Long = -1
    private var currentProfileName: String = ""
    private var targetUrl: String = ""

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_TARGET_URL = "target_url"
        const val EXTRA_DESKTOP_MODE = "desktop_mode"

        private var sRuntime: GeckoRuntime? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace)

        currentProfileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1)
        currentProfileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Default"
        targetUrl = intent.getStringExtra(EXTRA_TARGET_URL) ?: "https://labs.google/fx/tools/flow"

        if (currentProfileId == -1L) {
            finish()
            return
        }

        progressBar = findViewById(R.id.progressBar)
        btnDesktopToggle = findViewById(R.id.btnDesktopToggle)
        tvWorkspaceProfileName = findViewById(R.id.tvWorkspaceProfileName)

        tvWorkspaceProfileName.text = currentProfileName

        setupToolbar()
        setupGeckoView()
    }

    private fun setupGeckoView() {
        val container = findViewById<android.widget.FrameLayout>(R.id.webViewContainer)
        container.removeAllViews()

        geckoView = GeckoView(this)
        container.addView(geckoView)

        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this)
            setupWebExtension()
        }

        // Use Builder to isolate cookies, cache, and logins using contextId!
        // A unique contextId means this session gets its own completely isolated storage.
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .contextId("profile_$currentProfileId") // UNIQUE CONTEXT ID
            .build()

        geckoSession = GeckoSession(settings)

        // Track URL changes to save the last visited project for the "Play" button
        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                saveLastVisitedUrl(url)
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                // Done loading
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                // Update progress bar
            }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
            }
        }

        geckoSession.open(sRuntime!!)
        geckoView.setSession(geckoSession)

        geckoSession.loadUri(targetUrl)
    }

    private fun saveLastVisitedUrl(url: String) {
        // Only save valid Flow AI project URLs that the user is visiting
        if (url.startsWith("http") && url.contains("labs.google/fx/tools/flow")) {
            lifecycleScope.launch {
                val profile = database.profileDao().getProfileById(currentProfileId)
                if (profile != null && profile.lastVisitedUrl != url) {
                    database.profileDao().updateProfile(profile.copy(lastVisitedUrl = url))
                }
            }
        }
    }

    private fun setupToolbar() {
        btnDesktopToggle.setImageResource(R.drawable.ic_desktop)
        btnDesktopToggle.setOnClickListener {
            Toast.makeText(this, "Desktop mode is always on for full features.", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            // Returns the user to the main app page (Dashboard with profiles)
            finish()
        }

        findViewById<ImageButton>(R.id.btnReload).setOnClickListener {
            geckoSession.reload()
        }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            geckoSession.goBack()
        }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            geckoSession.goForward()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Instead of getting trapped, hardware back button returns to Dashboard
        finish()
    }

    private fun setupWebExtension() {
        // Register the built-in extension for the media downloader
        val extensionResult = sRuntime!!.webExtensionController.ensureBuiltIn(
            "resource://android/assets/downloader",
            "downloader@webworkspace.com"
        )
        extensionResult.accept({ extension ->
            extension?.setMessageDelegate(object : org.mozilla.geckoview.WebExtension.MessageDelegate {
                override fun onConnect(port: org.mozilla.geckoview.WebExtension.Port) {
                    port.setDelegate(object : org.mozilla.geckoview.WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: org.mozilla.geckoview.WebExtension.Port) {
                            if (message is org.json.JSONObject) {
                                val action = message.optString("action")
                                if (action == "long_press_media") {
                                    val url = message.optString("url")
                                    val type = message.optString("type")
                                    val isBlob = message.optBoolean("isBlob", false)
                                    runOnUiThread { showDownloadDialog(url, type, isBlob) }
                                }
                            }
                        }
                        override fun onDisconnect(port: org.mozilla.geckoview.WebExtension.Port) {}
                    })
                }
            }, "browser")
        }, { e -> e?.printStackTrace() })
    }

    private fun showDownloadDialog(url: String, type: String, isBlob: Boolean) {
        val title = if (type == "video") "Download Video" else "Download Image"
        val options = arrayOf("High Quality (Original)")
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(options) { _, _ ->
                downloadMedia(url, type, isBlob)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun downloadMedia(url: String, type: String, isBlob: Boolean) {
        try {
            val ext = if (type == "video") "mp4" else "jpg"
            val filename = "FlowMedia_${System.currentTimeMillis()}.$ext"

            if (isBlob) {
                // Handle Base64 Data URI
                val base64String = url.substringAfter(",")
                val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                saveToMediaStore(decodedBytes, filename, type)
            } else {
                // Standard HTTP Download
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                request.setTitle("Flow AI Media")
                request.setDescription("Downloading $type...")
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                
                val dm = getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Download started!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToMediaStore(data: ByteArray, filename: String, type: String) {
        val resolver = contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (type == "video") "video/mp4" else "image/jpeg")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = resolver.insert(
            if (type == "video") android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI 
            else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
            contentValues
        )

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(data)
                Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Failed to save media.", Toast.LENGTH_SHORT).show()
        }
    }
}
