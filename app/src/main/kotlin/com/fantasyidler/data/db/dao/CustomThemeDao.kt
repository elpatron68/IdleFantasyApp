package com.fantasyidler.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fantasyidler.data.model.CustomTheme
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomThemeDao {
    @Query("SELECT * FROM themes WHERE name = :theme")
    suspend fun getTheme(theme: String): CustomTheme?

    @Query("SELECT * FROM themes")
    suspend fun getAllThemes(): List<CustomTheme>

    @Query("SELECT * FROM themes")
    fun observeAllThemes(): Flow<List<CustomTheme>>

    @Query("DELETE FROM themes WHERE name = :theme")
    suspend fun delete(theme: String)

    @Query("DELETE FROM themes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customTheme: CustomTheme)
}