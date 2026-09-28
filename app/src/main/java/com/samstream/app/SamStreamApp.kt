package com.samstream.app

import android.app.Application
import com.samstream.app.data.Catalog
import com.samstream.app.data.Prefs

class SamStreamApp : Application() {
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
