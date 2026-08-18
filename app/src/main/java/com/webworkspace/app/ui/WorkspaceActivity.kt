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
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.webworkspace.app.R
import com.webworkspace.app.data.AppDatabase
import kotlinx.coroutines.launch

class WorkspaceActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDesktopToggle: ImageButton
    private lateinit var tvWorkspaceProfileName: TextView
    
    private val database by lazy { AppDatabase.getDatabase(this) }
    
    private var currentProfileId: Long = -1
    private var currentProfileName: String = ""
    private var targetUrl: String = ""
    private var isDesktopMode: Boolean = true
    
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_TARGET_URL = "target_url"
        const val EXTRA_DESKTOP_MODE = "desktop_mode"
        private const val FILE_CHOOSER_REQUEST_CODE = 1001
        
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace)

        currentProfileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1)
        currentProfileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Default"
        targetUrl = intent.getStringExtra(EXTRA_TARGET_URL) ?: "https://flow.google.com"
        isDesktopMode = intent.getBooleanExtra(EXTRA_DESKTOP_MODE, true)

        if (currentProfileId == -1L) {
            finish()
            return
        }

        progressBar = findViewById(R.id.progressBar)
        btnDesktopToggle = findViewById(R.id.btnDesktopToggle)
        tvWorkspaceProfileName = findViewById(R.id.tvWorkspaceProfileName)
        
        tvWorkspaceProfileName.text = currentProfileName
        
        setupToolbar()
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val container = findViewById<android.widget.FrameLayout>(R.id.webViewContainer)
        container.removeAllViews()
        
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profileStore = ProfileStore.getInstance()
            val profileName = "Profile_$currentProfileId"
            val webkitProfile = profileStore.getOrCreateProfile(profileName)
            
            webView = WebView(this)
            WebViewCompat.setProfile(webView, webkitProfile.name)
        } else {
            Toast.makeText(this, "Multi-Profile not supported. Data may be shared.", Toast.LENGTH_LONG).show()
            webView = WebView(this)
        }
        
        container.addView(webView)
        
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
                return false // Keep all links inside the app
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                CookieManager.getInstance().getCookie(url)?.let {
                    if (it.isNotEmpty()) updateProfileSessionStatus(true)
                }
                url?.let {
                    saveLastVisitedUrl(it)
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
                val fileIntent = fileChooserParams?.createIntent()
                if (fileIntent != null) {
                    try {
                        startActivityForResult(fileIntent, FILE_CHOOSER_REQUEST_CODE)
                    } catch (e: Exception) {
                        fileChooserCallback = null
                        return false
                    }
                } else {
                    fileChooserCallback = null
                    return false
                }
                return true
            }
        }

        webView.loadUrl(targetUrl)
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
        }
    }

    private fun updateDesktopMode() {
        if (isDesktopMode) {
            webView.settings.userAgentString = DESKTOP_USER_AGENT
            btnDesktopToggle.setImageResource(R.drawable.ic_desktop)
        } else {
            webView.settings.userAgentString = WebSettings.getDefaultUserAgent(this)
            btnDesktopToggle.setImageResource(R.drawable.ic_mobile)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val result = if (data == null || data.data == null) {
                    data?.clipData?.let { clipData ->
                        Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
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
