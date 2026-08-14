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
