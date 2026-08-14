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

---

## 📄 Código fuente completo

### `app/src/main/java/com/artesanias/app/ArtesaniasApplication.kt`

```kotlin
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

```

### `app/src/main/java/com/artesanias/app/di/AppModule.kt`

```kotlin
package com.artesanias.app.di

import android.content.Context
import com.artesanias.app.data.local.*
import com.artesanias.app.data.remote.TvDataSender
import com.artesanias.app.data.remote.WearDataSender
import com.artesanias.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de inyección de dependencias de Hilt: le enseña al framework
 * CÓMO construir los objetos que las clases piden en su constructor (los
 * DAOs y repositorios), ya que esas clases no tienen un constructor
 * `@Inject` "automático" tan simple (dependen de la base de datos, que a
 * su vez necesita el Context de la app).
 *
 * - `@Module`: marca esta clase/objeto como una fuente de instrucciones
 *   de inyección para Hilt.
 * - `@InstallIn(SingletonComponent::class)`: dice que estas instrucciones
 *   viven mientras viva la aplicación completa (no una Activity o
 *   Fragment en particular), coherente con que todo aquí se provee como
 *   `@Singleton` (una sola instancia compartida por toda la app).
 * - `@Provides`: marca cada función como "así se construye este tipo".
 *   Hilt lee los parámetros de la función para saber qué otras
 *   dependencias necesita resolver primero (p.ej. para dar un
 *   `ProductoRepository` primero resuelve su `ProductoDao`, que a su vez
 *   necesita la `ArtesaniasDatabase`).
 * - `@ApplicationContext`: le pide a Hilt específicamente el Context de la
 *   aplicación (no el de una Activity), que es el que hay que usar aquí
 *   porque estos objetos sobreviven a cualquier pantalla.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ArtesaniasDatabase =
        ArtesaniasDatabase.getInstance(ctx)

    // Cada DAO se obtiene directamente de la base de datos ya construida
    // (Room genera la implementación real de estos métodos `abstract`).
    @Provides @Singleton
    fun provideUsuarioDao(db: ArtesaniasDatabase) = db.usuarioDao()

    @Provides @Singleton
    fun provideCategoriaDao(db: ArtesaniasDatabase) = db.categoriaDao()

    @Provides @Singleton
    fun provideProductoDao(db: ArtesaniasDatabase) = db.productoDao()

    @Provides @Singleton
    fun provideOrdenDao(db: ArtesaniasDatabase) = db.ordenDao()

    @Provides @Singleton
    fun provideDetalleOrdenDao(db: ArtesaniasDatabase) = db.detalleOrdenDao()

    @Provides @Singleton
    fun provideNotificacionDao(db: ArtesaniasDatabase) = db.notificacionDao()

    @Provides @Singleton
    fun provideAuthRepository(usuarioDao: UsuarioDao) = AuthRepository(usuarioDao)

    @Provides @Singleton
    fun provideWearSender(@ApplicationContext ctx: Context) = WearDataSender(ctx)

    // Los repositorios que dependen de otros repositorios (ProductoRepository
    // dentro de OrdenRepository) se resuelven en cadena: Hilt ve que
    // provideOrdenRepository pide un ProductoRepository, busca cómo
    // proveerlo (la función de abajo) y lo construye primero.
    @Provides @Singleton
    fun provideProductoRepository(
        productoDao: ProductoDao,
        notificacionDao: NotificacionDao,
        wearSender: WearDataSender,
        tvSender: TvDataSender
    ) = ProductoRepository(productoDao, notificacionDao, wearSender, tvSender)

    @Provides @Singleton
    fun provideOrdenRepository(
        ordenDao: OrdenDao,
        detalleOrdenDao: DetalleOrdenDao,
        productoRepo: ProductoRepository,
        notificacionDao: NotificacionDao,
        wearSender: WearDataSender,
        tvSender: TvDataSender
    ) = OrdenRepository(ordenDao, detalleOrdenDao, productoRepo, notificacionDao, wearSender, tvSender)

    @Provides @Singleton
    fun provideUsuarioRepository(usuarioDao: UsuarioDao) = UsuarioRepository(usuarioDao)
}

```

### `app/src/main/java/com/artesanias/app/data/model/Models.kt`

```kotlin
package com.artesanias.app.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

// ─────────────────────────────────────────────────────────────────────────
// Entidades de Room: cada `data class` marcada con `@Entity` representa una
// tabla de la base de datos SQLite (una fila por instancia, una columna
// por propiedad). Anotaciones usadas en este archivo:
//   @Entity(tableName = ...)  nombre real de la tabla en SQLite.
//   @PrimaryKey(autoGenerate = true)  la columna `id` es la llave primaria
//       y SQLite le asigna el siguiente número disponible al insertar
//       (no hace falta indicarlo a mano).
//   @ForeignKey  declara una relación con otra tabla y qué hacer si se
//       borra la fila "padre": CASCADE borra también las filas hijas
//       (p.ej. borrar una orden borra su detalle), RESTRICT bloquea el
//       borrado si hay hijos dependientes (no se puede borrar un producto
//       que ya aparece en el detalle de una orden), SET_NULL deja la
//       referencia en null en vez de borrar (una categoría se puede
//       eliminar sin arrastrar sus productos).
//   @Index  crea un índice sobre la columna de la llave foránea, para que
//       Room no tenga que recorrer toda la tabla al buscar por ella.
//   @Parcelize  genera automáticamente la implementación de `Parcelable`
//       (el mecanismo de Android para pasar objetos entre pantallas/
//       Activities vía Bundle/Intent) a partir de las propiedades del
//       constructor, sin tener que escribirla a mano.
// ─────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────
// USUARIO
// ─────────────────────────────────────────────
@Parcelize
@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val apellido: String,
    val email: String,
    val passwordHash: String,
    val rol: RolUsuario = RolUsuario.CLIENTE,
    val telefono: String = "",
    val activo: Boolean = true,
    val fechaRegistro: Long = System.currentTimeMillis()
) : Parcelable

enum class RolUsuario { ADMIN, CLIENTE }

// ─────────────────────────────────────────────
// CATEGORÍA
// ─────────────────────────────────────────────
@Parcelize
@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val descripcion: String = "",
    val imagenUrl: String = ""
) : Parcelable

// ─────────────────────────────────────────────
// PRODUCTO
// ─────────────────────────────────────────────
@Parcelize
@Entity(
    tableName = "productos",
    // Si se borra una categoría, sus productos NO se borran: solo pierden
    // la referencia (categoriaId queda en null) para no perder el catálogo.
    foreignKeys = [ForeignKey(
        entity = Categoria::class,
        parentColumns = ["id"],
        childColumns = ["categoriaId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoriaId")]
)
data class Producto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val descripcion: String,
    val precio: Double,
    val stock: Int,
    val categoriaId: Int? = null,
    val imagenPath: String = "",   // ruta local de foto tomada con cámara
    val tecnica: String = "",       // Talavera, Barro negro, etc.
    val origen: String = "",        // ciudad/estado artesano
    val artesano: String = "",
    val activo: Boolean = true,
    val fechaCreacion: Long = System.currentTimeMillis()
) : Parcelable {
    // Propiedad calculada (no es una columna real): se recalcula cada vez
    // que se lee, siempre a partir del stock actual.
    val stockBajo: Boolean get() = stock <= 5
}

// ─────────────────────────────────────────────
// ORDEN / PEDIDO
// ─────────────────────────────────────────────
@Parcelize
@Entity(
    tableName = "ordenes",
    // Si se borra un usuario, sus órdenes se borran con él (no tiene
    // sentido conservar pedidos huérfanos de una cuenta que ya no existe).
    foreignKeys = [ForeignKey(
        entity = Usuario::class,
        parentColumns = ["id"],
        childColumns = ["usuarioId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("usuarioId")]
)
data class Orden(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val usuarioId: Int,
    val total: Double,
    val estado: EstadoOrden = EstadoOrden.PENDIENTE,
    val fecha: Long = System.currentTimeMillis(),
    val notas: String = "",
    val confirmada: Boolean = false   // para compras > 1000 MXN
) : Parcelable {
    // Umbrales de negocio que disparan las alertas al reloj y a la TV
    // (ver OrdenRepository.crearOrden): toda compra > $500 se considera
    // "grande" y se notifica; > $1000 además requiere una confirmación
    // explícita desde el reloj antes de darse por buena.
    val esCompraGrande: Boolean get() = total > 500.0
    val requiereConfirmacion: Boolean get() = total > 1000.0
}

enum class EstadoOrden { PENDIENTE, CONFIRMADA, EN_PROCESO, ENVIADA, ENTREGADA, CANCELADA }

// ─────────────────────────────────────────────
// DETALLE DE ORDEN
// ─────────────────────────────────────────────
@Parcelize
@Entity(
    tableName = "detalle_orden",
    foreignKeys = [
        // Si se borra la orden, se borra su detalle (las líneas de
        // productos no tienen sentido sin la orden a la que pertenecen).
        ForeignKey(
            entity = Orden::class,
            parentColumns = ["id"],
            childColumns = ["ordenId"],
            onDelete = ForeignKey.CASCADE
        ),
        // En cambio, un producto que ya aparece en el detalle de alguna
        // orden NO se puede borrar (RESTRICT): se protege el historial de
        // ventas. Por eso el catálogo usa borrado lógico (activo = 0) en
        // vez de un DELETE real (ver ProductoDao.desactivarProducto).
        ForeignKey(
            entity = Producto::class,
            parentColumns = ["id"],
            childColumns = ["productoId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("ordenId"), Index("productoId")]
)
data class DetalleOrden(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ordenId: Int,
    val productoId: Int,
    val cantidad: Int,
    val precioUnitario: Double,
    val subtotal: Double = cantidad * precioUnitario
) : Parcelable

// ─────────────────────────────────────────────
// NOTIFICACIÓN (para historial)
// ─────────────────────────────────────────────
@Entity(tableName = "notificaciones")
data class Notificacion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tipo: TipoNotificacion,
    val titulo: String,
    val mensaje: String,
    val datos: String = "",   // JSON extra (productoId, ordenId, etc.)
    val leida: Boolean = false,
    val fecha: Long = System.currentTimeMillis()
)

enum class TipoNotificacion {
    STOCK_BAJO,
    COMPRA_GRANDE,
    COMPRA_MUY_GRANDE,
    SISTEMA
}

// ─────────────────────────────────────────────
// CARRITO (en memoria, no es una tabla de Room)
// ─────────────────────────────────────────────
// El carrito de compras vive solo en memoria (en el ViewModel) mientras el
// cliente arma su pedido; no se guarda en la base de datos hasta que se
// confirma la compra y se convierte en una Orden + una lista de
// DetalleOrden. Por eso no lleva `@Entity`.
data class ItemCarrito(
    val producto: Producto,
    var cantidad: Int = 1
) {
    val subtotal: Double get() = producto.precio * cantidad
}

```

### `app/src/main/java/com/artesanias/app/data/local/ArtesaniasDatabase.kt`

```kotlin
package com.artesanias.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.artesanias.app.data.model.*
import com.artesanias.app.util.HashUtil

/**
 * Base de datos local de la app, implementada con Room (la capa de
 * persistencia sobre SQLite que usa Android).
 *
 * Anotaciones de Room usadas aquí:
 * - `@Database`: marca esta clase como la base de datos. `entities` lista
 *   las tablas (una por cada `@Entity` en Models.kt); `version` es el
 *   número de esquema (hay que subirlo si se cambian las tablas, junto con
 *   una migración); `exportSchema = false` evita que Room genere un
 *   archivo JSON del esquema en cada compilación (no se usa en este
 *   proyecto porque no hay migraciones formales todavía).
 * - `@TypeConverters`: registra la clase `Converters` de abajo, que le
 *   enseña a Room a guardar tipos que SQLite no entiende de forma nativa
 *   (aquí, los `enum class` como `RolUsuario` o `EstadoOrden`).
 *
 * La clase es `abstract`: Room genera en tiempo de compilación la
 * implementación real de cada método `abstract fun ...Dao()`, devolviendo
 * el DAO correspondiente ya conectado a esta base de datos.
 */
@Database(
    entities = [
        Usuario::class,
        Categoria::class,
        Producto::class,
        Orden::class,
        DetalleOrden::class,
        Notificacion::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ArtesaniasDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun productoDao(): ProductoDao
    abstract fun ordenDao(): OrdenDao
    abstract fun detalleOrdenDao(): DetalleOrdenDao
    abstract fun notificacionDao(): NotificacionDao

    companion object {
        // @Volatile asegura que la escritura de INSTANCE sea visible de
        // inmediato para todos los hilos (sin esto, un hilo podría ver una
        // copia "cacheada" desactualizada y crear una segunda instancia).
        @Volatile private var INSTANCE: ArtesaniasDatabase? = null

        /**
         * Devuelve la única instancia de la base de datos (patrón
         * singleton). El bloque `synchronized` evita que dos hilos
         * construyan la base de datos al mismo tiempo si ambos llegan
         * aquí antes de que `INSTANCE` se haya asignado.
         */
        fun getInstance(context: Context): ArtesaniasDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, ArtesaniasDatabase::class.java, "artesanias.db")
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Sembrado con SQL crudo sobre el mismo `db` que entrega el
                            // callback, en vez de usar los DAOs de Room: los DAOs despachan
                            // su trabajo al executor de transacciones de Room, que procesa
                            // una tarea a la vez y todavía está "ocupado" ejecutando este
                            // mismo onCreate, así que llamarlos aquí (incluso con
                            // runBlocking en otro dispatcher) deadlockea. Con SQL directo el
                            // sembrado corre síncronamente dentro de esta misma transacción
                            // de apertura, así que termina antes de que la BD quede
                            // disponible para cualquier query posterior (p.ej. login).
                            poblarDatosIniciales(db)
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * Inserta el usuario administrador, el cliente de prueba, las
         * categorías y el catálogo inicial de productos la primera vez
         * que se crea la base de datos (solo se ejecuta si el archivo
         * `artesanias.db` no existía todavía en el dispositivo).
         */
        private fun poblarDatosIniciales(db: SupportSQLiteDatabase) {
            // Funciones locales auxiliares: arman el ContentValues (el
            // formato fila-columna que espera SQLiteDatabase.insert) para
            // cada tabla, para no repetir el `.apply { put(...) }` en cada
            // fila de ejemplo de abajo.
            fun usuario(nombre: String, apellido: String, email: String, pass: String, rol: RolUsuario) =
                ContentValues().apply {
                    put("nombre", nombre)
                    put("apellido", apellido)
                    put("email", email)
                    // Nunca se guarda la contraseña en texto plano, solo su hash.
                    put("passwordHash", HashUtil.hash(pass))
                    put("rol", rol.name)
                    put("telefono", "")
                    put("activo", 1)
                    put("fechaRegistro", System.currentTimeMillis())
                }

            fun categoria(nombre: String, descripcion: String) =
                ContentValues().apply {
                    put("nombre", nombre)
                    put("descripcion", descripcion)
                    put("imagenUrl", "")
                }

            fun producto(
                nombre: String, descripcion: String, precio: Double, stock: Int,
                categoriaId: Long, tecnica: String, origen: String, artesano: String
            ) = ContentValues().apply {
                put("nombre", nombre)
                put("descripcion", descripcion)
                put("precio", precio)
                put("stock", stock)
                put("categoriaId", categoriaId)
                put("imagenPath", "")
                put("tecnica", tecnica)
                put("origen", origen)
                put("artesano", artesano)
                put("activo", 1)
                put("fechaCreacion", System.currentTimeMillis())
            }

            // CONFLICT_ABORT: si por alguna razón ya existiera una fila que
            // choca (p.ej. mismo email), cancela esa inserción y lanza un
            // error en vez de sobrescribir en silencio.
            fun insert(table: String, cv: ContentValues) =
                db.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv)

            // Admin y cliente de prueba (ver credenciales en el README)
            insert("usuarios", usuario("Administrador", "Sistema", "admin@artesanias.mx", "Admin123", RolUsuario.ADMIN))
            insert("usuarios", usuario("María", "González", "cliente@artesanias.mx", "Cliente123", RolUsuario.CLIENTE))

            // Categorías. insert() devuelve el rowid autogenerado de cada
            // una, que se reutiliza abajo como categoriaId de los productos.
            val catTalavera = insert("categorias", categoria("Talavera", "Cerámica tradicional de Puebla"))
            val catBarro = insert("categorias", categoria("Barro Negro", "Alfarería de Oaxaca"))
            val catMayolica = insert("categorias", categoria("Mayólica", "Cerámica esmaltada"))
            val catBandeja = insert("categorias", categoria("Utilitaria", "Piezas de uso diario"))

            // Productos de ejemplo
            insert("productos", producto(
                "Plato Talavera Grande", "Plato decorativo tradicional de Puebla con motivos florales",
                350.0, 12, catTalavera, "Talavera", "Puebla", "Familia Uriarte"
            ))
            insert("productos", producto(
                "Vasija Barro Negro", "Pieza única de barro negro pulido de Oaxaca",
                480.0, 4, catBarro, "Barro Negro", "San Bartolo Coyotepec, Oaxaca", "Rosa Nieto"
            ))
            insert("productos", producto(
                "Jarro Talavera", "Jarro con asa, decoración azul cobalto",
                180.0, 20, catTalavera, "Talavera", "Dolores Hidalgo, Gto", "Taller del Sol"
            ))
            insert("productos", producto(
                "Cazuela de Barro", "Cazuela para cocinar, resistente al calor",
                220.0, 8, catBandeja, "Alfarería Utilitaria", "Michoacán", "Cooperativa La Barro"
            ))
            insert("productos", producto(
                "Jarrón Mayólica", "Jarrón alto con esmalte y decoración policromada",
                650.0, 3, catMayolica, "Mayólica", "Guanajuato", "Taller Gorky"
            ))
            insert("productos", producto(
                "Tazón Barro Rojo", "Tazón para sopas, barro rojo natural",
                95.0, 2, catBandeja, "Barro Rojo", "Tlaquepaque, Jalisco", "Artesanos Unidos"
            ))
            insert("productos", producto(
                "Florero Talavera Mini", "Florero pequeño para decoración, multicolor",
                120.0, 15, catTalavera, "Talavera", "Puebla", "Familia Uriarte"
            ))
            insert("productos", producto(
                "Incensario Barro Negro", "Incensario ritual de barro negro, pieza ceremonial",
                380.0, 5, catBarro, "Barro Negro", "San Marcos Tlapazola, Oaxaca", "Maestro Jiménez"
            ))
        }
    }
}

/**
 * Convertidores de tipos para Room: SQLite solo entiende tipos primitivos
 * (texto, números, blobs), así que los `enum class` del modelo (que no son
 * primitivos) necesitan una función de ida (`@TypeConverter` que los pasa
 * a `String`, con `.name`) y una de vuelta (`String` a enum, con
 * `.valueOf(s)`) para poder guardarse y leerse de una columna de texto.
 */
class Converters {
    @TypeConverter fun fromRol(rol: RolUsuario): String = rol.name
    @TypeConverter fun toRol(s: String): RolUsuario = RolUsuario.valueOf(s)

    @TypeConverter fun fromEstado(e: EstadoOrden): String = e.name
    @TypeConverter fun toEstado(s: String): EstadoOrden = EstadoOrden.valueOf(s)

    @TypeConverter fun fromTipoNotif(t: TipoNotificacion): String = t.name
    @TypeConverter fun toTipoNotif(s: String): TipoNotificacion = TipoNotificacion.valueOf(s)
}

```

### `app/src/main/java/com/artesanias/app/data/local/Daos.kt`

```kotlin
package com.artesanias.app.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.artesanias.app.data.model.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────
// DAOs (Data Access Objects) de Room. Cada interfaz marcada con `@Dao`
// define las operaciones SQL permitidas sobre una tabla; Room genera la
// implementación real en tiempo de compilación a partir de las anotaciones:
//   @Query      SQL de lectura (o de escritura, como los UPDATE de abajo)
//               escrito a mano; Room valida la sintaxis y el mapeo de
//               columnas a propiedades en tiempo de compilación.
//   @Insert     genera el INSERT automáticamente a partir del objeto.
//               `onConflict` decide qué hacer si choca una fila existente
//               (ABORT = cancelar y lanzar error, REPLACE = sobrescribir).
//   @Update     genera el UPDATE automáticamente, por el `id` del objeto.
//   @Delete     genera el DELETE automáticamente, por el `id` del objeto.
//
// Tipo de retorno de cada método:
//   Flow<T>     lectura "reactiva": Room vuelve a emitir automáticamente
//               cada vez que cambian las filas de las que depende la
//               consulta (ideal para observar desde un ViewModel/UI, que
//               así se actualiza solo, sin volver a pedir los datos).
//   suspend fun lectura o escritura "de una sola vez", pensada para
//               llamarse desde una corrutina cuando no se necesita seguir
//               observando cambios (p.ej. leer un producto por id antes de
//               procesar una compra).
// ─────────────────────────────────────────────────────────────────────────

// ───────────── USUARIO DAO ─────────────
@Dao
interface UsuarioDao {

    @Query("SELECT * FROM usuarios ORDER BY nombre ASC")
    fun getAllUsuarios(): Flow<List<Usuario>>

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun getUsuarioById(id: Int): Usuario?

    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    suspend fun getUsuarioByEmail(email: String): Usuario?

    // Compara el hash de la contraseña, nunca la contraseña en texto plano
    // (ver HashUtil.hash en util/Utils.kt). `activo = 1` evita que una
    // cuenta desactivada pueda iniciar sesión aunque la contraseña sea correcta.
    @Query("SELECT * FROM usuarios WHERE email = :email AND passwordHash = :hash AND activo = 1 LIMIT 1")
    suspend fun login(email: String, hash: String): Usuario?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUsuario(usuario: Usuario): Long

    @Update
    suspend fun updateUsuario(usuario: Usuario)

    @Query("UPDATE usuarios SET activo = :activo WHERE id = :id")
    suspend fun setActivo(id: Int, activo: Boolean)

    @Query("SELECT COUNT(*) FROM usuarios WHERE email = :email")
    suspend fun emailExiste(email: String): Int
}

// ───────────── PRODUCTO DAO ─────────────
@Dao
interface ProductoDao {

    // Catálogo visible para clientes: solo productos activos.
    @Query("SELECT * FROM productos WHERE activo = 1 ORDER BY nombre ASC")
    fun getAllProductos(): Flow<List<Producto>>

    // Vista de administrador: incluye también los productos desactivados,
    // para poder reactivarlos o darles seguimiento.
    @Query("SELECT * FROM productos ORDER BY nombre ASC")
    fun getAllProductosAdmin(): Flow<List<Producto>>

    // Para enviar el snapshot del catálogo a la TV
    @Query("SELECT * FROM productos WHERE activo = 1 ORDER BY nombre ASC")
    suspend fun getAllProductosSync(): List<Producto>

    @Query("SELECT * FROM productos WHERE id = :id")
    suspend fun getProductoById(id: Int): Producto?

    @Query("SELECT * FROM productos WHERE categoriaId = :categoriaId AND activo = 1")
    fun getProductosByCategoria(categoriaId: Int): Flow<List<Producto>>

    @Query("SELECT * FROM productos WHERE stock <= 5 AND activo = 1")
    fun getProductosConStockBajo(): Flow<List<Producto>>

    // Versión suspend (de una sola lectura) del query de arriba, para
    // usarla desde WearableDataListenerService: un Service no tiene un
    // ciclo de vida al que atar un observador de Flow, así que necesita
    // pedir el dato una vez y responder, no quedarse escuchando cambios.
    @Query("SELECT * FROM productos WHERE stock <= 5 AND activo = 1 ORDER BY stock ASC")
    suspend fun getProductosConStockBajoSync(): List<Producto>

    // Búsqueda simple por coincidencia parcial (LIKE) en varias columnas a
    // la vez, para que el buscador de la tienda encuentre productos tanto
    // por nombre como por técnica, artesano o descripción.
    @Query("""SELECT * FROM productos WHERE activo = 1 AND
              (nombre LIKE '%' || :q || '%' OR descripcion LIKE '%' || :q || '%'
               OR artesano LIKE '%' || :q || '%' OR tecnica LIKE '%' || :q || '%')""")
    fun buscarProductos(q: String): Flow<List<Producto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducto(producto: Producto): Long

    @Update
    suspend fun updateProducto(producto: Producto)

    @Query("UPDATE productos SET stock = stock + :cantidad WHERE id = :id")
    suspend fun agregarStock(id: Int, cantidad: Int)

    // La condición `stock >= :cantidad` en el WHERE es la protección real
    // contra vender más unidades de las que hay: si no alcanza el stock,
    // el UPDATE no afecta ninguna fila (nunca deja el stock en negativo) y
    // devuelve 0, que el repositorio usa para saber que la operación no
    // se pudo completar.
    @Query("UPDATE productos SET stock = stock - :cantidad WHERE id = :id AND stock >= :cantidad")
    suspend fun reducirStock(id: Int, cantidad: Int): Int

    // "Eliminar" un producto en realidad solo lo desactiva (borrado
    // lógico): así no rompe el historial de órdenes que ya lo referencian.
    @Query("UPDATE productos SET activo = 0 WHERE id = :id")
    suspend fun desactivarProducto(id: Int)
}

// ───────────── CATEGORÍA DAO ─────────────
@Dao
interface CategoriaDao {

    @Query("SELECT * FROM categorias ORDER BY nombre ASC")
    fun getAllCategorias(): Flow<List<Categoria>>

    @Query("SELECT * FROM categorias WHERE id = :id")
    suspend fun getCategoriaById(id: Int): Categoria?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoria(categoria: Categoria): Long

    @Update
    suspend fun updateCategoria(categoria: Categoria)

    @Delete
    suspend fun deleteCategoria(categoria: Categoria)
}

// ───────────── ORDEN DAO ─────────────
@Dao
interface OrdenDao {

    @Query("SELECT * FROM ordenes ORDER BY fecha DESC")
    fun getAllOrdenes(): Flow<List<Orden>>

    @Query("SELECT * FROM ordenes WHERE usuarioId = :usuarioId ORDER BY fecha DESC")
    fun getOrdenesByUsuario(usuarioId: Int): Flow<List<Orden>>

    @Query("SELECT * FROM ordenes WHERE id = :id")
    suspend fun getOrdenById(id: Int): Orden?

    @Insert
    suspend fun insertOrden(orden: Orden): Long

    @Update
    suspend fun updateOrden(orden: Orden)

    @Query("UPDATE ordenes SET estado = :estado WHERE id = :id")
    suspend fun cambiarEstado(id: Int, estado: EstadoOrden)

    @Query("UPDATE ordenes SET confirmada = 1 WHERE id = :id")
    suspend fun confirmarOrden(id: Int)

    // Para el resumen semanal que se envía a la TV (pantalla de ventas).
    // Hace JOIN con usuarios para poder mostrar el nombre del cliente sin
    // que la TV tenga que pedir esa tabla aparte.
    @Query("""
        SELECT o.fecha AS fecha, (u.nombre || ' ' || u.apellido) AS cliente, o.total AS total
        FROM ordenes o
        INNER JOIN usuarios u ON u.id = o.usuarioId
        WHERE o.fecha >= :desde
        ORDER BY o.fecha ASC
    """)
    suspend fun getComprasDesde(desde: Long): List<CompraResumen>

    // Ranking de productos más vendidos en el periodo: suma las cantidades
    // vendidas por producto (agrupando el detalle de todas las órdenes) y
    // se queda con el top 6, para la gráfica de barras de la TV.
    @Query("""
        SELECT p.nombre AS nombre, SUM(d.cantidad) AS cantidad
        FROM detalle_orden d
        INNER JOIN ordenes o ON o.id = d.ordenId
        INNER JOIN productos p ON p.id = d.productoId
        WHERE o.fecha >= :desde
        GROUP BY d.productoId
        ORDER BY cantidad DESC
        LIMIT 6
    """)
    suspend fun getMasVendidosDesde(desde: Long): List<VentaProducto>
}

// Resultados de las consultas @Query de arriba: Room los llena por nombre
// de columna (los alias `AS fecha`, `AS cliente`, etc.), no necesitan ser
// @Entity porque no representan una tabla, solo el resultado de un JOIN.
data class CompraResumen(val fecha: Long, val cliente: String, val total: Double)
data class VentaProducto(val nombre: String, val cantidad: Int)

// ───────────── DETALLE ORDEN DAO ─────────────
@Dao
interface DetalleOrdenDao {

    @Query("SELECT * FROM detalle_orden WHERE ordenId = :ordenId")
    fun getDetallesByOrden(ordenId: Int): Flow<List<DetalleOrden>>

    @Insert
    suspend fun insertDetalle(detalle: DetalleOrden)

    // Inserta varias líneas de detalle de una sola vez (todos los
    // productos de un carrito al confirmar la compra), en una sola
    // transacción en vez de una llamada @Insert por cada producto.
    @Insert
    suspend fun insertDetalles(detalles: List<DetalleOrden>)
}

// ───────────── NOTIFICACIÓN DAO ─────────────
@Dao
interface NotificacionDao {

    @Query("SELECT * FROM notificaciones ORDER BY fecha DESC")
    fun getAllNotificaciones(): Flow<List<Notificacion>>

    @Query("SELECT COUNT(*) FROM notificaciones WHERE leida = 0")
    fun getNoLeidasCount(): Flow<Int>

    @Insert
    suspend fun insertNotificacion(notificacion: Notificacion)

    @Query("UPDATE notificaciones SET leida = 1 WHERE id = :id")
    suspend fun marcarLeida(id: Int)

    @Query("UPDATE notificaciones SET leida = 1")
    suspend fun marcarTodasLeidas()
}

```

### `app/src/main/java/com/artesanias/app/data/repository/Repositories.kt`

```kotlin
package com.artesanias.app.data.repository

import android.util.Log
import com.artesanias.app.data.local.*
import com.artesanias.app.data.model.*
import com.artesanias.app.data.remote.TvDataSender
import com.artesanias.app.data.remote.WearDataSender
import com.artesanias.app.util.HashUtil
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────
// Capa de repositorios: es la única capa que los ViewModels deberían tocar
// para leer o escribir datos. Cada repositorio junta un DAO de Room (la
// fuente de datos local) con los "senders" del reloj y la TV, para que la
// lógica de negocio (cuándo notificar, cuándo sincronizar) viva en un solo
// lugar y no se repita en cada pantalla.
//
// `@Singleton` + `@Inject constructor(...)`: Hilt crea una sola instancia
// de cada repositorio para toda la app y la entrega automáticamente a
// quien la pida en su constructor (ViewModels, Services, etc.), resolviendo
// también las dependencias del propio repositorio (los DAOs, los senders).
// ─────────────────────────────────────────────────────────────────────────

// ───────────── AUTH REPOSITORY ─────────────
@Singleton
class AuthRepository @Inject constructor(
    private val usuarioDao: UsuarioDao
) {
    /** Verifica credenciales contra el hash guardado; null si no coinciden o el usuario está inactivo. */
    suspend fun login(email: String, password: String): Usuario? {
        val hash = HashUtil.hash(password)
        return usuarioDao.login(email.trim().lowercase(), hash)
    }

    /** Crea una cuenta nueva (cliente por defecto), rechazando correos ya registrados. */
    suspend fun registrar(
        nombre: String, apellido: String,
        email: String, password: String,
        rol: RolUsuario = RolUsuario.CLIENTE
    ): Result<Long> {
        return try {
            val emailLimpio = email.trim().lowercase()
            if (usuarioDao.emailExiste(emailLimpio) > 0)
                return Result.failure(Exception("Este correo ya está registrado"))
            val id = usuarioDao.insertUsuario(
                Usuario(
                    nombre = nombre.trim(),
                    apellido = apellido.trim(),
                    email = emailLimpio,
                    passwordHash = HashUtil.hash(password),
                    rol = rol
                )
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ───────────── PRODUCTO REPOSITORY ─────────────
@Singleton
class ProductoRepository @Inject constructor(
    private val productoDao: ProductoDao,
    private val notificacionDao: NotificacionDao,
    private val wearSender: WearDataSender,
    private val tvSender: TvDataSender
) {
    fun getProductos(): Flow<List<Producto>> = productoDao.getAllProductos()
    fun getProductosAdmin(): Flow<List<Producto>> = productoDao.getAllProductosAdmin()
    fun getProductosStockBajo(): Flow<List<Producto>> = productoDao.getProductosConStockBajo()
    fun buscar(q: String): Flow<List<Producto>> = productoDao.buscarProductos(q)
    fun porCategoria(id: Int): Flow<List<Producto>> = productoDao.getProductosByCategoria(id)
    suspend fun getProductoById(id: Int): Producto? = productoDao.getProductoById(id)

    suspend fun insertarProducto(producto: Producto): Long =
        productoDao.insertProducto(producto).also { sincronizarCatalogoConTv() }

    /** Actualiza un producto y, si quedó con poco stock, dispara la alerta correspondiente al reloj. */
    suspend fun actualizarProducto(producto: Producto) {
        productoDao.updateProducto(producto)
        if (producto.stockBajo) notificarStockBajo(producto)
        sincronizarCatalogoConTv()
    }

    /** Suma unidades al inventario (reabastecimiento) y avisa al reloj y a la TV. */
    suspend fun agregarStock(productoId: Int, cantidad: Int) {
        productoDao.agregarStock(productoId, cantidad)
        wearSender.enviarMensaje(
            "/stock/actualizado",
            "Producto ID $productoId: +$cantidad unidades agregadas"
        )
        sincronizarCatalogoConTv()
    }

    /**
     * Descuenta stock de un producto de forma segura, y notifica al reloj
     * si el producto quedó con 5 unidades o menos.
     *
     * @return `true` si se pudo descontar (había stock suficiente),
     *   `false` si no (ver ProductoDao.reducirStock: el UPDATE con
     *   `WHERE stock >= :cantidad` no afecta ninguna fila en ese caso).
     */
    suspend fun reducirStock(productoId: Int, cantidad: Int): Boolean {
        // 1. Leer el producto ANTES de reducir para tener el stock actual
        val productoActual = productoDao.getProductoById(productoId) ?: return false

        // 2. Reducir en BD
        val rows = productoDao.reducirStock(productoId, cantidad)
        if (rows == 0) return false

        // 3. Calcular el stock resultante sin volver a consultar la BD
        val stockResultante = productoActual.stock - cantidad

        // 4. Si el stock resultante es <= 5, notificar con el valor correcto
        if (stockResultante <= 5) {
            val productoActualizado = productoActual.copy(stock = stockResultante)
            notificarStockBajo(productoActualizado)
        }

        return true
    }

    // Snapshot del catálogo hacia la pantalla 1 de la TV (ver TvDataSender/TvServer)
    suspend fun sincronizarCatalogoConTv() {
        tvSender.enviarCatalogo(productoDao.getAllProductosSync())
    }

    // Integra la alerta de stock bajo con Wear OS: persiste la notificación
    // localmente y la envía al reloj por el path "/alerta/stock".
    private suspend fun notificarStockBajo(producto: Producto) {
        // Formato "productoId:nombre:stock" para que el reloj pueda parsearlo
        val msgWear = "${producto.id}:${producto.nombre}:${producto.stock}"

        notificacionDao.insertNotificacion(
            Notificacion(
                tipo = TipoNotificacion.STOCK_BAJO,
                titulo = "⚠️ Stock bajo",
                mensaje = "Stock bajo: ${producto.nombre} tiene solo ${producto.stock} unidades",
                datos = """{"productoId":${producto.id},"stock":${producto.stock}}"""
            )
        )
        wearSender.enviarMensaje("/alerta/stock", msgWear)
    }
}

// ───────────── ORDEN REPOSITORY ─────────────
@Singleton
class OrdenRepository @Inject constructor(
    private val ordenDao: OrdenDao,
    private val detalleOrdenDao: DetalleOrdenDao,
    private val productoRepository: ProductoRepository,
    private val notificacionDao: NotificacionDao,
    private val wearSender: WearDataSender,
    private val tvSender: TvDataSender
) {
    fun getOrdenes(): Flow<List<Orden>> = ordenDao.getAllOrdenes()
    fun getMisOrdenes(usuarioId: Int): Flow<List<Orden>> = ordenDao.getOrdenesByUsuario(usuarioId)
    fun getDetalles(ordenId: Int): Flow<List<DetalleOrden>> =
        detalleOrdenDao.getDetallesByOrden(ordenId)

    /**
     * Confirma la compra de un carrito: valida stock, crea la orden y su
     * detalle, descuenta inventario, y dispara las notificaciones al
     * reloj (siempre) y a la TV (si la conexión está disponible). Es el
     * flujo central de negocio de toda la app.
     *
     * @return `Result.success` con la orden ya creada, o `Result.failure`
     *   si no había stock suficiente o algo más falló al guardarla.
     */
    suspend fun crearOrden(
        usuarioId: Int,
        items: List<ItemCarrito>
    ): Result<Orden> {
        return try {
            // Validar stock ANTES de crear la orden: reducirStock() ya protege
            // la base de datos (no deja bajar de 0), pero antes su resultado
            // se ignoraba, así que la compra se cobraba igual aunque no
            // hubiera stock suficiente para descontarlo.
            for (item in items) {
                val productoActual = productoRepository.getProductoById(item.producto.id)
                if (productoActual == null || productoActual.stock < item.cantidad) {
                    return Result.failure(
                        Exception("Sin stock suficiente de ${item.producto.nombre} (disponible: ${productoActual?.stock ?: 0})")
                    )
                }
            }

            val total = items.sumOf { it.subtotal }
            val orden = Orden(usuarioId = usuarioId, total = total)
            val ordenId = ordenDao.insertOrden(orden).toInt()

            val detalles = items.map { item ->
                DetalleOrden(
                    ordenId = ordenId,
                    productoId = item.producto.id,
                    cantidad = item.cantidad,
                    precioUnitario = item.producto.precio
                )
            }
            detalleOrdenDao.insertDetalles(detalles)

            // Reducir stock de cada producto
            items.forEach { item ->
                productoRepository.reducirStock(item.producto.id, item.cantidad)
            }

            val ordenCreada = orden.copy(id = ordenId)

            // ── Construir detalle de productos para mostrar en el reloj ──
            // Formato: "nombre:cantidad:precio|nombre:cantidad:precio|..."
            val detalleProductos = items.joinToString("|") { item ->
                "${item.producto.nombre}:${item.cantidad}:${String.format("%.2f", item.producto.precio)}"
            }

            // ── Revisar qué productos quedaron con stock bajo tras la compra ──
            // Formato: "id:nombre:stock|id:nombre:stock|..."
            val productosStockBajo = items.mapNotNull { item ->
                val productoActual = productoRepository.getProductoById(item.producto.id)
                if (productoActual != null && productoActual.stock <= 5) productoActual else null
            }
            val stockBajoPayload = productosStockBajo.joinToString("|") { p ->
                "${p.id}:${p.nombre}:${p.stock}"
            }

            // Notificaciones según monto (ver Orden.esCompraGrande /
            // requiereConfirmacion en Models.kt para los umbrales).
            when {
                ordenCreada.requiereConfirmacion -> {
                    val msg = "Compra de \$${String.format("%.2f", total)} requiere confirmación #$ordenId"
                    notificacionDao.insertNotificacion(
                        Notificacion(
                            tipo = TipoNotificacion.COMPRA_MUY_GRANDE,
                            titulo = "🔔 Compra muy grande",
                            mensaje = msg,
                            datos = """{"ordenId":$ordenId,"total":$total}"""
                        )
                    )
                    // Enviar mensaje con detalle y stock resultante.
                    // El reloj abre CompraAlertActivity con este payload extendido.
                    wearSender.enviarMensaje("/alerta/compra-grande",
                        "$msg||detalle=$detalleProductos||stockBajo=$stockBajoPayload")
                }
                ordenCreada.esCompraGrande -> {
                    val msg = "Nueva compra grande: \$${String.format("%.2f", total)} #$ordenId"
                    notificacionDao.insertNotificacion(
                        Notificacion(
                            tipo = TipoNotificacion.COMPRA_GRANDE,
                            titulo = "💰 Compra grande",
                            mensaje = msg,
                            datos = """{"ordenId":$ordenId,"total":$total}"""
                        )
                    )
                    wearSender.enviarMensaje("/alerta/compra-grande",
                        "$msg||detalle=$detalleProductos||stockBajo=$stockBajoPayload")
                }
            }

            // La sincronización con la TV es "best effort": si falla (p.ej. TV
            // apagada o sin red) no debe hacer fallar la compra, que ya quedó
            // guardada en la base de datos. Se aísla en su propio try/catch
            // con logging explícito para poder diagnosticar fallas de red.
            try {
                sincronizarVentasConTv()
                if (ordenCreada.esCompraGrande) {
                    val productoLabel = if (items.size == 1) {
                        items.first().producto.nombre
                    } else {
                        "${items.first().producto.nombre} (+${items.size - 1} más)"
                    }
                    tvSender.enviarCompraGrande(productoLabel, total)
                }
            } catch (e: Exception) {
                Log.e("OrdenRepository", "Fallo al sincronizar con la TV (orden #$ordenId ya se creó bien)", e)
            }

            Result.success(ordenCreada)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Marca una orden > $1000 como aprobada tras la confirmación del reloj. */
    suspend fun confirmarOrden(ordenId: Int) {
        ordenDao.confirmarOrden(ordenId)
        ordenDao.cambiarEstado(ordenId, EstadoOrden.CONFIRMADA)
        wearSender.enviarMensaje("/orden/confirmada", "Orden #$ordenId confirmada")
    }

    // Envía a la pantalla 2 de la TV el catálogo actualizado (cambió el stock)
    // junto con el resumen de los últimos 7 días.
    private suspend fun sincronizarVentasConTv() {
        productoRepository.sincronizarCatalogoConTv()
        val desde = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        tvSender.enviarComprasSemana(ordenDao.getComprasDesde(desde))
        tvSender.enviarMasVendidos(ordenDao.getMasVendidosDesde(desde))
    }
}

// ───────────── USUARIO REPOSITORY ─────────────
// Operaciones de administración de cuentas (panel de admin): listar,
// activar/desactivar y dar de alta usuarios sin pasar por el flujo de
// autologin de AuthRepository.
@Singleton
class UsuarioRepository @Inject constructor(
    private val usuarioDao: UsuarioDao
) {
    fun getUsuarios(): Flow<List<Usuario>> = usuarioDao.getAllUsuarios()

    suspend fun getUsuario(id: Int): Usuario? = usuarioDao.getUsuarioById(id)

    suspend fun actualizar(usuario: Usuario) = usuarioDao.updateUsuario(usuario)

    /** Activa o desactiva una cuenta (borrado lógico: un usuario inactivo no puede iniciar sesión). */
    suspend fun setActivo(id: Int, activo: Boolean) = usuarioDao.setActivo(id, activo)

    suspend fun insertar(
        nombre: String, apellido: String,
        email: String, password: String, rol: RolUsuario
    ): Result<Long> {
        return try {
            if (usuarioDao.emailExiste(email.lowercase()) > 0)
                return Result.failure(Exception("Email ya registrado"))
            val id = usuarioDao.insertUsuario(
                Usuario(
                    nombre = nombre, apellido = apellido,
                    email = email.lowercase(),
                    passwordHash = HashUtil.hash(password),
                    rol = rol
                )
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

```

### `app/src/main/java/com/artesanias/app/data/remote/TvDataSender.kt`

```kotlin
package com.artesanias.app.data.remote

import android.util.Log
import com.artesanias.app.data.local.CompraResumen
import com.artesanias.app.data.local.VentaProducto
import com.artesanias.app.data.model.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envía datos a la app de Android TV por un socket TCP (la Wearable Data
 * Layer que usa WearDataSender es exclusiva de Wear OS, Android TV no la
 * soporta). Ver TvServer.kt en el módulo tv para el protocolo y el puerto.
 *
 * HOST_DEFAULT es "127.0.0.1" a propósito: se probó primero con la IP WiFi
 * de la PC en la red local, pero el router bloquea la conexión iniciada
 * desde el celular hacia la PC (aislamiento de clientes WiFi) aunque el
 * sentido contrario sí funciona — no hay forma de arreglar eso desde la
 * app o desde el firewall de Windows, así que en vez de WiFi se usa el
 * cable USB con `adb reverse`, que crea un túnel 127.0.0.1:8765 (celular)
 * -> 127.0.0.1:8765 (PC) sin pasar por el router:
 *   adb -s <celular> reverse tcp:8765 tcp:8765
 *   adb -s <tv> forward tcp:8765 tcp:8765
 *   netsh interface portproxy add v4tov4 listenport=8765 listenaddress=0.0.0.0 connectport=8765 connectaddress=127.0.0.1
 * (el portproxy ya no es necesario para este flujo por USB, pero se deja
 * documentado por si se vuelve a usar WiFi en una red sin aislamiento).
 * El celular debe permanecer conectado por USB para que esto funcione.
 *
 * `@Singleton` + `@Inject constructor()`: le dice a Hilt (el framework de
 * inyección de dependencias) que exista una sola instancia de esta clase
 * en toda la app, compartida por quien la pida en su constructor (aquí no
 * se necesita porque no tiene estado que valga la pena compartir, pero es
 * el patrón consistente con el resto de los repositorios/servicios).
 */
@Singleton
class TvDataSender @Inject constructor() {
    private val TAG = "TvDataSender"
    private val diasSemana = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

    companion object {
        const val HOST_DEFAULT = "127.0.0.1"
        const val PUERTO = 8766
    }

    private suspend fun enviar(json: JSONObject) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Intentando enviar a $HOST_DEFAULT:$PUERTO -> ${json.optString("tipo")}")
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST_DEFAULT, PUERTO), 1500)
                socket.getOutputStream().write((json.toString() + "\n").toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                // Al ir por USB (adb reverse -> adb forward, dos saltos
                // encadenados) hay más latencia que en loopback puro; sin
                // esta espera el socket se cierra antes de que el dato
                // alcance a cruzar el segundo salto hacia el emulador de TV.
                delay(300)
            }
            Log.d(TAG, "Enviado a la TV correctamente: ${json.optString("tipo")}")
        } catch (e: Exception) {
            // La TV es opcional: si no está conectada, no debe afectar el flujo normal de la app.
            Log.w(TAG, "No se pudo enviar a la TV: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Manda el catálogo completo (solo productos activos) a la Pantalla 1 de la TV. */
    suspend fun enviarCatalogo(productos: List<Producto>) {
        val items = JSONArray()
        productos.filter { it.activo }.forEach { p ->
            items.put(JSONObject().apply {
                put("nombre", p.nombre)
                put("precio", p.precio)
                put("stock", p.stock)
                put("categoria", "")
            })
        }
        enviar(JSONObject().apply {
            put("tipo", "productos")
            put("items", items)
        })
    }

    /** Manda el top de productos más vendidos para la gráfica de barras de la Pantalla 2. */
    suspend fun enviarMasVendidos(items: List<VentaProducto>) {
        val arr = JSONArray()
        items.forEach { v ->
            arr.put(JSONObject().apply {
                put("nombre", v.nombre)
                put("cantidad", v.cantidad)
            })
        }
        enviar(JSONObject().apply {
            put("tipo", "mas_vendidos")
            put("items", arr)
        })
    }

    /** Manda la tabla de compras de los últimos 7 días para la Pantalla 2. */
    suspend fun enviarComprasSemana(items: List<CompraResumen>) {
        val formatoDia = SimpleDateFormat("EEE", Locale("es", "MX"))
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(JSONObject().apply {
                put("fecha", formatoDia.format(c.fecha).replaceFirstChar { it.uppercase() })
                put("cliente", c.cliente)
                put("total", c.total)
            })
        }
        enviar(JSONObject().apply {
            put("tipo", "compras_semana")
            put("items", arr)
        })
    }

    /** Dispara la alerta de "compra grande" que la TV muestra como overlay sobre cualquier pantalla. */
    suspend fun enviarCompraGrande(producto: String, monto: Double) {
        enviar(JSONObject().apply {
            put("tipo", "compra_grande")
            put("producto", producto)
            put("monto", monto)
        })
    }
}

```

### `app/src/main/java/com/artesanias/app/data/remote/WearableDataListenerService.kt`

```kotlin
package com.artesanias.app.data.remote

import android.content.Context
import android.util.Log
import com.artesanias.app.data.local.ArtesaniasDatabase
import com.google.android.gms.wearable.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// ─── Envío de mensajes al Wear OS ───
// Implementa la navegación phone-watch: cada "path" enviado aquí es escuchado
// por PhoneMessageListenerService en el módulo wear, que abre la Activity
// correspondiente (StockAlertActivity, CompraAlertActivity, etc.) usando
// el Wearable Data Layer API de Google Play Services.
/**
 * `@ApplicationContext`: calificador de Hilt que le pide específicamente el
 * Context de la aplicación (no el de una Activity), correcto aquí porque
 * esta clase es un singleton que vive más que cualquier pantalla.
 */
@Singleton
class WearDataSender @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "WearDataSender"

    /**
     * Envía un mensaje de texto simple al reloj por el Wearable Data
     * Layer API. `Wearable.getNodeClient(...).connectedNodes` devuelve los
     * dispositivos Wear OS actualmente emparejados y conectados (los
     * "nodos"); si no hay ninguno, no tiene caso intentar enviar. `path`
     * es la ruta que identifica el tipo de mensaje (p.ej.
     * "/alerta/stock"), que `PhoneMessageListenerService` en el reloj usa
     * como un `when` para decidir qué hacer con el mensaje.
     */
    suspend fun enviarMensaje(path: String, mensaje: String) {
        try {
            val nodes = Wearable.getNodeClient(context)
                .connectedNodes
                .await()

            if (nodes.isEmpty()) {
                Log.w(TAG, "Sin nodos conectados para enviar: $path")
                return
            }

            // Normalmente hay un solo reloj emparejado, pero se recorre
            // por si hubiera más de uno.
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, path, mensaje.toByteArray(Charsets.UTF_8))
                    .await()
                Log.d(TAG, "Mensaje enviado a ${node.displayName}: $path -> $mensaje")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje Wear: ${e.message}")
        }
    }

    /**
     * Variante que usa el DataClient en vez del MessageClient: en lugar de
     * un mensaje puntual, deja un valor persistente ("DataItem") que
     * Google Play Services sincroniza con el reloj incluso si en ese
     * momento no está conectado (se entrega en cuanto se reconecta).
     * `.setUrgent()` le pide al sistema que lo entregue lo antes posible
     * en vez de esperar a agrupar varios envíos.
     */
    suspend fun enviarDato(path: String, clave: String, valor: String) {
        try {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putString(clave, valor)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
                .await()
            Log.d(TAG, "Dato enviado: $path/$clave = $valor")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando dato Wear: ${e.message}")
        }
    }
}

// ─── Listener de mensajes DESDE el Wear OS ───
/**
 * `WearableListenerService` es una clase base de Google Play Services que
 * el sistema instancia automáticamente (no hace falta arrancarla a mano)
 * cada vez que llega un mensaje cuyo "path" coincide con alguno de los
 * `<intent-filter>` declarados para este servicio en AndroidManifest.xml.
 * Es el equivalente, en la dirección reloj → teléfono, de
 * PhoneMessageListenerService en el módulo wear.
 */
class WearableDataListenerService : WearableListenerService() {

    private val TAG = "WearListener"

    // Alcance de corrutinas propio del Service: SupervisorJob evita que si
    // una tarea falla, cancele a las demás; se cancela por completo en
    // onDestroy para no dejar corrutinas huérfanas corriendo de fondo.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /** Se llama automáticamente cada vez que el reloj manda un mensaje. */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val datos = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "Mensaje recibido del reloj: ${messageEvent.path} -> $datos")

        when (messageEvent.path) {

            // El reloj pide la lista de productos con stock bajo (pantalla
            // "Ajustar Inventario"): se responde de forma asíncrona porque
            // consultar la base de datos es una operación suspend.
            "/stock/lista" -> {
                val nodeId = messageEvent.sourceNodeId
                scope.launch {
                    responderListaStock(nodeId)
                }
            }

            // El reloj confirmó agregar stock a un producto. Formato del
            // mensaje: "productoId:cantidad".
            "/stock/agregar" -> {
                val partes = datos.split(":")
                if (partes.size == 2) {
                    val productoId = partes[0].toIntOrNull() ?: return
                    val cantidad = partes[1].toIntOrNull() ?: return
                    scope.launch {
                        agregarStockDirecto(productoId, cantidad)
                    }
                }
            }

            // El reloj confirmó una compra que requería aprobación
            // (> $1000). Se reenvía como broadcast local para que la
            // Activity del teléfono que esté abierta en ese momento pueda
            // reaccionar (p.ej. refrescar la lista de órdenes).
            "/orden/confirmar" -> {
                val ordenId = datos.toIntOrNull() ?: return
                val intent = android.content.Intent("com.artesanias.app.CONFIRMAR_ORDEN").apply {
                    putExtra("ordenId", ordenId)
                }
                sendBroadcast(intent)
            }

            "/ping" -> {
                Log.d(TAG, "Ping recibido del reloj")
            }
        }
    }

    /** Responde a "/stock/lista" con el catálogo bajo en stock, en formato "id:nombre:stock|...". */
    private suspend fun responderListaStock(nodeId: String) {
        try {
            // getInstance() es el método correcto según ArtesaniasDatabase
            val db = ArtesaniasDatabase.getInstance(applicationContext)
            val productos = db.productoDao().getProductosConStockBajoSync()

            val payload = if (productos.isEmpty()) {
                ""
            } else {
                productos.joinToString("|") { p -> "${p.id}:${p.nombre}:${p.stock}" }
            }

            Log.d(TAG, "Enviando lista de stock al reloj: ${productos.size} productos -> $payload")

            Wearable.getMessageClient(applicationContext)
                .sendMessage(nodeId, "/stock/lista/respuesta", payload.toByteArray())
                .await()

        } catch (e: Exception) {
            Log.e(TAG, "Error respondiendo lista de stock: ${e.message}")
        }
    }

    /** Aplica el "+cantidad" de stock que el reloj pidió y le confirma de vuelta. */
    private suspend fun agregarStockDirecto(productoId: Int, cantidad: Int) {
        try {
            val db = ArtesaniasDatabase.getInstance(applicationContext)
            db.productoDao().agregarStock(productoId, cantidad)
            Log.d(TAG, "Stock agregado directo: producto=$productoId, cantidad=$cantidad")

            val nodes = Wearable.getNodeClient(applicationContext).connectedNodes.await()
            nodes.forEach { node ->
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(
                        node.id,
                        "/stock/actualizado",
                        "Producto ID $productoId: +$cantidad unidades".toByteArray()
                    ).await()
            }

            // Broadcast para refrescar UI del teléfono si está abierta
            val intent = android.content.Intent("com.artesanias.app.AGREGAR_STOCK").apply {
                putExtra("productoId", productoId)
                putExtra("cantidad", cantidad)
            }
            sendBroadcast(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Error agregando stock directo: ${e.message}")
        }
    }
}

```

### `app/src/main/java/com/artesanias/app/util/Utils.kt`

```kotlin
package com.artesanias.app.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import com.artesanias.app.data.model.RolUsuario
import com.artesanias.app.data.model.Usuario
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.security.MessageDigest

// ───────────── HASH UTIL ─────────────
/** Convierte una contraseña en su hash SHA-256 (en hexadecimal) para nunca guardarla en texto plano. */
object HashUtil {
    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        // "%02x" formatea cada byte como 2 dígitos hexadecimales (00-ff);
        // unidos, dan la representación de texto habitual de un hash.
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

// ───────────── SESSION MANAGER ─────────────
/**
 * Guarda la sesión activa (quién inició sesión y con qué rol) en
 * SharedPreferences, el almacén clave-valor persistente de Android — a
 * diferencia de Room, no es para datos relacionales, sino para unos pocos
 * valores simples que sobreviven a que se cierre la app. `MODE_PRIVATE`
 * significa que solo esta app puede leer este archivo de preferencias.
 */
class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("artesanias_session", Context.MODE_PRIVATE)

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_ROL = "user_rol"
        const val KEY_LOGGED_IN = "logged_in"
        const val NO_SESSION = -1
    }

    // Todas son propiedades calculadas (`get()`): leen SharedPreferences
    // en cada acceso, así que siempre reflejan el estado más reciente (no
    // hay que "refrescar" nada manualmente tras guardarSesion/cerrarSesion).
    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_LOGGED_IN, false)
    val userId: Int get() = prefs.getInt(KEY_USER_ID, NO_SESSION)
    val userName: String get() = prefs.getString(KEY_USER_NAME, "") ?: ""
    val userEmail: String get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    val userRol: RolUsuario get() {
        val rolStr = prefs.getString(KEY_USER_ROL, RolUsuario.CLIENTE.name) ?: RolUsuario.CLIENTE.name
        return RolUsuario.valueOf(rolStr)
    }
    val isAdmin: Boolean get() = userRol == RolUsuario.ADMIN

    fun guardarSesion(usuario: Usuario) {
        prefs.edit().apply {
            putBoolean(KEY_LOGGED_IN, true)
            putInt(KEY_USER_ID, usuario.id)
            putString(KEY_USER_NAME, "${usuario.nombre} ${usuario.apellido}")
            putString(KEY_USER_EMAIL, usuario.email)
            putString(KEY_USER_ROL, usuario.rol.name)
            apply()
        }
    }

    /** Borra toda la sesión guardada (equivalente a "cerrar sesión"). */
    fun cerrarSesion() {
        prefs.edit().clear().apply()
    }
}

// ───────────── QR UTIL ─────────────
/** Generación y lectura de códigos QR (con la librería ZXing) para emparejar el reloj Wear OS. */
object QRUtil {
    // Genera QR con el nodeId del teléfono para conectar Wear OS
    fun generarQRParaWear(nodeId: String, tamano: Int = 512): Bitmap {
        val contenido = "artesanias://wear/connect?nodeId=$nodeId"
        return generarQR(contenido, tamano)
    }

    /**
     * Codifica cualquier texto como una imagen de código QR. `BitMatrix`
     * es la cuadrícula de bits (true = módulo negro, false = blanco) que
     * ZXing genera a partir del texto; aquí se recorre pixel por pixel
     * para convertirla en un `Bitmap` real que se pueda mostrar en un
     * ImageView.
     */
    fun generarQR(contenido: String, tamano: Int = 512): Bitmap {
        val writer = MultiFormatWriter()
        val bitMatrix: BitMatrix = writer.encode(contenido, BarcodeFormat.QR_CODE, tamano, tamano)
        val bmp = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.RGB_565)
        for (x in 0 until tamano) {
            for (y in 0 until tamano) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bmp
    }

    // Parsear QR escaneado
    fun parsearQRWear(qrContent: String): String? {
        return if (qrContent.startsWith("artesanias://wear/connect?nodeId=")) {
            qrContent.removePrefix("artesanias://wear/connect?nodeId=")
        } else null
    }
}

// ───────────── EXTENSIONES ÚTILES ─────────────
// Funciones de extensión: le agregan un método a un tipo que ya existe
// (Double, String) sin tener que heredar de él ni modificar su código
// fuente. Se llaman igual que un método normal: `total.formatearPrecio()`.

/** Formatea un monto como precio en pesos mexicanos, p.ej. `1234.5` → `"$1,234.50 MXN"`. */
fun Double.formatearPrecio(): String = "\$${String.format("%,.2f", this)} MXN"

/** Valida el formato de un correo usando el patrón estándar de Android (`Patterns.EMAIL_ADDRESS`). */
fun String.isEmailValido(): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

/** Regla mínima de seguridad de contraseña para este proyecto: al menos 6 caracteres. */
fun String.isPasswordSeguro(): Boolean = length >= 6

```

### `app/src/main/java/com/artesanias/app/ui/MainActivity.kt`

```kotlin
package com.artesanias.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.artesanias.app.R
import com.artesanias.app.databinding.ActivityMainBinding
import com.artesanias.app.util.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity de la app (arquitectura "single-Activity"): todas las
 * pantallas son Fragments manejados por el Navigation Component sobre un
 * único `nav_graph`, y esta clase solo se encarga de la navegación de más
 * alto nivel (mostrar/ocultar la barra inferior, cambiar su menú según el
 * rol de la sesión activa).
 *
 * `@AndroidEntryPoint`: habilita la inyección de dependencias de Hilt en
 * esta Activity (permite usar `by viewModels()` / `by activityViewModels()`
 * más abajo en los Fragments, que a su vez inyectan repositorios).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Determinar destino inicial: si ya hay una sesión guardada (el
        // usuario no cerró sesión la última vez), se salta la pantalla de
        // login y entra directo a la pantalla que le corresponde según su
        // rol; si no, arranca en el login.
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            if (session.isLoggedIn) {
                if (session.isAdmin) R.id.adminDashboardFragment else R.id.tiendaFragment
            } else {
                R.id.loginFragment
            }
        )
        navController.graph = graph

        setupNavigation()
    }

    /**
     * Conecta el NavController con la barra de navegación inferior y con
     * la ActionBar, y decide cuándo mostrar/ocultar la barra inferior y
     * con qué menú (de administrador o de cliente).
     */
    private fun setupNavigation() {
        val bottomNav = binding.bottomNavView

        // Se ejecuta en cada cambio de pantalla (NavController lo llama
        // automáticamente): oculta la barra inferior en las pantallas de
        // autenticación, donde no aplica ningún menú todavía.
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.loginFragment, R.id.registroFragment -> {
                    bottomNav.visibility = android.view.View.GONE
                    supportActionBar?.hide()
                }
                else -> {
                    bottomNav.visibility = android.view.View.VISIBLE
                    supportActionBar?.show()
                    // Solo reconstruir el menú cuando cambia el rol (no en cada
                    // navegación): limpiar/reinflar el menú en cada destino
                    // desincroniza el ítem seleccionado de BottomNavigationView
                    // del NavController. Se compara contra el menú realmente
                    // inflado usando un ítem ancla fijo por rol (no un flag
                    // aparte, que se podía desincronizar al cerrar sesión y
                    // volver a entrar con otro rol dentro de la misma Activity,
                    // dejando visible el menú del rol anterior) y no un
                    // destino cualquiera (que fallaría en pantallas que no
                    // son pestañas del menú, como editar producto).
                    val anclaRol = if (session.isAdmin) R.id.adminDashboardFragment else R.id.tiendaFragment
                    if (bottomNav.menu.findItem(anclaRol) == null) {
                        updateMenuForRole(bottomNav)
                    }
                }
            }
        }

        // Destinos "de primer nivel": NavigationUI los trata como raíces
        // (no muestran flecha de "atrás" en la ActionBar, y el botón atrás
        // del sistema en ellos sale de la app en vez de navegar hacia atrás).
        val topLevelAdmin = setOf(
            R.id.adminDashboardFragment, R.id.adminProductosFragment,
            R.id.adminUsuariosFragment, R.id.camaraFragment
        )
        val topLevelCliente = setOf(
            R.id.tiendaFragment, R.id.carritoFragment, R.id.misOrdenesFragment, R.id.talleresFragment
        )

        val appBarConfig = AppBarConfiguration(topLevelAdmin + topLevelCliente)
        setupActionBarWithNavController(navController, appBarConfig)
        // Conecta cada ítem del menú de la barra inferior con el destino
        // del mismo id en el nav_graph: tocar un ítem navega solo, sin
        // necesidad de un OnItemSelectedListener escrito a mano.
        bottomNav.setupWithNavController(navController)
    }

    /** Reemplaza el menú de la barra inferior por el que corresponde al rol de la sesión activa. */
    private fun updateMenuForRole(nav: BottomNavigationView) {
        nav.menu.clear()
        if (session.isAdmin) {
            nav.inflateMenu(R.menu.menu_admin)
        } else {
            nav.inflateMenu(R.menu.menu_cliente)
        }
    }

    // Hace que la flecha de "atrás" de la ActionBar (en pantallas que no
    // son de primer nivel) navegue hacia atrás en el grafo.
    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}

```

### `app/src/main/java/com/artesanias/app/ui/ViewModels.kt`

```kotlin
package com.artesanias.app.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.*
import com.artesanias.app.data.local.CategoriaDao
import com.artesanias.app.data.model.*
import com.artesanias.app.data.repository.*
import com.artesanias.app.util.SessionManager
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────
// ViewModels: son el puente entre los repositorios (datos) y los
// Fragments (UI). Sobreviven a los cambios de configuración (rotar la
// pantalla) y, cuando se piden con `by activityViewModels()` en vez de
// `by viewModels()`, se comparten entre todos los Fragments de la misma
// Activity (útil para el carrito o la sesión, que deben verse igual sin
// importar en qué pantalla esté el usuario).
//
// `@HiltViewModel` + `@Inject constructor(...)`: Hilt construye el
// ViewModel resolviendo sus dependencias (repositorios) automáticamente,
// de la misma forma que con los repositorios en AppModule.kt.
//
// `ViewModel` vs `AndroidViewModel`: `AndroidViewModel` es igual pero
// además recibe el `Application` como Context seguro de usar (no se fuga
// memoria porque el Application vive tanto como el ViewModel), necesario
// aquí donde se arma un `SessionManager` o se registra un
// `BroadcastReceiver` directamente en el ViewModel.
// ─────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────
// AUTH VIEW MODEL
// ─────────────────────────────────────────────
@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)

    // Nullable a propósito: un LiveData normal reemite su último valor a
    // cualquier observador nuevo. Sin consumirResult, al volver a la
    // pantalla de login (p.ej. después de cerrar sesión) se disparaba otra
    // vez la navegación del inicio de sesión anterior, chocando con la
    // navegación del logout y tumbando la app.
    private val _loginResult = MutableLiveData<Result<Usuario>?>(null)
    val loginResult: LiveData<Result<Usuario>?> = _loginResult
    fun consumirLoginResult() { _loginResult.value = null }

    private val _registerResult = MutableLiveData<Result<Long>?>(null)
    val registerResult: LiveData<Result<Long>?> = _registerResult
    fun consumirRegisterResult() { _registerResult.value = null }

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn
    val currentUser get() = sessionManager

    /** Intenta iniciar sesión; el resultado (éxito o error) llega por `loginResult`. */
    fun login(email: String, password: String) {
        // viewModelScope: alcance de corrutinas atado al ciclo de vida del
        // ViewModel — se cancela solo cuando el ViewModel se destruye, así
        // que la corrutina puede seguir corriendo aunque el Fragment que la
        // disparó ya no esté en pantalla (p.ej. tras rotar la pantalla).
        viewModelScope.launch {
            _loading.value = true
            try {
                val usuario = authRepository.login(email, password)
                if (usuario != null) {
                    sessionManager.guardarSesion(usuario)
                    _loginResult.value = Result.success(usuario)
                } else {
                    _loginResult.value = Result.failure(Exception("Credenciales incorrectas"))
                }
            } catch (e: Exception) {
                _loginResult.value = Result.failure(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun registrar(nombre: String, apellido: String, email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            _registerResult.value = authRepository.registrar(nombre, apellido, email, password)
            _loading.value = false
        }
    }

    fun cerrarSesion() {
        sessionManager.cerrarSesion()
        _loginResult.value = null
    }
}

// ─────────────────────────────────────────────
// ADMIN PRODUCTOS VIEW MODEL
// ─────────────────────────────────────────────
@HiltViewModel
class AdminProductosViewModel @Inject constructor(
    application: Application,
    private val productoRepository: ProductoRepository,
    private val categoriaRepository: CategoriaDao
) : AndroidViewModel(application) {

    private val TAG = "AdminProductosVM"
    private val context = application.applicationContext

    // .asLiveData(): convierte el Flow reactivo del repositorio (que viene
    // de Room) en LiveData, el tipo que observan los Fragments con
    // `.observe(viewLifecycleOwner) { ... }`.
    val productos: LiveData<List<Producto>> =
        productoRepository.getProductosAdmin().asLiveData()

    val productosStockBajo: LiveData<List<Producto>> =
        productoRepository.getProductosStockBajo().asLiveData()

    private val _operacionResult = MutableLiveData<Result<Unit>>()
    val operacionResult: LiveData<Result<Unit>> = _operacionResult

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    // ── Receiver para comandos del Wear OS ──
    // Escucha los broadcasts locales que WearableDataListenerService
    // dispara cuando llega un mensaje del reloj, para reaccionar aunque el
    // panel de admin esté abierto en ese momento (agregar stock al vuelo,
    // responder la solicitud de inventario).
    private val wearReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {

                "com.artesanias.app.AGREGAR_STOCK" -> {
                    val productoId = intent.getIntExtra("productoId", -1)
                    val cantidad = intent.getIntExtra("cantidad", 0)
                    if (productoId != -1 && cantidad > 0) {
                        Log.d(TAG, "Broadcast AGREGAR_STOCK: producto=$productoId, cantidad=$cantidad")
                        agregarStock(productoId, cantidad)
                    }
                }

                "com.artesanias.app.SOLICITAR_STOCK_LISTA" -> {
                    val nodeId = intent.getStringExtra("nodeId") ?: return
                    Log.d(TAG, "Reloj solicita lista de stock bajo, nodeId=$nodeId")
                    enviarListaStockAlReloj(nodeId)
                }
            }
        }
    }

    init {
        // Registrar receiver para escuchar comandos del Wear.
        // RECEIVER_NOT_EXPORTED: el broadcast es interno de esta app (lo
        // dispara otro componente del mismo paquete), así que no hace
        // falta ni es seguro exponerlo a otras apps del dispositivo.
        val filter = IntentFilter().apply {
            addAction("com.artesanias.app.AGREGAR_STOCK")
            addAction("com.artesanias.app.SOLICITAR_STOCK_LISTA")
        }
        context.registerReceiver(wearReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    // Se llama cuando el ViewModel se destruye de verdad (no en cada
    // recreación de pantalla): hay que des-registrar el receiver aquí para
    // no dejarlo "colgado" escuchando broadcasts para siempre.
    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(wearReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    /** Inserta un producto nuevo (id == 0) o actualiza uno existente, según corresponda. */
    fun guardarProducto(producto: Producto) {
        viewModelScope.launch {
            _loading.value = true
            try {
                if (producto.id == 0) {
                    productoRepository.insertarProducto(producto)
                } else {
                    productoRepository.actualizarProducto(producto)
                }
                _operacionResult.value = Result.success(Unit)
            } catch (e: Exception) {
                _operacionResult.value = Result.failure(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun agregarStock(productoId: Int, cantidad: Int) {
        viewModelScope.launch {
            try {
                productoRepository.agregarStock(productoId, cantidad)
                _operacionResult.value = Result.success(Unit)
            } catch (e: Exception) {
                _operacionResult.value = Result.failure(e)
            }
        }
    }

    // Enviar lista de productos con stock bajo al reloj que la pidió
    private fun enviarListaStockAlReloj(nodeId: String) {
        viewModelScope.launch {
            try {
                // .first(): toma solo la emisión más reciente del Flow y
                // sigue, sin quedarse suscrito (aquí no se necesita seguir
                // escuchando cambios, solo responder una vez a la solicitud).
                val productosConStockBajo = productoRepository
                    .getProductosStockBajo()
                    .first()

                // Formato: "id:nombre:stock|id:nombre:stock|..."
                val payload = if (productosConStockBajo.isEmpty()) {
                    ""
                } else {
                    productosConStockBajo.joinToString("|") { p ->
                        "${p.id}:${p.nombre}:${p.stock}"
                    }
                }

                Wearable.getMessageClient(context)
                    .sendMessage(nodeId, "/stock/lista/respuesta", payload.toByteArray())
                    .await()

                Log.d(TAG, "Lista de stock enviada al reloj: ${productosConStockBajo.size} productos")
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando lista de stock al reloj: ${e.message}")
            }
        }
    }

    fun desactivar(productoId: Int) {
        viewModelScope.launch {
            // Implementar si se necesita
        }
    }
}

// ─────────────────────────────────────────────
// ADMIN USUARIOS VIEW MODEL
// ─────────────────────────────────────────────
@HiltViewModel
class AdminUsuariosViewModel @Inject constructor(
    private val usuarioRepository: UsuarioRepository
) : ViewModel() {

    val usuarios: LiveData<List<Usuario>> = usuarioRepository.getUsuarios().asLiveData()

    private val _operacionResult = MutableLiveData<Result<Unit>>()
    val operacionResult: LiveData<Result<Unit>> = _operacionResult

    fun agregarUsuario(
        nombre: String, apellido: String,
        email: String, password: String, rol: RolUsuario
    ) {
        viewModelScope.launch {
            val result = usuarioRepository.insertar(nombre, apellido, email, password, rol)
            // .map { }: convierte un Result<Long> (el id generado, que no
            // interesa aquí) en un Result<Unit>, conservando si fue éxito o
            // error, para que operacionResult tenga un solo tipo genérico
            // sin importar qué operación de administración lo haya producido.
            _operacionResult.value = result.map { }
        }
    }

    /** Invierte el estado activo/inactivo de una cuenta (bloquear o desbloquear el acceso). */
    fun toggleActivo(usuario: Usuario) {
        viewModelScope.launch {
            usuarioRepository.setActivo(usuario.id, !usuario.activo)
        }
    }
}

// ─────────────────────────────────────────────
// TIENDA VIEW MODEL (cliente)
// ─────────────────────────────────────────────
// Compartido por todos los Fragments del cliente (Tienda, Carrito, Mis
// Órdenes) vía `by activityViewModels()`, para que el carrito armado en
// la pantalla de Tienda siga existiendo al entrar a Carrito.
@HiltViewModel
class TiendaViewModel @Inject constructor(
    application: Application,
    private val productoRepository: ProductoRepository,
    private val ordenRepository: OrdenRepository
) : AndroidViewModel(application) {

    private val session = SessionManager(application)

    // StateFlow para el texto de búsqueda: siempre tiene un valor actual
    // (empieza en ""), a diferencia de un Flow normal que solo emite hacia
    // adelante. flatMapLatest lo usa para cambiar de query de búsqueda
    // sobre la marcha, cancelando automáticamente la búsqueda anterior si
    // el usuario sigue escribiendo antes de que termine.
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val productos: LiveData<List<Producto>> = _query.flatMapLatest { q ->
        if (q.isBlank()) productoRepository.getProductos()
        else productoRepository.buscar(q)
    }.asLiveData()

    // El carrito vive solo en memoria (no en Room) mientras se arma el
    // pedido; ver ItemCarrito en Models.kt.
    private val _carrito = MutableLiveData<MutableList<ItemCarrito>>(mutableListOf())
    val carrito: LiveData<MutableList<ItemCarrito>> = _carrito

    val totalCarrito: LiveData<Double> = carrito.map { items ->
        items.sumOf { it.subtotal }
    }

    val cantidadCarrito: LiveData<Int> = carrito.map { items ->
        items.sumOf { it.cantidad }
    }

    val misOrdenes: LiveData<List<Orden>> =
        ordenRepository.getMisOrdenes(session.userId).asLiveData()

    // Nullable a propósito: LiveData reemite el último valor a cualquier
    // observador nuevo (p.ej. al volver a entrar al Carrito), así que sin
    // "consumirOrdenResult" el diálogo de compra exitosa y la navegación a
    // Mis Órdenes se repetían solas cada vez que se reabría la pantalla.
    private val _ordenResult = MutableLiveData<Result<Orden>?>(null)
    val ordenResult: LiveData<Result<Orden>?> = _ordenResult

    fun consumirOrdenResult() { _ordenResult.value = null }

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun buscar(q: String) { _query.value = q }

    /** Agrega un producto al carrito, sumando la cantidad si ya estaba (no duplica la línea). */
    fun agregarAlCarrito(producto: Producto, cantidad: Int = 1) {
        val lista = _carrito.value ?: mutableListOf()
        val existing = lista.find { it.producto.id == producto.id }
        if (existing != null) {
            existing.cantidad += cantidad
        } else {
            lista.add(ItemCarrito(producto, cantidad))
        }
        // Reasignar `_carrito.value` (aunque sea la misma lista mutada) es
        // necesario para que LiveData notifique a los observadores: solo
        // muta el contenido de la lista no dispara la notificación por sí solo.
        _carrito.value = lista
    }

    fun quitarDelCarrito(productoId: Int) {
        val lista = _carrito.value ?: return
        lista.removeAll { it.producto.id == productoId }
        _carrito.value = lista
    }

    fun cambiarCantidad(productoId: Int, nueva: Int) {
        val lista = _carrito.value ?: return
        if (nueva <= 0) { quitarDelCarrito(productoId); return }
        lista.find { it.producto.id == productoId }?.cantidad = nueva
        _carrito.value = lista
    }

    fun limpiarCarrito() { _carrito.value = mutableListOf() }

    /** Confirma la compra del carrito actual; el resultado llega por `ordenResult`. */
    fun realizarCompra() {
        val items = _carrito.value?.toList() ?: return
        if (items.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            val result = ordenRepository.crearOrden(session.userId, items)
            _ordenResult.value = result
            if (result.isSuccess) limpiarCarrito()
            _loading.value = false
        }
    }
}

```

### `app/src/main/java/com/artesanias/app/ui/auth/AuthFragments.kt`

```kotlin
package com.artesanias.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.artesanias.app.R
import com.artesanias.app.data.model.RolUsuario
import com.artesanias.app.databinding.FragmentLoginBinding
import com.artesanias.app.databinding.FragmentRegistroBinding
import com.artesanias.app.ui.AuthViewModel
import com.artesanias.app.util.isEmailValido
import com.artesanias.app.util.isPasswordSeguro
import dagger.hilt.android.AndroidEntryPoint

// ─────────────────────────────────────────────
// LOGIN FRAGMENT
// ─────────────────────────────────────────────
/** Pantalla de inicio de sesión: valida el formulario y, según el rol del usuario autenticado, navega al panel de admin o a la tienda. */
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentLoginBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.btnLogin.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            // El guard `result == null` es necesario porque loginResult es
            // nullable a propósito (ver AuthViewModel): al llegar aquí por
            // primera vez su valor inicial es null y no hay nada que hacer.
            if (result == null) return@observe
            result.onSuccess { usuario ->
                val dest = if (usuario.rol == RolUsuario.ADMIN)
                    R.id.adminDashboardFragment else R.id.tiendaFragment
                // popUpTo(loginFragment, inclusive=true): quita esta
                // pantalla de la pila al entrar, para que el botón "atrás"
                // desde Tienda/AdminDashboard no regrese al login.
                findNavController().navigate(dest, null, navOptions {
                    popUpTo(R.id.loginFragment) { inclusive = true }
                })
            }.onFailure { e ->
                Toast.makeText(requireContext(), e.message ?: "Error al iniciar sesión", Toast.LENGTH_LONG).show()
            }
            viewModel.consumirLoginResult()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val pass  = binding.etPassword.text.toString()

            when {
                email.isBlank() -> binding.tilEmail.error = "Ingresa tu correo"
                !email.isEmailValido() -> binding.tilEmail.error = "Correo inválido"
                pass.isBlank() -> binding.tilPassword.error = "Ingresa tu contraseña"
                else -> {
                    binding.tilEmail.error = null
                    binding.tilPassword.error = null
                    viewModel.login(email, pass)
                }
            }
        }

        binding.btnIrRegistro.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_registro)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// REGISTRO FRAGMENT
// ─────────────────────────────────────────────
/** Formulario de alta de cuenta nueva (siempre como CLIENTE; los admins solo se crean desde el panel de administración). */
@AndroidEntryPoint
class RegistroFragment : Fragment() {

    private var _binding: FragmentRegistroBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentRegistroBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.btnRegistrar.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.registerResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            result.onSuccess {
                Toast.makeText(requireContext(), "¡Cuenta creada! Inicia sesión", Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }.onFailure { e ->
                Toast.makeText(requireContext(), e.message ?: "Error al registrar", Toast.LENGTH_LONG).show()
            }
            viewModel.consumirRegisterResult()
        }

        binding.btnRegistrar.setOnClickListener {
            val nombre   = binding.etNombre.text.toString().trim()
            val apellido = binding.etApellido.text.toString().trim()
            val email    = binding.etEmail.text.toString().trim()
            val pass     = binding.etPassword.text.toString()
            val confirm  = binding.etConfirmPassword.text.toString()

            var valid = true
            if (nombre.isBlank()) { binding.tilNombre.error = "Campo obligatorio"; valid = false }
            else binding.tilNombre.error = null

            if (apellido.isBlank()) { binding.tilApellido.error = "Campo obligatorio"; valid = false }
            else binding.tilApellido.error = null

            if (!email.isEmailValido()) { binding.tilEmail.error = "Correo inválido"; valid = false }
            else binding.tilEmail.error = null

            if (!pass.isPasswordSeguro()) { binding.tilPassword.error = "Mínimo 6 caracteres"; valid = false }
            else binding.tilPassword.error = null

            if (pass != confirm) { binding.tilConfirmPassword.error = "Las contraseñas no coinciden"; valid = false }
            else binding.tilConfirmPassword.error = null

            if (valid) viewModel.registrar(nombre, apellido, email, pass)
        }

        binding.btnVolver.setOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

```

### `app/src/main/java/com/artesanias/app/ui/admin/AdminFragments.kt`

```kotlin
package com.artesanias.app.ui.admin

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import com.artesanias.app.R
import com.artesanias.app.data.model.*
import com.artesanias.app.databinding.*
import com.artesanias.app.ui.AdminProductosViewModel
import com.artesanias.app.ui.AdminUsuariosViewModel
import com.artesanias.app.ui.AuthViewModel
import com.artesanias.app.ui.shared.ProductoAdapter
import com.artesanias.app.ui.shared.ProductoAdminAdapter
import com.artesanias.app.ui.shared.UsuarioAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint

// ─────────────────────────────────────────────────────────────────────────
// Pantallas del panel de administrador. Todas siguen el mismo patrón:
//   @AndroidEntryPoint   habilita que Hilt inyecte dependencias en este
//       Fragment (necesario para que `by viewModels()` pueda resolver el
//       ViewModel y sus propias dependencias inyectadas).
//   View Binding (`_binding` / `binding`)  en vez de `findViewById`, Android
//       genera una clase `FragmentXxxBinding` con una propiedad por cada
//       vista del layout XML, con seguridad de tipos en tiempo de
//       compilación. `_binding` es nullable y se limpia en onDestroyView
//       porque la vista del Fragment se destruye antes que el Fragment
//       mismo (para no dejar una referencia fuga a una vista muerta); el
//       getter `binding` (sin guion bajo) es el que se usa en el resto del
//       código y solo es válido mientras la vista exista.
//   by viewModels() / by activityViewModels()  piden el ViewModel a Hilt.
//       `viewModels()` crea uno propio de este Fragment; `activityViewModels()`
//       reutiliza el mismo que cualquier otro Fragment de la misma Activity
//       que lo pida (necesario para AuthViewModel, que debe reflejar el
//       mismo estado de sesión en todas las pantallas).
// ─────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────
// ADMIN DASHBOARD
// ─────────────────────────────────────────────
@AndroidEntryPoint
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val productoViewModel: AdminProductosViewModel by viewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAdminDashboardBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        productoViewModel.productos.observe(viewLifecycleOwner) { lista ->
            binding.tvTotalProductos.text = lista.size.toString()
            val activos = lista.count { it.activo }
            binding.tvProductosActivos.text = "$activos activos"
        }

        productoViewModel.productosStockBajo.observe(viewLifecycleOwner) { lista ->
            binding.tvStockBajo.text = lista.size.toString()
            binding.cardStockBajo.visibility =
                if (lista.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // Navegación rápida
        binding.cardProductos.setOnClickListener {
            findNavController().navigate(R.id.adminProductosFragment)
        }
        binding.cardUsuarios.setOnClickListener {
            findNavController().navigate(R.id.adminUsuariosFragment)
        }
        binding.cardCamara.setOnClickListener {
            findNavController().navigate(R.id.camaraFragment)
        }
        binding.btnCerrarSesion.setOnClickListener {
            authViewModel.cerrarSesion()
            // popUpTo el grafo completo: al cerrar sesión no debe quedar
            // nada del panel de admin en la pila de retroceso.
            findNavController().navigate(R.id.loginFragment, null, navOptions {
                popUpTo(findNavController().graph.id) { inclusive = true }
            })
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// ADMIN PRODUCTOS
// ─────────────────────────────────────────────
/** Lista de inventario con buscador, edición y botón para agregar stock por producto. */
@AndroidEntryPoint
class AdminProductosFragment : Fragment() {

    private var _binding: FragmentAdminProductosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminProductosViewModel by viewModels()
    private lateinit var adapter: ProductoAdminAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAdminProductosBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProductoAdminAdapter(
            onEditar = { producto ->
                val bundle = Bundle().apply { putParcelable("producto", producto) }
                findNavController().navigate(R.id.action_adminProductos_to_editarProducto, bundle)
            },
            onAgregarStock = { producto -> mostrarDialogoStock(producto) }
        )

        binding.rvProductos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProductos.adapter = adapter

        viewModel.productos.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.fabNuevoProducto.setOnClickListener {
            findNavController().navigate(R.id.camaraFragment)
        }

        binding.etBuscar.addTextChangedListener { text ->
            adapter.filtrar(text.toString())
        }

        viewModel.operacionResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "✓ Operación exitosa", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Diálogo simple para capturar cuántas unidades reabastecer de un producto. */
    private fun mostrarDialogoStock(producto: Producto) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_agregar_stock, null)
        val etCantidad = dialogView.findViewById<TextInputEditText>(R.id.et_cantidad)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Agregar stock\n${producto.nombre}")
            .setMessage("Stock actual: ${producto.stock}")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val cantidad = etCantidad.text.toString().toIntOrNull() ?: 0
                if (cantidad > 0) {
                    viewModel.agregarStock(producto.id, cantidad)
                } else {
                    Toast.makeText(requireContext(), "Ingresa una cantidad válida", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// ADMIN USUARIOS
// ─────────────────────────────────────────────
/** Lista de cuentas registradas, con acción para activar/desactivar y un diálogo para dar de alta nuevas. */
@AndroidEntryPoint
class AdminUsuariosFragment : Fragment() {

    private var _binding: FragmentAdminUsuariosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminUsuariosViewModel by viewModels()
    private lateinit var adapter: UsuarioAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAdminUsuariosBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = UsuarioAdapter { usuario -> viewModel.toggleActivo(usuario) }

        binding.rvUsuarios.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsuarios.adapter = adapter

        viewModel.usuarios.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.fabNuevoUsuario.setOnClickListener { mostrarDialogoNuevoUsuario() }

        viewModel.operacionResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "✓ Usuario creado", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarDialogoNuevoUsuario() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_nuevo_usuario, null)
        val etNombre   = dialogView.findViewById<TextInputEditText>(R.id.et_nombre)
        val etApellido = dialogView.findViewById<TextInputEditText>(R.id.et_apellido)
        val etEmail    = dialogView.findViewById<TextInputEditText>(R.id.et_email)
        val etPass     = dialogView.findViewById<TextInputEditText>(R.id.et_password)
        val switchAdmin = dialogView.findViewById<android.widget.Switch>(R.id.switch_admin)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nuevo usuario")
            .setView(dialogView)
            .setPositiveButton("Crear") { _, _ ->
                val nombre   = etNombre.text.toString().trim()
                val apellido = etApellido.text.toString().trim()
                val email    = etEmail.text.toString().trim()
                val pass     = etPass.text.toString()
                val rol      = if (switchAdmin.isChecked) RolUsuario.ADMIN else RolUsuario.CLIENTE

                if (nombre.isBlank() || email.isBlank() || pass.isBlank()) {
                    Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.agregarUsuario(nombre, apellido, email, pass, rol)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// NUEVO/EDITAR PRODUCTO FRAGMENT
// ─────────────────────────────────────────────
/**
 * Formulario único para dar de alta un producto o editar uno existente:
 * `productoExistente` (recibido por argumento de navegación) decide cuál
 * de los dos modos usar y precarga los campos si aplica.
 */
@AndroidEntryPoint
class EditarProductoFragment : Fragment() {

    private var _binding: FragmentEditarProductoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminProductosViewModel by viewModels()

    private var productoExistente: Producto? = null
    private var imagenPath: String = ""

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentEditarProductoBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recibir producto (edición) o imagenPath (nuevo desde cámara)
        productoExistente = arguments?.getParcelable("producto")
        imagenPath = arguments?.getString("imagenPath") ?: ""

        productoExistente?.let { p ->
            binding.etNombre.setText(p.nombre)
            binding.etDescripcion.setText(p.descripcion)
            binding.etPrecio.setText(p.precio.toString())
            binding.etStock.setText(p.stock.toString())
            binding.etTecnica.setText(p.tecnica)
            binding.etOrigen.setText(p.origen)
            binding.etArtesano.setText(p.artesano)
            binding.tvTitulo.text = "Editar producto"
        } ?: run {
            binding.tvTitulo.text = "Nuevo producto"
            if (imagenPath.isNotBlank()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(imagenPath)
                binding.ivProducto.setImageBitmap(bmp)
            }
        }

        viewModel.operacionResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "✓ Producto guardado", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.btnGuardar.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        binding.btnGuardar.setOnClickListener { guardar() }
    }

    /** Valida el formulario y arma el `Producto` (nuevo o copia editada) antes de guardarlo. */
    private fun guardar() {
        val nombre = binding.etNombre.text.toString().trim()
        val precio = binding.etPrecio.text.toString().toDoubleOrNull()
        val stock  = binding.etStock.text.toString().toIntOrNull()

        if (nombre.isBlank()) { binding.tilNombre.error = "Obligatorio"; return }
        if (precio == null || precio <= 0) { binding.tilPrecio.error = "Precio inválido"; return }
        if (stock == null || stock < 0) { binding.tilStock.error = "Stock inválido"; return }

        val producto = (productoExistente ?: Producto(
            nombre = "", descripcion = "", precio = 0.0, stock = 0
        )).copy(
            nombre = nombre,
            descripcion = binding.etDescripcion.text.toString().trim(),
            precio = precio,
            stock = stock,
            tecnica = binding.etTecnica.text.toString().trim(),
            origen = binding.etOrigen.text.toString().trim(),
            artesano = binding.etArtesano.text.toString().trim(),
            imagenPath = if (imagenPath.isNotBlank()) imagenPath else (productoExistente?.imagenPath ?: "")
        )

        viewModel.guardarProducto(producto)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

```

### `app/src/main/java/com/artesanias/app/ui/store/StoreFragments.kt`

```kotlin
package com.artesanias.app.ui.store

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.artesanias.app.R
import com.artesanias.app.data.model.*
import com.artesanias.app.databinding.*
import com.artesanias.app.ui.AuthViewModel
import com.artesanias.app.ui.TiendaViewModel
import com.artesanias.app.ui.shared.CarritoAdapter
import com.artesanias.app.ui.shared.ProductoAdapter
import com.artesanias.app.util.formatearPrecio
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

// ─────────────────────────────────────────────
// TIENDA FRAGMENT
// ─────────────────────────────────────────────
/** Catálogo de productos del cliente: grilla con buscador, agregar al carrito y ver detalle. */
@AndroidEntryPoint
class TiendaFragment : Fragment() {

    private var _binding: FragmentTiendaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TiendaViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private lateinit var adapter: ProductoAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentTiendaBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProductoAdapter(
            onAgregar = { producto -> agregarAlCarrito(producto) },
            onClick   = { producto -> mostrarDetalleProducto(producto) }
        )

        binding.rvProductos.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProductos.adapter = adapter

        viewModel.productos.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }

        // Badge en el carrito
        viewModel.cantidadCarrito.observe(viewLifecycleOwner) { cantidad ->
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_nav_view)
            val badge: BadgeDrawable? = bottomNav?.getOrCreateBadge(R.id.carritoFragment)
            if (cantidad > 0) {
                badge?.isVisible = true
                badge?.number = cantidad
            } else {
                badge?.isVisible = false
            }
        }

        // Búsqueda
        binding.etBuscar.addTextChangedListener { text -> viewModel.buscar(text.toString()) }

        binding.btnCerrarSesion.setOnClickListener {
            authViewModel.cerrarSesion()
            // popUpTo el grafo completo: al cerrar sesión no debe quedar nada
            // de la sesión anterior en la pila de retroceso.
            findNavController().navigate(R.id.loginFragment, null, navOptions {
                popUpTo(findNavController().graph.id) { inclusive = true }
            })
        }

        // Swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.buscar("")
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun agregarAlCarrito(producto: Producto) {
        viewModel.agregarAlCarrito(producto)
        Toast.makeText(requireContext(), "✓ ${producto.nombre} en el carrito", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarDetalleProducto(producto: Producto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(producto.nombre)
            .setMessage(buildString {
                appendLine(producto.descripcion)
                appendLine()
                appendLine("💰 Precio: ${producto.precio.formatearPrecio()}")
                appendLine("🏺 Técnica: ${producto.tecnica}")
                appendLine("📍 Origen: ${producto.origen}")
                appendLine("👨‍🎨 Artesano: ${producto.artesano}")
                appendLine("📦 Stock: ${producto.stock} disponibles")
            })
            .setPositiveButton("Agregar al carrito") { _, _ -> agregarAlCarrito(producto) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// CARRITO FRAGMENT
// ─────────────────────────────────────────────
/** Resumen del carrito y flujo de compra: pide confirmación extra si el total supera $1,000 MXN. */
@AndroidEntryPoint
class CarritoFragment : Fragment() {

    private var _binding: FragmentCarritoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TiendaViewModel by activityViewModels()
    private lateinit var adapter: CarritoAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentCarritoBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CarritoAdapter(
            onMasCantidad  = { item -> viewModel.cambiarCantidad(item.producto.id, item.cantidad + 1) },
            onMenosCantidad = { item -> viewModel.cambiarCantidad(item.producto.id, item.cantidad - 1) },
            onEliminar = { item -> viewModel.quitarDelCarrito(item.producto.id) }
        )

        binding.rvCarrito.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCarrito.adapter = adapter

        viewModel.carrito.observe(viewLifecycleOwner) { items ->
            adapter.updateItems(items)
            binding.tvVacio.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.btnComprar.isEnabled = items.isNotEmpty()
        }

        viewModel.totalCarrito.observe(viewLifecycleOwner) { total ->
            binding.tvTotal.text = "Total: ${total.formatearPrecio()}"

            // Advertencia visual si requiere confirmación
            binding.tvAviso.visibility = if (total > 1000) View.VISIBLE else View.GONE
            binding.tvAviso.text = "⚠️ Compra mayor a $1,000 MXN — requiere confirmación"
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.btnComprar.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.ordenResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            result.onSuccess { orden ->
                val mensaje = when {
                    orden.requiereConfirmacion ->
                        "✅ Orden #${orden.id} creada.\n⚠️ Requiere confirmación (>${1000.0.formatearPrecio()})"
                    orden.esCompraGrande ->
                        "✅ Orden #${orden.id} creada.\n💰 Compra grande notificada al reloj"
                    else ->
                        "✅ ¡Compra realizada! Orden #${orden.id}"
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Compra exitosa")
                    .setMessage(mensaje)
                    .setPositiveButton("OK") { _, _ ->
                        // popUpTo el carrito: cada compra no debe apilar una
                        // pantalla nueva sobre la anterior, o la pila de
                        // navegación crece sin límite con cada compra hecha
                        // y el bottom nav termina sin responder.
                        findNavController().navigate(R.id.misOrdenesFragment, null, navOptions {
                            popUpTo(R.id.carritoFragment) { inclusive = true }
                            launchSingleTop = true
                        })
                    }
                    .show()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            viewModel.consumirOrdenResult()
        }

        binding.btnComprar.setOnClickListener {
            val total = viewModel.totalCarrito.value ?: 0.0
            if (total > 1000) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirmar compra grande")
                    .setMessage("Esta compra supera los \$1,000 MXN.\n¿Deseas proceder?\n\nSe notificará al administrador.")
                    .setPositiveButton("Sí, confirmar") { _, _ -> viewModel.realizarCompra() }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else {
                viewModel.realizarCompra()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// MIS ORDENES FRAGMENT (cliente)
// ─────────────────────────────────────────────
/** Historial de compras del cliente autenticado, ordenadas de más reciente a más antigua. */
@AndroidEntryPoint
class MisOrdenesFragment : Fragment() {

    private var _binding: FragmentMisOrdenesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TiendaViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentMisOrdenesBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = OrdenAdapter()
        binding.rvOrdenes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrdenes.adapter = adapter

        viewModel.misOrdenes.observe(viewLifecycleOwner) { ordenes ->
            adapter.submitList(ordenes)
            binding.tvVacio.visibility = if (ordenes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// Adaptador simple para órdenes
class OrdenAdapter : androidx.recyclerview.widget.ListAdapter<Orden, OrdenAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Orden>() {
        override fun areItemsTheSame(a: Orden, b: Orden) = a.id == b.id
        override fun areContentsTheSame(a: Orden, b: Orden) = a == b
    }
) {
    inner class VH(private val b: com.artesanias.app.databinding.ItemOrdenBinding)
        : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
        fun bind(o: Orden) {
            b.tvOrdenId.text = "Orden #${o.id}"
            b.tvTotal.text = o.total.formatearPrecio()
            b.tvEstado.text = o.estado.name.replace("_", " ")
            b.tvFecha.text = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", o.fecha).toString()
            b.chipConfirmacion.visibility = if (o.requiereConfirmacion && !o.confirmada) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(com.artesanias.app.databinding.ItemOrdenBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(getItem(pos))
}

```

### `app/src/main/java/com/artesanias/app/ui/store/TalleresFragment.kt`

```kotlin
package com.artesanias.app.ui.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.artesanias.app.R
import com.artesanias.app.data.model.Producto
import com.artesanias.app.ui.TiendaViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint

/**
 * Mapa de talleres artesanales: agrupa los productos por su campo `origen`
 * y pone un pin por región usando un catálogo fijo de coordenadas (los
 * productos de ejemplo no traen lat/lng, solo texto de ciudad/estado).
 */
@AndroidEntryPoint
class TalleresFragment : Fragment(), com.google.android.gms.maps.OnMapReadyCallback {

    private val viewModel: TiendaViewModel by activityViewModels()
    private var mapa: GoogleMap? = null

    companion object {
        // Coordenadas aproximadas de las regiones artesanales que aparecen
        // en los productos de ejemplo (data/local/ArtesaniasDatabase.kt).
        private val COORDENADAS = mapOf(
            "Puebla" to LatLng(19.0413, -98.2062),
            "San Bartolo Coyotepec, Oaxaca" to LatLng(16.9678, -96.6961),
            "Dolores Hidalgo, Gto" to LatLng(21.1561, -100.9330),
            "Michoacán" to LatLng(19.5138, -101.6157),
            "Guanajuato" to LatLng(21.0190, -101.2574),
            "Tlaquepaque, Jalisco" to LatLng(20.6409, -103.3121),
            "San Marcos Tlapazola, Oaxaca" to LatLng(16.9958, -96.4658)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_talleres, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // SupportMapFragment vive DENTRO de este Fragment (un Fragment
        // anidado dentro de otro), así que se busca con
        // `childFragmentManager` (el manejador de fragmentos hijos de
        // ESTE Fragment) y no con el de la Activity. `getMapAsync` carga
        // el mapa en segundo plano y avisa por `onMapReady` cuando ya se
        // puede dibujar sobre él.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /** Se llama una sola vez, cuando el SDK de Google Maps ya terminó de inicializar el mapa. */
    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        viewModel.productos.observe(viewLifecycleOwner) { productos ->
            dibujarPines(productos)
        }
    }

    private fun dibujarPines(productos: List<Producto>) {
        val mapa = mapa ?: return
        mapa.clear()

        val porOrigen = productos.filter { it.origen.isNotBlank() }.groupBy { it.origen }
        val bounds = LatLngBounds.builder()
        var huboPines = false

        porOrigen.forEach { (origen, items) ->
            val coordenada = COORDENADAS[origen] ?: return@forEach
            val artesanos = items.map { it.artesano }.distinct().joinToString(", ")
            mapa.addMarker(
                MarkerOptions()
                    .position(coordenada)
                    .title(origen)
                    .snippet("${items.size} producto(s) — $artesanos")
            )
            bounds.include(coordenada)
            huboPines = true
        }

        if (huboPines) {
            mapa.setOnMapLoadedCallback {
                mapa.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80))
            }
        }
    }
}

```

### `app/src/main/java/com/artesanias/app/ui/camera/CamaraFragment.kt`

```kotlin
package com.artesanias.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.artesanias.app.R
import com.artesanias.app.databinding.FragmentCamaraBinding
import com.artesanias.app.util.QRUtil
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Pantalla de cámara con dos modos, usando CameraX (la librería de Jetpack
 * que envuelve la Camera2 API de Android con un ciclo de vida más simple):
 * - Modo foto: captura la imagen de un producto nuevo y la pasa al
 *   formulario de alta (EditarProductoFragment) por la ruta del archivo.
 * - Modo QR: captura una imagen, la decodifica con ZXing (la librería de
 *   lectura de códigos QR/barras) y extrae el nodeId del reloj Wear OS
 *   para emparejarlo.
 */
@AndroidEntryPoint
class CamaraFragment : Fragment() {

    private var _binding: FragmentCamaraBinding? = null
    private val binding get() = _binding!!

    // Hilo dedicado para las operaciones de CameraX (captura, guardado):
    // no deben correr en el hilo principal para no congelar la UI.
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var modoQR = false   // false = foto producto, true = scan QR

    // Launcher de permisos de cámara: registra el flujo estándar de
    // Android para pedir un permiso en tiempo de ejecución y recibir la
    // respuesta del usuario (concedido o no) sin bloquear la UI.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) iniciarCamara()
        else Toast.makeText(requireContext(), "Permiso de cámara requerido", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentCamaraBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        actualizarUI()

        binding.btnCambiarModo.setOnClickListener {
            modoQR = !modoQR
            actualizarUI()
            iniciarCamara()
        }

        binding.btnCapturar.setOnClickListener {
            if (modoQR) escanearQR() else tomarFoto()
        }

        // Verificar y solicitar permiso
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun actualizarUI() {
        if (modoQR) {
            binding.tvModo.text = "Modo: Escanear QR (Wear OS)"
            binding.btnCambiarModo.text = "📷 Modo Producto"
            binding.btnCapturar.text = "Escanear QR"
            binding.overlayQr.visibility = View.VISIBLE
        } else {
            binding.tvModo.text = "Modo: Fotografiar Producto"
            binding.btnCambiarModo.text = "🔲 Modo QR"
            binding.btnCapturar.text = "Tomar Foto"
            binding.overlayQr.visibility = View.GONE
        }
    }

    /** Abre la cámara trasera y conecta su vista previa al `viewFinder` del layout. */
    private fun iniciarCamara() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CamaraFragment", "Error al iniciar cámara", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun tomarFoto() {
        val capture = imageCapture ?: return
        val photoFile = crearArchivoFoto()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = Uri.fromFile(photoFile)
                    Toast.makeText(requireContext(), "Foto guardada", Toast.LENGTH_SHORT).show()
                    // Navegar a formulario de nuevo producto con la ruta de la foto
                    val bundle = Bundle().apply {
                        putString("imagenPath", photoFile.absolutePath)
                    }
                    findNavController().navigate(R.id.action_camara_to_nuevoProducto, bundle)
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Error al capturar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun escanearQR() {
        val capture = imageCapture ?: return
        val photoFile = crearArchivoFoto()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    procesarQR(photoFile)
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Error al capturar", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    /**
     * Decodifica el QR de la foto recién tomada usando ZXing: convierte el
     * bitmap a una matriz de píxeles (`RGBLuminanceSource`), la binariza
     * (blanco/negro) y se la pasa al lector genérico de códigos
     * (`MultiFormatReader`), que reconoce el patrón del QR y devuelve el
     * texto que codifica.
     */
    private fun procesarQR(archivo: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(archivo.absolutePath)
            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            val qrContent = result.text

            val nodeId = QRUtil.parsearQRWear(qrContent)
            if (nodeId != null) {
                Toast.makeText(
                    requireContext(),
                    "✅ Reloj conectado: ${nodeId.take(8)}...",
                    Toast.LENGTH_LONG
                ).show()
                // Guardar nodeId para comunicación Wear
                // En producción: conectar vía Wearable.MessageClient
            } else {
                Toast.makeText(requireContext(), "QR no reconocido: $qrContent", Toast.LENGTH_LONG).show()
            }
        } catch (e: NotFoundException) {
            Toast.makeText(requireContext(), "No se encontró QR en la imagen", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error procesando QR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun crearArchivoFoto(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = requireContext().getExternalFilesDir("artesanias") ?: requireContext().filesDir
        return File(dir, "IMG_$timestamp.jpg")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}

```

### `app/src/main/java/com/artesanias/app/ui/shared/Adapters.kt`

```kotlin
package com.artesanias.app.ui.shared

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.artesanias.app.data.model.Producto
import com.artesanias.app.databinding.ItemProductoBinding
import com.artesanias.app.databinding.ItemProductoAdminBinding
import com.artesanias.app.util.formatearPrecio
import com.bumptech.glide.Glide
import java.io.File

// ─────────────────────────────────────────────────────────────────────────
// Adaptadores de RecyclerView: convierten una lista de datos en las filas
// visibles de una lista/grilla. Patrón usado en todos:
//   ViewHolder (clase "VH")  guarda las referencias a las vistas de una
//       fila ya infladas (por View Binding), para no tener que buscarlas
//       de nuevo cada vez que esa fila se reutiliza al hacer scroll.
//   onCreateViewHolder  infla el layout XML de una fila nueva.
//   onBindViewHolder  llena una fila (ya existente o reciclada) con los
//       datos del ítem que le toca mostrar.
//   ListAdapter + DiffUtil.ItemCallback  variante de RecyclerView.Adapter
//       que, al recibir una lista nueva con `submitList`, compara contra
//       la lista anterior en un hilo de fondo y solo re-dibuja las filas
//       que realmente cambiaron (más eficiente que `notifyDataSetChanged`,
//       que redibuja toda la lista sin importar qué cambió).
// ─────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────
// ADAPTADOR TIENDA (cliente)
// ─────────────────────────────────────────────
/** Grilla de productos de la tienda, con botón "Agregar" al carrito y tap para ver el detalle. */
class ProductoAdapter(
    private val onAgregar: (Producto) -> Unit,
    private val onClick: (Producto) -> Unit
) : ListAdapter<Producto, ProductoAdapter.VH>(DIFF) {

    private var listaCompleta: List<Producto> = emptyList()

    fun filtrar(query: String) {
        val filtrada = if (query.isBlank()) listaCompleta
        else listaCompleta.filter {
            it.nombre.contains(query, ignoreCase = true) ||
            it.tecnica.contains(query, ignoreCase = true) ||
            it.artesano.contains(query, ignoreCase = true)
        }
        submitList(filtrada)
    }

    override fun submitList(list: List<Producto>?) {
        listaCompleta = list ?: emptyList()
        super.submitList(list)
    }

    inner class VH(private val b: ItemProductoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Producto) {
            b.tvNombre.text = p.nombre
            b.tvPrecio.text = p.precio.formatearPrecio()
            b.tvTecnica.text = p.tecnica.ifBlank { "Alfarería" }
            b.tvOrigen.text = p.origen
            b.tvStock.text = "Stock: ${p.stock}"
            b.tvStock.setTextColor(
                if (p.stockBajo) 0xFFE53935.toInt() else 0xFF43A047.toInt()
            )

            // Imagen: Glide carga y cachea el bitmap desde el archivo local
            // tomado con la cámara (ver CamaraFragment) de forma asíncrona,
            // sin bloquear el hilo principal ni recargarla en cada scroll.
            if (p.imagenPath.isNotBlank()) {
                Glide.with(b.root).load(File(p.imagenPath)).into(b.ivProducto)
            } else {
                b.ivProducto.setImageResource(com.artesanias.app.R.drawable.ic_pottery_placeholder)
            }

            b.btnAgregar.setOnClickListener { onAgregar(p) }
            b.root.setOnClickListener { onClick(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Producto>() {
            override fun areItemsTheSame(a: Producto, b: Producto) = a.id == b.id
            override fun areContentsTheSame(a: Producto, b: Producto) = a == b
        }
    }
}

// ─────────────────────────────────────────────
// ADAPTADOR ADMIN PRODUCTOS
// ─────────────────────────────────────────────
/** Lista de inventario para el panel de admin: muestra estado activo/inactivo y alerta de stock bajo. */
class ProductoAdminAdapter(
    private val onEditar: (Producto) -> Unit,
    private val onAgregarStock: (Producto) -> Unit
) : ListAdapter<Producto, ProductoAdminAdapter.VH>(ProductoAdapter.DIFF) {

    private var listaCompleta: List<Producto> = emptyList()

    fun filtrar(query: String) {
        val f = if (query.isBlank()) listaCompleta
        else listaCompleta.filter { it.nombre.contains(query, true) }
        super.submitList(f)
    }

    override fun submitList(list: List<Producto>?) {
        listaCompleta = list ?: emptyList()
        super.submitList(list)
    }

    inner class VH(private val b: ItemProductoAdminBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Producto) {
            b.tvNombre.text = p.nombre
            b.tvPrecio.text = p.precio.formatearPrecio()
            b.tvStock.text = "Stock: ${p.stock}"
            b.chipEstado.text = if (p.activo) "Activo" else "Inactivo"
            b.chipEstado.setChipBackgroundColorResource(
                if (p.activo) com.artesanias.app.R.color.colorSuccess
                else com.artesanias.app.R.color.colorError
            )
            b.chipStockBajo.visibility = if (p.stockBajo) View.VISIBLE else View.GONE
            b.chipStockBajo.text = "⚠️ Stock bajo"

            if (p.imagenPath.isNotBlank()) {
                Glide.with(b.root).load(File(p.imagenPath)).into(b.ivProducto)
            }

            b.btnEditar.setOnClickListener { onEditar(p) }
            b.btnStock.setOnClickListener { onAgregarStock(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductoAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(getItem(pos))
}

// ─────────────────────────────────────────────
// ADAPTADOR USUARIOS (admin)
// ─────────────────────────────────────────────
/** Lista de cuentas para el panel de admin, con un switch para activar/desactivar cada una. */
class UsuarioAdapter(
    private val onToggleActivo: (com.artesanias.app.data.model.Usuario) -> Unit
) : ListAdapter<com.artesanias.app.data.model.Usuario, UsuarioAdapter.VH>(
    object : DiffUtil.ItemCallback<com.artesanias.app.data.model.Usuario>() {
        override fun areItemsTheSame(a: com.artesanias.app.data.model.Usuario, b: com.artesanias.app.data.model.Usuario) = a.id == b.id
        override fun areContentsTheSame(a: com.artesanias.app.data.model.Usuario, b: com.artesanias.app.data.model.Usuario) = a == b
    }
) {
    inner class VH(private val b: com.artesanias.app.databinding.ItemUsuarioBinding)
        : RecyclerView.ViewHolder(b.root) {
        fun bind(u: com.artesanias.app.data.model.Usuario) {
            b.tvNombre.text = "${u.nombre} ${u.apellido}"
            b.tvEmail.text = u.email
            b.chipRol.text = if (u.rol == com.artesanias.app.data.model.RolUsuario.ADMIN) "Admin" else "Cliente"
            b.switchActivo.isChecked = u.activo
            b.switchActivo.setOnCheckedChangeListener { _, _ -> onToggleActivo(u) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(com.artesanias.app.databinding.ItemUsuarioBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(getItem(pos))
}

// ─────────────────────────────────────────────
// ADAPTADOR CARRITO
// ─────────────────────────────────────────────
/**
 * Lista del carrito, con controles de +/- cantidad y eliminar por línea.
 * A diferencia de los adaptadores de arriba, extiende `RecyclerView.Adapter`
 * directo (no `ListAdapter`) porque el carrito es una lista pequeña que
 * cambia por completo en cada actualización: no vale la pena el cálculo de
 * diffs, basta con `notifyDataSetChanged()`.
 */
class CarritoAdapter(
    private val onMasCantidad: (com.artesanias.app.data.model.ItemCarrito) -> Unit,
    private val onMenosCantidad: (com.artesanias.app.data.model.ItemCarrito) -> Unit,
    private val onEliminar: (com.artesanias.app.data.model.ItemCarrito) -> Unit
) : RecyclerView.Adapter<CarritoAdapter.VH>() {

    private val items = mutableListOf<com.artesanias.app.data.model.ItemCarrito>()

    fun updateItems(nuevos: List<com.artesanias.app.data.model.ItemCarrito>) {
        items.clear()
        items.addAll(nuevos)
        notifyDataSetChanged()
    }

    inner class VH(private val b: com.artesanias.app.databinding.ItemCarritoBinding)
        : RecyclerView.ViewHolder(b.root) {
        fun bind(item: com.artesanias.app.data.model.ItemCarrito) {
            b.tvNombre.text = item.producto.nombre
            b.tvPrecioUnit.text = item.producto.precio.formatearPrecio()
            b.tvCantidad.text = item.cantidad.toString()
            b.tvSubtotal.text = item.subtotal.formatearPrecio()
            b.btnMas.setOnClickListener { onMasCantidad(item) }
            b.btnMenos.setOnClickListener { onMenosCantidad(item) }
            b.btnEliminar.setOnClickListener { onEliminar(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(com.artesanias.app.databinding.ItemCarritoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos])
    override fun getItemCount() = items.size
}

```

