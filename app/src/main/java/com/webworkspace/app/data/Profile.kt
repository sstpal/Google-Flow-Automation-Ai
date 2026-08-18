package com.webworkspace.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isDesktopMode: Boolean = true,
    val lastUsedTimestamp: Long = System.currentTimeMillis(),
    val hasSession: Boolean = false,
    val lastVisitedUrl: String? = null // We can update this based on cookie presence if needed
)
