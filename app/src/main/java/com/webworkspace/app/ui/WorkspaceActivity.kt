package com.webworkspace.app.ui

import android.os.Bundle
import android.view.View
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
import org.mozilla.geckoview.GeckoView

class WorkspaceActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private val geckoSession = GeckoSession()
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
        }

        // Force desktop user agent
        geckoSession.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        geckoSession.settings.viewportMode = org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP

        geckoSession.open(sRuntime!!)
        geckoView.setSession(geckoSession)

        geckoSession.loadUri(targetUrl)
    }

    private fun updateProfileSessionStatus(hasSession: Boolean) {
        lifecycleScope.launch {
            val profile = database.profileDao().getProfileById(currentProfileId)
            if (profile != null && profile.hasSession != hasSession) {
                database.profileDao().updateProfile(profile.copy(hasSession = hasSession))
            }
        }
    }

    private fun saveLastVisitedUrl(url: String) {
        lifecycleScope.launch {
            val profile = database.profileDao().getProfileById(currentProfileId)
            if (profile != null && profile.lastVisitedUrl != url) {
                database.profileDao().updateProfile(profile.copy(lastVisitedUrl = url))
            }
        }
    }

    private fun setupToolbar() {
        btnDesktopToggle.setImageResource(R.drawable.ic_desktop)
        btnDesktopToggle.setOnClickListener {
            Toast.makeText(this, "Desktop mode is always on for full features.", Toast.LENGTH_SHORT).show()
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
        geckoSession.goBack()
    }
}
