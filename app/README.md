# 📱 Módulo Phone (`app/`)

App principal para el cliente y el administrador de la tienda de artesanías. Es el único módulo con base de datos propia (Room) — los módulos `tv` y `wear` son "pantallas satélite" que reciben datos desde aquí.

⬅️ [Volver al README principal](../README.md)

---

## 🎯 Qué hace

- **Autenticación** con roles Admin/Cliente (`AuthFragments.kt`, `AuthViewModel`)
- **Tienda**: catálogo con búsqueda en tiempo real, carrito, checkout (`store/`)
- **Mis Órdenes**: historial de compras del cliente
- **Panel de Admin**: gestión de productos, stock, usuarios, dashboard con estadísticas (`admin/`)
- **Cámara dual**: foto de producto (CameraX) + escaneo de QR para parear el reloj (ZXing) (`camera/`)
- **Talleres**: mapa de talleres artesanales (Google Maps en WebView, mismo HTML que usa el módulo `tv`)
- Envía datos en tiempo real a los otros dos módulos: notificaciones al reloj vía Wearable Data Layer, y catálogo/ventas/alertas a la TV vía socket TCP

---

## 🛠️ Stack y librerías

| Librería | Versión | Para qué |
|---|---|---|
| `com.google.dagger:hilt-android` + `hilt-compiler` (ksp) | 2.50 | Inyección de dependencias en toda la app (`di/AppModule.kt`) |
| `androidx.room` (runtime/ktx/compiler) | 2.6.1 | Base de datos local (`data/local/`) |
| `androidx.navigation` (fragment-ktx/ui-ktx) | 2.7.6 | Navigation Component, `nav_graph.xml`, control de acceso por rol |
| `androidx.lifecycle` (viewmodel/livedata/runtime-ktx) | 2.7.0 | ViewModels + LiveData/Flow |
| `androidx.camera` (core/camera2/lifecycle/view/extensions) | 1.3.1 | CameraX — foto de producto |
| `com.google.zxing:core` + `zxing-android-embedded` | 3.5.2 / 4.3.0 | Generar y escanear códigos QR para parear el reloj |
| `com.github.bumptech.glide` + compiler (ksp) | 4.16.0 | Carga de imágenes de producto |
| `com.google.android.gms:play-services-wearable` | 18.1.0 | Wearable Data Layer — comunicación con el reloj |
| `com.google.android.gms:play-services-maps` | 18.2.0 | Mapa de talleres |
| `kotlinx-coroutines-android` + `-play-services` | 1.7.3 | Corrutinas y `.await()` sobre Tasks de Play Services |
| `androidx.hilt:hilt-navigation-fragment` | 1.1.0 | `by viewModels()` con Hilt dentro de Fragments |
| `pub.devrel:easypermissions` | 3.0.0 | Permisos en tiempo de ejecución (cámara) |

---

## 🏗️ Estructura

```
app/src/main/java/com/artesanias/app/
├── data/
│   ├── local/        # Room: ArtesaniasDatabase, Daos
│   ├── model/         # Entities (@Entity) y data classes
│   ├── remote/         # TvDataSender (socket TCP) y WearableDataListenerService
│   └── repository/     # Repositorios: login, productos, usuarios, órdenes
├── di/
│   └── AppModule.kt    # @Module de Hilt
├── ui/
│   ├── admin/           # Gestión de productos/usuarios, dashboard
│   ├── auth/            # Login y Registro
│   ├── camera/          # CameraX + escaneo QR
│   ├── shared/           # Adapters de RecyclerView compartidos
│   ├── store/            # Tienda, carrito, mis órdenes, talleres
│   ├── MainActivity.kt   # Única Activity, hospeda el NavHostFragment
│   └── ViewModels.kt     # AuthViewModel, TiendaViewModel, AdminProductosViewModel, etc.
├── util/               # SessionManager, HashUtil, QRUtil
└── ArtesaniasApplication.kt   # @HiltAndroidApp
```

Ver también la [arquitectura completa de los 3 módulos](../README.md#️-arquitectura) en el README principal.

---

## 🚀 Instalación

### 1. `local.properties`

Crea (o edita) `local.properties` en la **raíz del proyecto** (no dentro de `app/`) con tu API key de Google Maps:

```properties
MAPS_API_KEY=tu_api_key_aquí
```

### 2. Instalar

```
Run → Run 'app'    # En dispositivo físico o emulador API 26+
```

### 3. Credenciales de prueba

Ver [Credenciales por defecto](../README.md#-credenciales-por-defecto) en el README principal.

---

## 📝 Notas de desarrollo

- `AuthViewModel` es `activityViewModels()` en **todos** los Fragments que lo usan (incluido `AdminDashboardFragment`) para compartir una sola instancia con la sesión activa; usar `viewModels()` (scope de Fragment) ahí rompe el cierre/cambio de sesión — fue un bug real encontrado y corregido, ver [Problemas conocidos](../README.md#-problemas-conocidos--corregidos).
- Los resultados de login/registro/orden usan `LiveData` **nullable** con un método `consumir...()` explícito para evitar el problema clásico de "sticky LiveData" (un Fragment que vuelve a observar recibe el resultado viejo).
- `TvDataSender` es best-effort: si la TV no está conectada, la operación de la app (crear orden, actualizar stock) sigue igual — nunca debe fallar por culpa de la TV.
- Contraseñas con SHA-256 (sin salt) — ver [Seguridad](../README.md#-seguridad) en el README principal para el porqué y la recomendación de producción.
