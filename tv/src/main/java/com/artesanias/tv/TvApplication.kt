package com.artesanias.tv

import android.app.Application
import com.artesanias.tv.net.TvServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Clase Application de la app de TV. A diferencia del módulo de teléfono
 * (que usa Hilt), aquí no hay inyección de dependencias: el único trabajo
 * de arranque es levantar el servidor de sockets (`TvServer`) en cuanto
 * el proceso inicia, para que esté escuchando conexiones del teléfono
 * incluso antes de que se abra ninguna pantalla.
 */
class TvApplication : Application() {
    // Alcance de corrutinas de toda la app: vive mientras viva el proceso,
    // así que el servidor sigue corriendo aunque cambie de pantalla.
    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        TvServer.iniciar(appScope)
    }
}
