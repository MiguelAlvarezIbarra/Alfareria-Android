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

// ─────────────────────────────────────────────
// AUTH VIEW MODEL
// ─────────────────────────────────────────────
@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)

    private val _loginResult = MutableLiveData<Result<Usuario>>()
    val loginResult: LiveData<Result<Usuario>> = _loginResult

    private val _registerResult = MutableLiveData<Result<Long>>()
    val registerResult: LiveData<Result<Long>> = _registerResult

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn
    val currentUser get() = sessionManager

    fun login(email: String, password: String) {
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

    fun cerrarSesion() = sessionManager.cerrarSesion()
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

    val productos: LiveData<List<Producto>> =
        productoRepository.getProductosAdmin().asLiveData()

    val productosStockBajo: LiveData<List<Producto>> =
        productoRepository.getProductosStockBajo().asLiveData()

    private val _operacionResult = MutableLiveData<Result<Unit>>()
    val operacionResult: LiveData<Result<Unit>> = _operacionResult

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    // ── Receiver para comandos del Wear OS ──
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
        // Registrar receiver para escuchar comandos del Wear
        val filter = IntentFilter().apply {
            addAction("com.artesanias.app.AGREGAR_STOCK")
            addAction("com.artesanias.app.SOLICITAR_STOCK_LISTA")
        }
        context.registerReceiver(wearReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(wearReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

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
            _operacionResult.value = result.map { }
        }
    }

    fun toggleActivo(usuario: Usuario) {
        viewModelScope.launch {
            usuarioRepository.setActivo(usuario.id, !usuario.activo)
        }
    }
}

// ─────────────────────────────────────────────
// TIENDA VIEW MODEL (cliente)
// ─────────────────────────────────────────────
@HiltViewModel
class TiendaViewModel @Inject constructor(
    application: Application,
    private val productoRepository: ProductoRepository,
    private val ordenRepository: OrdenRepository
) : AndroidViewModel(application) {

    private val session = SessionManager(application)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val productos: LiveData<List<Producto>> = _query.flatMapLatest { q ->
        if (q.isBlank()) productoRepository.getProductos()
        else productoRepository.buscar(q)
    }.asLiveData()

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

    private val _ordenResult = MutableLiveData<Result<Orden>>()
    val ordenResult: LiveData<Result<Orden>> = _ordenResult

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun buscar(q: String) { _query.value = q }

    fun agregarAlCarrito(producto: Producto, cantidad: Int = 1) {
        val lista = _carrito.value ?: mutableListOf()
        val existing = lista.find { it.producto.id == producto.id }
        if (existing != null) {
            existing.cantidad += cantidad
        } else {
            lista.add(ItemCarrito(producto, cantidad))
        }
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