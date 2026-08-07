package com.artesanias.tv

import android.app.Application
import com.artesanias.tv.net.TvServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class TvApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        TvServer.iniciar(appScope)
    }
}
