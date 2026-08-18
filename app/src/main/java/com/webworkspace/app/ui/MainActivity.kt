package com.webworkspace.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.webworkspace.app.R
import com.webworkspace.app.data.AppDatabase
import com.webworkspace.app.data.Profile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ProfileAdapter
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    showSettingsDialog()
                    true
                }
                else -> false
            }
        }

        val recyclerView = findViewById<RecyclerView>(R.id.profilesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = ProfileAdapter(
            onPlayClick = { profile ->
                // Play -> Resume last visited URL or open Flow default
                val url = profile.lastVisitedUrl ?: "https://labs.google/fx/tools/flow"
                openWorkspace(profile, url, true)
            },
            onNewSessionClick = { profile ->
                // New Session -> Force open a new Flow project dashboard
                openWorkspace(profile, "https://labs.google/fx/tools/flow", true)
            },
            onCustomizeClick = { profile ->
                // Customization -> Opens Google accounts login/profile
                openWorkspace(profile, "https://myaccount.google.com/", false)
            },
            onDeleteClick = { profile ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.action_delete)
                    .setMessage("Are you sure you want to delete profile: ${profile.name}?")
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        lifecycleScope.launch {
                            database.profileDao().deleteProfile(profile)
                        }
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        )
        recyclerView.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabGallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddProfile).setOnClickListener {
            checkAndFetchAccounts()
        }

        lifecycleScope.launch {
            database.profileDao().getAllProfiles().collectLatest { profiles ->
                adapter.submitList(profiles)
            }
        }

        // Check for updates automatically in the background
        lifecycleScope.launch {
            try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                val versionName = pInfo.versionName ?: "1.0"
                com.webworkspace.app.updater.UpdateChecker.checkForUpdates(this@MainActivity, versionName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkAndFetchAccounts() {
        if (checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showAccountSelectionDialog()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.GET_ACCOUNTS), 1001)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showAccountSelectionDialog()
        } else {
            // Fallback to manual entry
            showAddProfileDialog()
        }
    }

    private fun showAccountSelectionDialog() {
        val am = android.accounts.AccountManager.get(this)
        val accounts = am.accounts.filter { android.util.Patterns.EMAIL_ADDRESS.matcher(it.name).matches() }
            .map { it.name }.distinct().toTypedArray()

        if (accounts.isEmpty()) {
            showAddProfileDialog() // Fallback if no accounts found
            return
        }

        val options = arrayOf("Enter manually...") + accounts

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Google Account for Profile")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showAddProfileDialog()
                } else {
                    val email = options[which]
                    val profileName = email.substringBefore("@")
                    lifecycleScope.launch {
                        // Check if exists
                        val existing = database.profileDao().getAllProfiles().first().find { it.name == profileName }
                        if (existing == null) {
                            database.profileDao().insertProfile(Profile(name = profileName))
                            android.widget.Toast.makeText(this@MainActivity, "Profile created for $email!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(this@MainActivity, "Profile already exists.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Clear Global Web Cache & Cookies", "Delete All Profiles")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        android.widget.Toast.makeText(this, "WebView Cache Cleared. GeckoView data is managed per-profile.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        lifecycleScope.launch {
                            database.profileDao().getAllProfiles().first().forEach { profile ->
                                database.profileDao().deleteProfile(profile)
                            }
                            android.widget.Toast.makeText(this@MainActivity, "All profiles deleted.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAddProfileDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_profile, null)
        val etProfileName = view.findViewById<TextInputEditText>(R.id.etProfileName)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_add_profile)
            .setView(view)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = etProfileName.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        val newProfile = Profile(name = name)
                        database.profileDao().insertProfile(newProfile)
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun openWorkspace(profile: Profile, targetUrl: String, forceDesktop: Boolean) {
        lifecycleScope.launch {
            val updated = profile.copy(lastUsedTimestamp = System.currentTimeMillis())
            database.profileDao().updateProfile(updated)
        }
        
        val intent = Intent(this, WorkspaceActivity::class.java).apply {
            putExtra(WorkspaceActivity.EXTRA_PROFILE_ID, profile.id)
            putExtra(WorkspaceActivity.EXTRA_PROFILE_NAME, profile.name)
            putExtra(WorkspaceActivity.EXTRA_TARGET_URL, targetUrl)
            putExtra(WorkspaceActivity.EXTRA_DESKTOP_MODE, forceDesktop)
        }
        startActivity(intent)
    }
}
