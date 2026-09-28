package com.openreel.app

import android.app.Application
import com.openreel.app.data.Catalog
import com.openreel.app.data.Prefs

class OpenReelApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var catalog: Catalog
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        catalog = Catalog(this, prefs)
    }
}
