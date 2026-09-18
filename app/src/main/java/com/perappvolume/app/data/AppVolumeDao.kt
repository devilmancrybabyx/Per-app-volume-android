package com.perappvolume.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppVolumeDao {

    @Query("SELECT * FROM app_volume ORDER BY lastUpdatedEpochMs DESC")
    fun observeAll(): Flow<List<AppVolumeEntity>>

    @Query("SELECT * FROM app_volume WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): AppVolumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppVolumeEntity)

    @Update
    suspend fun update(entity: AppVolumeEntity)

    @Delete
    suspend fun delete(entity: AppVolumeEntity)

    @Query("DELETE FROM app_volume")
    suspend fun clearAll()
}
