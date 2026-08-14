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
