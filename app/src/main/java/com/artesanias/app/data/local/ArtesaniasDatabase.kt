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
