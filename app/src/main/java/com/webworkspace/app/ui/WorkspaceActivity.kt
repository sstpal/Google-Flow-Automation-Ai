package com.webworkspace.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.webworkspace.app.R
import com.webworkspace.app.data.AppDatabase
import com.webworkspace.app.data.Profile
import com.webworkspace.app.data.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WorkspaceActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var profileSpinner: Spinner
    private lateinit var btnDesktopToggle: ImageButton
    
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val settings by lazy { SettingsManager(this) }
    
    private var currentProfileId: Long = -1
    private var currentProfileName: String = ""
    private var isDesktopMode: Boolean = true
    private var profilesList: List<Profile> = emptyList()
    
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_DESKTOP_MODE = "desktop_mode"
        private const val FILE_CHOOSER_REQUEST_CODE = 1001
        
        // Use a generic desktop user agent
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace)

        currentProfileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1)
        currentProfileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Default"
        isDesktopMode = intent.getBooleanExtra(EXTRA_DESKTOP_MODE, true)

        if (currentProfileId == -1L) {
            finish()
            return
        }

        webView = findViewById(R.id.webViewContainer) // wait, FrameLayout in layout, need to add WebView programmatically or change layout to WebView. Let's create it programmatically to set profile before creation
        
        progressBar = findViewById(R.id.progressBar)
        profileSpinner = findViewById(R.id.profileSpinner)
        btnDesktopToggle = findViewById(R.id.btnDesktopToggle)
        
        setupToolbar()
        loadProfilesForSpinner()
        
        // Initialize WebView
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val container = findViewById<android.widget.FrameLayout>(R.id.webViewContainer)
        container.removeAllViews()
        
        // Ensure MULTI_PROFILE is supported
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profileStore = ProfileStore.getInstance()
            val profileName = "Profile_$currentProfileId"
            val webkitProfile = profileStore.getOrCreateProfile(profileName)
            
            // Note: According to AndroidX Webkit documentation, you assign a profile to a WebView
            // Unfortunately, the API to assign profile is actually setProfile(profileName) which is available on WebView instance
            // Wait, WebViewCompat.setProfile(webView, webkitProfile.name)
            
            webView = WebView(this)
            WebViewCompat.setProfile(webView, webkitProfile.name)
            
        } else {
            // Fallback for older devices: just use default profile, but warn user
            Toast.makeText(this, "Multi-Profile not supported on this device. Data may be shared.", Toast.LENGTH_LONG).show()
            webView = WebView(this)
        }
        
        container.addView(webView)
        
        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        updateDesktopMode()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Keep everything in app
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                // Check if session exists (e.g., cookies exist)
                CookieManager.getInstance().getCookie(url)?.let {
                    if (it.isNotEmpty()) {
                        updateProfileSessionStatus(true)
                    }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    Toast.makeText(this@WorkspaceActivity, getString(R.string.error_loading), Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                } catch (e: Exception) {
                    fileChooserCallback = null
                    return false
                }
                return true
            }
        }

        val url = settings.defaultWorkspaceUrl
        webView.loadUrl(url)
    }
    
    private fun updateProfileSessionStatus(hasSession: Boolean) {
        lifecycleScope.launch {
            val profile = database.profileDao().getProfileById(currentProfileId)
            if (profile != null && profile.hasSession != hasSession) {
                database.profileDao().updateProfile(profile.copy(hasSession = hasSession))
            }
        }
    }

    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener {
            webView.reload()
        }
        btnDesktopToggle.setOnClickListener {
            isDesktopMode = !isDesktopMode
            updateDesktopMode()
            webView.reload()
            
            // Save preference
            lifecycleScope.launch {
                val profile = database.profileDao().getProfileById(currentProfileId)
                if (profile != null) {
                    database.profileDao().updateProfile(profile.copy(isDesktopMode = isDesktopMode))
                }
            }
        }
    }

    private fun updateDesktopMode() {
        if (isDesktopMode) {
            webView.settings.userAgentString = DESKTOP_USER_AGENT
            btnDesktopToggle.setImageResource(R.drawable.ic_desktop)
        } else {
            // Revert to default mobile user agent
            webView.settings.userAgentString = WebSettings.getDefaultUserAgent(this)
            btnDesktopToggle.setImageResource(R.drawable.ic_mobile)
        }
    }
    
    // We need to import WebSettings
    private fun getDefaultUserAgent(): String {
        return android.webkit.WebSettings.getDefaultUserAgent(this)
    }

    private fun loadProfilesForSpinner() {
        lifecycleScope.launch {
            profilesList = database.profileDao().getAllProfiles().first()
            val names = profilesList.map { it.name }
            val adapter = ArrayAdapter(this@WorkspaceActivity, android.R.layout.simple_spinner_dropdown_item, names)
            profileSpinner.adapter = adapter
            
            // Select current
            val currentIndex = profilesList.indexOfFirst { it.id == currentProfileId }
            if (currentIndex >= 0) {
                profileSpinner.setSelection(currentIndex)
            }
            
            profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedProfile = profilesList[position]
                    if (selectedProfile.id != currentProfileId) {
                        currentProfileId = selectedProfile.id
                        currentProfileName = selectedProfile.name
                        isDesktopMode = selectedProfile.isDesktopMode
                        setupWebView() // Re-initialize with new profile
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val result = if (data == null || data.data == null) {
                    // Check clipData if multiple files
                    data?.clipData?.let { clipData ->
                        val uris = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                        uris
                    } ?: emptyArray()
                } else {
                    arrayOf(data.data!!)
                }
                fileChooserCallback?.onReceiveValue(if (result.isEmpty()) null else result)
            } else {
                fileChooserCallback?.onReceiveValue(null)
            }
            fileChooserCallback = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
