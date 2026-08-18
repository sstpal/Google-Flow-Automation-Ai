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
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ProfileAdapter
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.profilesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = ProfileAdapter(
            onPlayClick = { profile ->
                // Play -> Opens Flow in Desktop mode
                openWorkspace(profile, "https://flow.google.com/", true)
            },
            onCustomizeClick = { profile ->
                // Customization -> Opens Google accounts login
                openWorkspace(profile, "https://accounts.google.com/", false)
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
