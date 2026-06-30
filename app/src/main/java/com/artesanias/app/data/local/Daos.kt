package com.artesanias.app.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.artesanias.app.data.model.*
import kotlinx.coroutines.flow.Flow

// ───────────── USUARIO DAO ─────────────
@Dao
interface UsuarioDao {

    @Query("SELECT * FROM usuarios ORDER BY nombre ASC")
    fun getAllUsuarios(): Flow<List<Usuario>>

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun getUsuarioById(id: Int): Usuario?

    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    suspend fun getUsuarioByEmail(email: String): Usuario?

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

    @Query("SELECT * FROM productos WHERE activo = 1 ORDER BY nombre ASC")
    fun getAllProductos(): Flow<List<Producto>>

    @Query("SELECT * FROM productos ORDER BY nombre ASC")
    fun getAllProductosAdmin(): Flow<List<Producto>>

    @Query("SELECT * FROM productos WHERE id = :id")
    suspend fun getProductoById(id: Int): Producto?

    @Query("SELECT * FROM productos WHERE categoriaId = :categoriaId AND activo = 1")
    fun getProductosByCategoria(categoriaId: Int): Flow<List<Producto>>

    @Query("SELECT * FROM productos WHERE stock <= 5 AND activo = 1")
    fun getProductosConStockBajo(): Flow<List<Producto>>

    // ← NUEVO: versión suspend para usar desde el Service sin Flow
    @Query("SELECT * FROM productos WHERE stock <= 5 AND activo = 1 ORDER BY stock ASC")
    suspend fun getProductosConStockBajoSync(): List<Producto>

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

    @Query("UPDATE productos SET stock = stock - :cantidad WHERE id = :id AND stock >= :cantidad")
    suspend fun reducirStock(id: Int, cantidad: Int): Int

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
}

// ───────────── DETALLE ORDEN DAO ─────────────
@Dao
interface DetalleOrdenDao {

    @Query("SELECT * FROM detalle_orden WHERE ordenId = :ordenId")
    fun getDetallesByOrden(ordenId: Int): Flow<List<DetalleOrden>>

    @Insert
    suspend fun insertDetalle(detalle: DetalleOrden)

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