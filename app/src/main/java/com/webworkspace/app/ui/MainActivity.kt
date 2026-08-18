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

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddProfile).setOnClickListener {
            showAddProfileDialog()
        }

        lifecycleScope.launch {
            database.profileDao().getAllProfiles().collectLatest { profiles ->
                adapter.submitList(profiles)
            }
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf("Clear Global Web Cache & Cookies", "Delete All Profiles")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        android.webkit.WebStorage.getInstance().deleteAllData()
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        android.webkit.CookieManager.getInstance().flush()
                        android.widget.Toast.makeText(this, "Cache and Global Cookies Cleared.", android.widget.Toast.LENGTH_SHORT).show()
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
