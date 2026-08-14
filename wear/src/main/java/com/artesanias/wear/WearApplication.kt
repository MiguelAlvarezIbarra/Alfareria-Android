package com.artesanias.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Clase Application del módulo Wear OS. Igual que en el teléfono,
 * `@HiltAndroidApp` arranca el contenedor de inyección de dependencias de
 * Hilt (aunque este módulo lo usa poco, ver Activities.kt); no necesita
 * lógica propia.
 */
@HiltAndroidApp
class WearApplication : Application()
