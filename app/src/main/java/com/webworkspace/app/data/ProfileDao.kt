package com.webworkspace.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY lastUsedTimestamp DESC")
    fun getAllProfiles(): Flow<List<Profile>>

    @Insert
    suspend fun insertProfile(profile: Profile): Long

    @Update
    suspend fun updateProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)
    
    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): Profile?
}
