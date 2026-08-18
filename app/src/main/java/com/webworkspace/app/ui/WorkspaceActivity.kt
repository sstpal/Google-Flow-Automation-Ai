package com.webworkspace.app.ui

import android.app.Activity
import android.content.Intent
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
import org.mozilla.geckoview.GeckoRuntimeSettings
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
    private var isDesktopMode: Boolean = true

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_TARGET_URL = "target_url"
        const val EXTRA_DESKTOP_MODE = "desktop_mode"
        
        private var geckoRuntime: GeckoRuntime? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace)

        currentProfileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1)
        currentProfileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Default"
        targetUrl = intent.getStringExtra(EXTRA_TARGET_URL) ?: "https://labs.google/fx/tools/flow"
        isDesktopMode = true // Force desktop mode as requested by user

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
        
        if (geckoRuntime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .build()
            geckoRuntime = GeckoRuntime.create(this, settings)
        }

        // Context ID creates completely isolated cookie jars per profile
        val sessionSettings = GeckoSessionSettings.Builder()
            .contextId(currentProfileName)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .build()
            
        geckoSession = GeckoSession(sessionSettings)
        
        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                progressBar.visibility = View.VISIBLE
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                progressBar.visibility = View.GONE
                updateProfileSessionStatus(true)
                saveLastVisitedUrl(targetUrl) // Approximation for GeckoView URL tracking
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                progressBar.progress = progress
            }
        }
        
        geckoSession.open(geckoRuntime!!)
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
            Toast.makeText(this, "Desktop mode is enforced globally for best features.", Toast.LENGTH_SHORT).show()
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

    override fun onBackPressed() {
        geckoSession.goBack()
    }
}
