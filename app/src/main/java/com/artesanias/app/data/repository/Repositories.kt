package com.artesanias.app.data.repository

import com.artesanias.app.data.local.*
import com.artesanias.app.data.model.*
import com.artesanias.app.data.remote.WearDataSender
import com.artesanias.app.util.HashUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ───────────── AUTH REPOSITORY ─────────────
@Singleton
class AuthRepository @Inject constructor(
    private val usuarioDao: UsuarioDao
) {
    suspend fun login(email: String, password: String): Usuario? {
        val hash = HashUtil.hash(password)
        return usuarioDao.login(email.trim().lowercase(), hash)
    }

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
    private val wearSender: WearDataSender
) {
    fun getProductos(): Flow<List<Producto>> = productoDao.getAllProductos()
    fun getProductosAdmin(): Flow<List<Producto>> = productoDao.getAllProductosAdmin()
    fun getProductosStockBajo(): Flow<List<Producto>> = productoDao.getProductosConStockBajo()
    fun buscar(q: String): Flow<List<Producto>> = productoDao.buscarProductos(q)
    fun porCategoria(id: Int): Flow<List<Producto>> = productoDao.getProductosByCategoria(id)
    suspend fun getProductoById(id: Int): Producto? = productoDao.getProductoById(id)

    suspend fun insertarProducto(producto: Producto): Long =
        productoDao.insertProducto(producto)

    suspend fun actualizarProducto(producto: Producto) {
        productoDao.updateProducto(producto)
        if (producto.stockBajo) notificarStockBajo(producto)
    }

    suspend fun agregarStock(productoId: Int, cantidad: Int) {
        productoDao.agregarStock(productoId, cantidad)
        wearSender.enviarMensaje(
            "/stock/actualizado",
            "Producto ID $productoId: +$cantidad unidades agregadas"
        )
    }

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
    private val wearSender: WearDataSender
) {
    fun getOrdenes(): Flow<List<Orden>> = ordenDao.getAllOrdenes()
    fun getMisOrdenes(usuarioId: Int): Flow<List<Orden>> = ordenDao.getOrdenesByUsuario(usuarioId)
    fun getDetalles(ordenId: Int): Flow<List<DetalleOrden>> =
        detalleOrdenDao.getDetallesByOrden(ordenId)

    // ───────────── ORDEN REPOSITORY ─────────────

    suspend fun crearOrden(
        usuarioId: Int,
        items: List<ItemCarrito>
    ): Result<Orden> {
        return try {
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

            // Notificaciones según monto
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

            Result.success(ordenCreada)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmarOrden(ordenId: Int) {
        ordenDao.confirmarOrden(ordenId)
        ordenDao.cambiarEstado(ordenId, EstadoOrden.CONFIRMADA)
        wearSender.enviarMensaje("/orden/confirmada", "Orden #$ordenId confirmada")
    }
}

// ───────────── USUARIO REPOSITORY ─────────────
@Singleton
class UsuarioRepository @Inject constructor(
    private val usuarioDao: UsuarioDao
) {
    fun getUsuarios(): Flow<List<Usuario>> = usuarioDao.getAllUsuarios()

    suspend fun getUsuario(id: Int): Usuario? = usuarioDao.getUsuarioById(id)

    suspend fun actualizar(usuario: Usuario) = usuarioDao.updateUsuario(usuario)

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