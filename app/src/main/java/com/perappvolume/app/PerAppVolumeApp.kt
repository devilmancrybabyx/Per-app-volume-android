package com.perappvolume.app

import android.app.Application
import com.perappvolume.app.data.AppVolumeDatabase

class PerAppVolumeApp : Application() {

    val database: AppVolumeDatabase by lazy { AppVolumeDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
