package com.artesanias.app.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.artesanias.app.data.model.*
import com.artesanias.app.util.HashUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        @Volatile private var INSTANCE: ArtesaniasDatabase? = null

        fun getInstance(context: Context): ArtesaniasDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, ArtesaniasDatabase::class.java, "artesanias.db")
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Pre-poblar con datos de ejemplo
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    poblarDatosIniciales(database)
                                }
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }

        private suspend fun poblarDatosIniciales(db: ArtesaniasDatabase) {
            // Admin por defecto
            db.usuarioDao().insertUsuario(
                Usuario(
                    nombre = "Administrador",
                    apellido = "Sistema",
                    email = "admin@artesanias.mx",
                    passwordHash = HashUtil.hash("Admin123"),
                    rol = RolUsuario.ADMIN
                )
            )
            // Cliente de prueba
            db.usuarioDao().insertUsuario(
                Usuario(
                    nombre = "María",
                    apellido = "González",
                    email = "cliente@artesanias.mx",
                    passwordHash = HashUtil.hash("Cliente123"),
                    rol = RolUsuario.CLIENTE
                )
            )

            // Categorías
            val catTalavera = db.categoriaDao().insertCategoria(
                Categoria(nombre = "Talavera", descripcion = "Cerámica tradicional de Puebla")
            )
            val catBarro = db.categoriaDao().insertCategoria(
                Categoria(nombre = "Barro Negro", descripcion = "Alfarería de Oaxaca")
            )
            val catMayolica = db.categoriaDao().insertCategoria(
                Categoria(nombre = "Mayólica", descripcion = "Cerámica esmaltada")
            )
            val catBandeja = db.categoriaDao().insertCategoria(
                Categoria(nombre = "Utilitaria", descripcion = "Piezas de uso diario")
            )

            // Productos de ejemplo
            val productos = listOf(
                Producto(
                    nombre = "Plato Talavera Grande",
                    descripcion = "Plato decorativo tradicional de Puebla con motivos florales",
                    precio = 350.0, stock = 12,
                    categoriaId = catTalavera.toInt(),
                    tecnica = "Talavera", origen = "Puebla", artesano = "Familia Uriarte"
                ),
                Producto(
                    nombre = "Vasija Barro Negro",
                    descripcion = "Pieza única de barro negro pulido de Oaxaca",
                    precio = 480.0, stock = 4,
                    categoriaId = catBarro.toInt(),
                    tecnica = "Barro Negro", origen = "San Bartolo Coyotepec, Oaxaca", artesano = "Rosa Nieto"
                ),
                Producto(
                    nombre = "Jarro Talavera",
                    descripcion = "Jarro con asa, decoración azul cobalto",
                    precio = 180.0, stock = 20,
                    categoriaId = catTalavera.toInt(),
                    tecnica = "Talavera", origen = "Dolores Hidalgo, Gto", artesano = "Taller del Sol"
                ),
                Producto(
                    nombre = "Cazuela de Barro",
                    descripcion = "Cazuela para cocinar, resistente al calor",
                    precio = 220.0, stock = 8,
                    categoriaId = catBandeja.toInt(),
                    tecnica = "Alfarería Utilitaria", origen = "Michoacán", artesano = "Cooperativa La Barro"
                ),
                Producto(
                    nombre = "Jarrón Mayólica",
                    descripcion = "Jarrón alto con esmalte y decoración policromada",
                    precio = 650.0, stock = 3,
                    categoriaId = catMayolica.toInt(),
                    tecnica = "Mayólica", origen = "Guanajuato", artesano = "Taller Gorky"
                ),
                Producto(
                    nombre = "Tazón Barro Rojo",
                    descripcion = "Tazón para sopas, barro rojo natural",
                    precio = 95.0, stock = 2,
                    categoriaId = catBandeja.toInt(),
                    tecnica = "Barro Rojo", origen = "Tlaquepaque, Jalisco", artesano = "Artesanos Unidos"
                ),
                Producto(
                    nombre = "Florero Talavera Mini",
                    descripcion = "Florero pequeño para decoración, multicolor",
                    precio = 120.0, stock = 15,
                    categoriaId = catTalavera.toInt(),
                    tecnica = "Talavera", origen = "Puebla", artesano = "Familia Uriarte"
                ),
                Producto(
                    nombre = "Incensario Barro Negro",
                    descripcion = "Incensario ritual de barro negro, pieza ceremonial",
                    precio = 380.0, stock = 5,
                    categoriaId = catBarro.toInt(),
                    tecnica = "Barro Negro", origen = "San Marcos Tlapazola, Oaxaca", artesano = "Maestro Jiménez"
                )
            )
            productos.forEach { db.productoDao().insertProducto(it) }
        }
    }
}

// Convertidores de tipos para Room
class Converters {
    @TypeConverter fun fromRol(rol: RolUsuario): String = rol.name
    @TypeConverter fun toRol(s: String): RolUsuario = RolUsuario.valueOf(s)

    @TypeConverter fun fromEstado(e: EstadoOrden): String = e.name
    @TypeConverter fun toEstado(s: String): EstadoOrden = EstadoOrden.valueOf(s)

    @TypeConverter fun fromTipoNotif(t: TipoNotificacion): String = t.name
    @TypeConverter fun toTipoNotif(s: String): TipoNotificacion = TipoNotificacion.valueOf(s)
}
