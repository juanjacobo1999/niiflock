package com.asistente.movil

import android.app.Application
import android.content.Context

class AsistenteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        Session.start()
    }

    companion object {
        lateinit var ctx: Context
            private set
    }
}
