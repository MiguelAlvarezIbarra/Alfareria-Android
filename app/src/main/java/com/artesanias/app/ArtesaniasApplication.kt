package com.artesanias.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Clase Application de la app. `@HiltAndroidApp` es el punto de entrada
 * obligatorio de Hilt: genera el "contenedor" raíz de inyección de
 * dependencias del que cuelgan todos los demás (Activities, Fragments,
 * ViewModels marcados con @AndroidEntryPoint / @HiltViewModel), y arranca
 * en cuanto el proceso de la app inicia, antes que cualquier Activity.
 * No necesita lógica propia: basta con la anotación.
 */
@HiltAndroidApp
class ArtesaniasApplication : Application()
