# 📺 Módulo TV (`tv/`)

App para Android TV que muestra, en una pantalla grande visible para todo el negocio, el catálogo de productos, un resumen de ventas y un mapa de los talleres artesanales — todo actualizado en vivo desde el teléfono del administrador.

⬅️ [Volver al README principal](../README.md)

---

## 🎯 Qué hace

No es un cliente independiente: no tiene su propia base de datos ni lógica de negocio. Es un **receptor pasivo** que dibuja lo que el teléfono le manda por socket TCP. Si el teléfono no está enviando nada (no se pareó o se desconectó el cable), la TV simplemente se queda con la última información que recibió.

### Pantallas (navegación por riel lateral, control remoto/D-pad)

| Pantalla | Fragment | Contenido |
|---|---|---|
| Catálogo | `ProductosFragment` | Cuadrícula de productos activos con precio y stock (⚠️ si es bajo) |
| Ventas | `VentasFragment` | Gráfica de barras (MPAndroidChart) de más vendidos + tabla de compras de los últimos 7 días |
| Talleres | `MapaFragment` | Mapa (Google Maps JS embebido en WebView) con los 7 talleres artesanales del catálogo |

Además, sin importar qué pantalla esté activa, si llega una alerta de **compra grande** aparece un diálogo overlay encima de todo (`MainActivity.mostrarNotificacionCompraGrande`) que se cierra solo a los 8 segundos.

Ver más detalle de la comunicación y el protocolo en el [README principal](../README.md#-comunicación-teléfono--tv).

---

## 🛠️ Stack y librerías

| Librería | Versión | Para qué |
|---|---|---|
| `androidx.leanback` | 1.0.0 | Estilos y componentes pensados para pantalla grande/TV |
| `androidx.appcompat` | 1.6.1 | Compatibilidad base de Activity/temas |
| `androidx.fragment-ktx` | 1.6.2 | Fragments (`ProductosFragment`, `VentasFragment`, `MapaFragment`) |
| `androidx.lifecycle` (viewmodel/runtime/livedata-ktx) | 2.7.0 | `lifecycleScope` para las corrutinas que leen `TvDataStore` |
| `com.google.android.material` | 1.11.0 | Componentes de Material Design |
| `com.github.PhilJay:MPAndroidChart` | v3.1.0 | Gráfica de barras de "más vendidos" |
| `kotlinx-coroutines-android` | 1.7.3 | El servidor de sockets (`TvServer`) corre en una corrutina sobre `Dispatchers.IO` |

No usa Hilt, Room ni Navigation Component — a propósito, ver [Notas de desarrollo](#-notas-de-desarrollo).

---

## 🏗️ Estructura

```
tv/src/main/java/com/artesanias/tv/
├── TvApplication.kt        # Application; arranca TvServer al inicio
├── MainActivity.kt         # Única Activity; cambia Fragments manualmente + overlay de compra grande
├── data/
│   ├── Modelos.kt          # Data classes locales (TvProducto, TvMasVendido, TvCompraSemana...)
│   └── TvDataStore.kt      # Estado en memoria (StateFlow) que alimenta las 3 pantallas
├── net/
│   └── TvServer.kt         # Servidor TCP: recibe JSON del teléfono y actualiza TvDataStore
└── ui/
    ├── ProductosFragment.kt
    ├── VentasFragment.kt
    └── MapaFragment.kt
```

---

## 🚀 Instalación

### 1. Requisito previo: `MAPS_API_KEY`

La pantalla de Talleres necesita una API key de Google Maps JavaScript API válida en `local.properties` (en la raíz del proyecto, mismo archivo que usa el módulo `app`):

```properties
MAPS_API_KEY=tu_api_key_aquí
```

### 2. Instalar en un dispositivo o emulador de Android TV

```
Run → Run 'tv'   # Selecciona un AVD de Android TV (API 26+) o un dispositivo TV real
```

> Si no tienes un AVD de TV creado: Android Studio → Device Manager → Create Device → categoría **TV** (por ejemplo "Television (1080p)").

### 3. Conectar la TV con el teléfono

La TV, por sí sola, no muestra nada útil — necesita que el teléfono le esté mandando datos. Ver la sección **"Cómo generar el puente de comunicación de la TV"** en el [README principal](../README.md#-cómo-generar-el-puente-de-comunicación-de-la-tv), es un paso obligatorio antes de probar este módulo.

---

## 📝 Notas de desarrollo

- No usa **Hilt**: a diferencia de `app` y `wear`, este módulo no tiene inyección de dependencias — `TvServer` y `TvDataStore` son `object` (singletons de Kotlin), suficiente para el tamaño del módulo.
- No usa **Navigation Component**: `MainActivity` cambia el Fragment visible a mano con `supportFragmentManager.beginTransaction().replace(...)`, porque solo hay 3 pantallas planas sin backstack real (navegación tipo "tabs" con control remoto).
- El mapa usa un **WebView** (no el SDK nativo de Maps) porque es la forma más simple de reutilizar el mismo HTML/JS de pines en ambos módulos (`app` y `tv`) sin duplicar lógica nativa de mapas.
- El puerto del servidor (`TvServer.PUERTO`) es **8766**. Si lo cambias, también hay que cambiarlo en `TvDataSender.PUERTO` (módulo `app`).
