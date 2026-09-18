package com.perappvolume.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppVolumeEntity::class], version = 1, exportSchema = false)
abstract class AppVolumeDatabase : RoomDatabase() {

    abstract fun appVolumeDao(): AppVolumeDao

    companion object {
        @Volatile private var instance: AppVolumeDatabase? = null

        fun getInstance(context: Context): AppVolumeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppVolumeDatabase::class.java,
                    "app_volume.db"
                ).build().also { instance = it }
            }
    }
}
