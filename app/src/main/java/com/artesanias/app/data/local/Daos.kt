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
