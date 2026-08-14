# 📺 Módulo TV (`tv/`)

App para Android TV que muestra, en una pantalla grande visible para todo el negocio, el catálogo de productos, un resumen de ventas y un mapa de los talleres artesanales — todo actualizado en vivo desde el teléfono del administrador.

⬅️ [Volver al README principal](../README.md)

---

## 🎯 Qué hace

No es un cliente independiente: no tiene su propia base de datos ni lógica de negocio. Es un **receptor pasivo** que dibuja lo que el teléfono le manda por socket TCP. Si el teléfono no está enviando nada (no se pareó o se desconectó el cable), la TV simplemente se queda con la última información que recibió.

### Pantallas (navegación por riel lateral, control remoto/D-pad)

| Pantalla | Fragment | Contenido |
|---|---|---|
| Catálogo | `ProductosFragment` | Cuadrícula de productos activos con precio y stock (⚠️ si es bajo) |
| Ventas | `VentasFragment` | Gráfica de barras (MPAndroidChart) de más vendidos + tabla de compras de los últimos 7 días |
| Talleres | `MapaFragment` | Mapa (Google Maps JS embebido en WebView) con los 7 talleres artesanales del catálogo |

Además, sin importar qué pantalla esté activa, si llega una alerta de **compra grande** aparece un diálogo overlay encima de todo (`MainActivity.mostrarNotificacionCompraGrande`) que se cierra solo a los 8 segundos.

Ver más detalle de la comunicación y el protocolo en el [README principal](../README.md#-comunicación-teléfono--tv).

---

## 🛠️ Stack y librerías

| Librería | Versión | Para qué |
|---|---|---|
| `androidx.leanback` | 1.0.0 | Estilos y componentes pensados para pantalla grande/TV |
| `androidx.appcompat` | 1.6.1 | Compatibilidad base de Activity/temas |
| `androidx.fragment-ktx` | 1.6.2 | Fragments (`ProductosFragment`, `VentasFragment`, `MapaFragment`) |
| `androidx.lifecycle` (viewmodel/runtime/livedata-ktx) | 2.7.0 | `lifecycleScope` para las corrutinas que leen `TvDataStore` |
| `com.google.android.material` | 1.11.0 | Componentes de Material Design |
| `com.github.PhilJay:MPAndroidChart` | v3.1.0 | Gráfica de barras de "más vendidos" |
| `kotlinx-coroutines-android` | 1.7.3 | El servidor de sockets (`TvServer`) corre en una corrutina sobre `Dispatchers.IO` |

No usa Hilt, Room ni Navigation Component — a propósito, ver [Notas de desarrollo](#-notas-de-desarrollo).

---

## 🏗️ Estructura

```
tv/src/main/java/com/artesanias/tv/
├── TvApplication.kt        # Application; arranca TvServer al inicio
├── MainActivity.kt         # Única Activity; cambia Fragments manualmente + overlay de compra grande
├── data/
│   ├── Modelos.kt          # Data classes locales (TvProducto, TvMasVendido, TvCompraSemana...)
│   └── TvDataStore.kt      # Estado en memoria (StateFlow) que alimenta las 3 pantallas
├── net/
│   └── TvServer.kt         # Servidor TCP: recibe JSON del teléfono y actualiza TvDataStore
└── ui/
    ├── ProductosFragment.kt
    ├── VentasFragment.kt
    └── MapaFragment.kt
```

---

## 🚀 Instalación

### 1. Requisito previo: `MAPS_API_KEY`

La pantalla de Talleres necesita una API key de Google Maps JavaScript API válida en `local.properties` (en la raíz del proyecto, mismo archivo que usa el módulo `app`):

```properties
MAPS_API_KEY=tu_api_key_aquí
```

### 2. Instalar en un dispositivo o emulador de Android TV

```
Run → Run 'tv'   # Selecciona un AVD de Android TV (API 26+) o un dispositivo TV real
```

> Si no tienes un AVD de TV creado: Android Studio → Device Manager → Create Device → categoría **TV** (por ejemplo "Television (1080p)").

### 3. Conectar la TV con el teléfono

La TV, por sí sola, no muestra nada útil — necesita que el teléfono le esté mandando datos. Ver la sección **"Cómo generar el puente de comunicación de la TV"** en el [README principal](../README.md#-cómo-generar-el-puente-de-comunicación-de-la-tv), es un paso obligatorio antes de probar este módulo.

---

## 📝 Notas de desarrollo

- No usa **Hilt**: a diferencia de `app` y `wear`, este módulo no tiene inyección de dependencias — `TvServer` y `TvDataStore` son `object` (singletons de Kotlin), suficiente para el tamaño del módulo.
- No usa **Navigation Component**: `MainActivity` cambia el Fragment visible a mano con `supportFragmentManager.beginTransaction().replace(...)`, porque solo hay 3 pantallas planas sin backstack real (navegación tipo "tabs" con control remoto).
- El mapa usa un **WebView** (no el SDK nativo de Maps) porque es la forma más simple de reutilizar el mismo HTML/JS de pines en ambos módulos (`app` y `tv`) sin duplicar lógica nativa de mapas.
- El puerto del servidor (`TvServer.PUERTO`) es **8766**. Si lo cambias, también hay que cambiarlo en `TvDataSender.PUERTO` (módulo `app`).

---

## 📄 Código fuente completo

### `tv/src/main/java/com/artesanias/tv/TvApplication.kt`

```kotlin
package com.artesanias.tv

import android.app.Application
import com.artesanias.tv.net.TvServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Clase Application de la app de TV. A diferencia del módulo de teléfono
 * (que usa Hilt), aquí no hay inyección de dependencias: el único trabajo
 * de arranque es levantar el servidor de sockets (`TvServer`) en cuanto
 * el proceso inicia, para que esté escuchando conexiones del teléfono
 * incluso antes de que se abra ninguna pantalla.
 */
class TvApplication : Application() {
    // Alcance de corrutinas de toda la app: vive mientras viva el proceso,
    // así que el servidor sigue corriendo aunque cambie de pantalla.
    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        TvServer.iniciar(appScope)
    }
}

```

### `tv/src/main/java/com/artesanias/tv/MainActivity.kt`

```kotlin
package com.artesanias.tv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.artesanias.tv.data.TvDataStore
import com.artesanias.tv.databinding.ActivityMainBinding
import com.artesanias.tv.ui.MapaFragment
import com.artesanias.tv.ui.ProductosFragment
import com.artesanias.tv.ui.VentasFragment
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Única Activity de la app de TV: no usa Navigation Component (a
 * diferencia del módulo de teléfono), solo cambia manualmente el
 * Fragment visible dentro de `fragmentContainer` según el ítem del riel
 * de navegación lateral que se seleccione (ver `mostrarPantalla`). También
 * escucha, mientras viva la Activity, el evento global de "compra grande"
 * para mostrar la alerta encima de cualquier pantalla que esté activa.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ocultarOverlay = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            mostrarPantalla(ProductosFragment(), binding.navProductos)
        }

        binding.navProductos.setOnClickListener { mostrarPantalla(ProductosFragment(), binding.navProductos) }
        binding.navVentas.setOnClickListener { mostrarPantalla(VentasFragment(), binding.navVentas) }
        binding.navMapa.setOnClickListener { mostrarPantalla(MapaFragment(), binding.navMapa) }

        // El overlay escucha el evento global sin importar qué Fragment esté
        // activo: por eso vive en la Activity y no en cada pantalla. Se usa
        // un Dialog (ventana propia) en vez de alternar la visibilidad de un
        // View hijo: es el patrón más confiable para "mostrar algo encima
        // de todo" y evita depender de que el layout padre recalcule bien
        // un View que arrancó en GONE.
        lifecycleScope.launch {
            TvDataStore.compraGrandeEvents.collect { evento ->
                mostrarNotificacionCompraGrande(evento)
            }
        }
    }

    private var dialogoCompraGrande: android.app.Dialog? = null

    /** Muestra (o reemplaza, si ya había una) la tarjeta de alerta, y la oculta sola a los 8 segundos. */
    private fun mostrarNotificacionCompraGrande(evento: com.artesanias.tv.data.TvCompraGrandeEvent) {
        dialogoCompraGrande?.dismiss()
        ocultarOverlay.removeCallbacksAndMessages(null)

        val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        val dialogBinding = com.artesanias.tv.databinding.DialogCompraGrandeBinding.inflate(layoutInflater)
        dialogBinding.txtOverlayProducto.text = evento.producto
        dialogBinding.txtOverlayMonto.text = "${formatoMoneda.format(evento.monto)} MXN"

        val dialog = android.app.Dialog(this, R.style.Theme_CompraGrandeDialog).apply {
            setContentView(dialogBinding.root)
            setCancelable(true)
            setOnDismissListener { dialogoCompraGrande = null }
        }
        dialogBinding.root.setOnClickListener { dialog.dismiss() }
        dialogoCompraGrande = dialog
        dialog.show()

        ocultarOverlay.postDelayed(8000) { dialog.dismiss() }
    }

    /** Reemplaza la pantalla activa y resalta el ítem correspondiente del riel de navegación. */
    private fun mostrarPantalla(fragment: Fragment, itemSeleccionado: android.view.View) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()

        listOf(binding.navProductos, binding.navVentas, binding.navMapa).forEach {
            it.isSelected = it == itemSeleccionado
        }

        // El contenido de cada pantalla (RecyclerView, VideoView...) puede
        // robarse el foco de control remoto al aparecer; lo regresamos al
        // riel de navegación para no perder la navegación por D-pad.
        itemSeleccionado.post { itemSeleccionado.requestFocus() }
    }
}

```

### `tv/src/main/java/com/artesanias/tv/data/Modelos.kt`

```kotlin
package com.artesanias.tv.data

// Modelos de datos del módulo TV: son simples `data class`, sin `@Entity`
// ni Room, porque la app de TV no tiene base de datos propia — solo
// mantiene en memoria (ver TvDataStore) lo último que le mandó el
// teléfono por el socket, para dibujarlo en pantalla.

/** Un producto del catálogo, tal como lo manda el teléfono (ver TvDataSender.enviarCatalogo). */
data class TvProducto(
    val nombre: String,
    val precio: Double,
    val stock: Int,
    val categoria: String = ""
)

/** Una fila del ranking de más vendidos (Pantalla 2). */
data class TvMasVendido(
    val nombre: String,
    val cantidad: Int
)

/** Una compra de la semana, para la tabla de la Pantalla 2. */
data class TvCompraSemana(
    val fecha: String,
    val cliente: String,
    val total: Double
)

/** Datos de la alerta de "compra grande" que dispara el overlay sobre cualquier pantalla. */
data class TvCompraGrandeEvent(
    val producto: String,
    val monto: Double
)

```

### `tv/src/main/java/com/artesanias/tv/data/TvDataStore.kt`

```kotlin
package com.artesanias.tv.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado en memoria de la TV. Arranca con datos de demostración para que las
 * pantallas no se vean vacías si el teléfono todavía no se ha conectado, y
 * se sobreescribe con datos reales en cuanto TvServer recibe el primer sync.
 *
 * `StateFlow` vs `SharedFlow`: un `StateFlow` siempre tiene un valor actual
 * (aquí, el último catálogo/ranking/tabla recibido) y se lo entrega de
 * inmediato a cualquier pantalla que empiece a observarlo, ideal para
 * "estado" que se puede volver a mostrar. Un `SharedFlow` con `replay = 0`
 * (como `compraGrandeEvents` más abajo) no guarda nada: solo notifica a
 * quien esté escuchando EN ESE MOMENTO, correcto para una alerta puntual
 * que no debe "repetirse sola" si la pantalla se vuelve a abrir después.
 */
object TvDataStore {

    private val _productos = MutableStateFlow(datosDemoProductos())
    val productos: StateFlow<List<TvProducto>> = _productos.asStateFlow()

    private val _masVendidos = MutableStateFlow(datosDemoMasVendidos())
    val masVendidos: StateFlow<List<TvMasVendido>> = _masVendidos.asStateFlow()

    private val _comprasSemana = MutableStateFlow(datosDemoComprasSemana())
    val comprasSemana: StateFlow<List<TvCompraSemana>> = _comprasSemana.asStateFlow()

    /** replay = 0: es un evento puntual (notificación), no un estado a restaurar. */
    val compraGrandeEvents = MutableSharedFlow<TvCompraGrandeEvent>(replay = 0, extraBufferCapacity = 4)

    private val _conectado = MutableStateFlow(false)
    val conectado: StateFlow<Boolean> = _conectado.asStateFlow()

    fun actualizarProductos(items: List<TvProducto>) {
        _productos.value = items
        _conectado.value = true
    }

    fun actualizarMasVendidos(items: List<TvMasVendido>) {
        _masVendidos.value = items
        _conectado.value = true
    }

    fun actualizarComprasSemana(items: List<TvCompraSemana>) {
        _comprasSemana.value = items
        _conectado.value = true
    }

    suspend fun emitirCompraGrande(evento: TvCompraGrandeEvent) {
        _conectado.value = true
        compraGrandeEvents.emit(evento)
    }

    private fun datosDemoProductos() = listOf(
        TvProducto("Plato Talavera Grande", 350.0, 12, "Talavera"),
        TvProducto("Vasija Barro Negro", 480.0, 4, "Barro Negro"),
        TvProducto("Jarro Talavera", 180.0, 20, "Talavera"),
        TvProducto("Cazuela de Barro", 220.0, 8, "Utilitaria"),
        TvProducto("Jarrón Mayólica", 650.0, 3, "Mayólica"),
        TvProducto("Tazón Barro Rojo", 95.0, 2, "Utilitaria"),
        TvProducto("Florero Talavera Mini", 120.0, 15, "Talavera"),
        TvProducto("Incensario Barro Negro", 380.0, 5, "Barro Negro")
    )

    private fun datosDemoMasVendidos() = listOf(
        TvMasVendido("Jarro Talavera", 14),
        TvMasVendido("Florero Talavera Mini", 11),
        TvMasVendido("Cazuela de Barro", 8),
        TvMasVendido("Tazón Barro Rojo", 6),
        TvMasVendido("Vasija Barro Negro", 3)
    )

    private fun datosDemoComprasSemana() = listOf(
        TvCompraSemana("Lun", "María González", 480.0),
        TvCompraSemana("Mar", "Cliente Tienda", 180.0),
        TvCompraSemana("Mié", "Cliente Tienda", 650.0),
        TvCompraSemana("Jue", "María González", 220.0),
        TvCompraSemana("Vie", "Cliente Tienda", 1150.0),
        TvCompraSemana("Sáb", "Cliente Tienda", 95.0)
    )
}

```

### `tv/src/main/java/com/artesanias/tv/net/TvServer.kt`

```kotlin
package com.artesanias.tv.net

import android.util.Log
import com.artesanias.tv.data.TvCompraGrandeEvent
import com.artesanias.tv.data.TvCompraSemana
import com.artesanias.tv.data.TvDataStore
import com.artesanias.tv.data.TvMasVendido
import com.artesanias.tv.data.TvProducto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * Servidor TCP muy simple que recibe del teléfono (vía TvDataSender) el
 * catálogo de productos, el resumen de ventas y las alertas de compra
 * grande. Cada mensaje es una línea de JSON terminada en '\n'.
 *
 * Protocolo (campo "tipo"):
 *   productos       -> {"tipo":"productos","items":[{"nombre","precio","stock","categoria"}...]}
 *   mas_vendidos    -> {"tipo":"mas_vendidos","items":[{"nombre","cantidad"}...]}
 *   compras_semana  -> {"tipo":"compras_semana","items":[{"fecha","cliente","total"}...]}
 *   compra_grande   -> {"tipo":"compra_grande","producto":"...","monto":123.45}
 */
object TvServer {
    const val PUERTO = 8766
    private const val TAG = "TvServer"

    // Evita levantar el servidor dos veces si iniciar() se llama más de
    // una vez (p.ej. si TvApplication.onCreate se disparara de nuevo).
    private var iniciado = false

    /** Abre el ServerSocket y arranca el ciclo de aceptar conexiones; no bloquea al que lo llama. */
    fun iniciar(scope: CoroutineScope) {
        if (iniciado) return
        iniciado = true
        // Dispatchers.IO: hilo pensado para operaciones bloqueantes de
        // entrada/salida (sockets, disco), para no usar el hilo principal.
        scope.launch(Dispatchers.IO) {
            try {
                ServerSocket(PUERTO).use { serverSocket ->
                    Log.i(TAG, "Escuchando en puerto $PUERTO")
                    // serverSocket.accept() bloquea hasta que llega una
                    // conexión nueva; cada una se atiende en su propia
                    // corrutina para poder seguir aceptando otras mientras
                    // tanto (el teléfono manda varios mensajes seguidos,
                    // cada uno en su propia conexión corta).
                    while (true) {
                        val socket = serverSocket.accept()
                        launch(Dispatchers.IO) { manejarCliente(socket) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en el servidor: ${e.message}")
            }
        }
    }

    /** Lee líneas del socket (un JSON por línea) hasta que el teléfono cierra la conexión. */
    private suspend fun manejarCliente(socket: Socket) {
        socket.use {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var linea: String?
                while (true) {
                    linea = reader.readLine() ?: break
                    if (linea.isBlank()) continue
                    procesarMensaje(linea)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cliente desconectado: ${e.message}")
            }
        }
    }

    private suspend fun procesarMensaje(linea: String) {
        val json = try { JSONObject(linea) } catch (e: Exception) {
            Log.w(TAG, "Mensaje inválido: $linea")
            return
        }

        when (json.optString("tipo")) {
            "productos" -> {
                val items = json.getJSONArray("items")
                val lista = (0 until items.length()).map { i ->
                    val o = items.getJSONObject(i)
                    TvProducto(
                        nombre = o.getString("nombre"),
                        precio = o.getDouble("precio"),
                        stock = o.getInt("stock"),
                        categoria = o.optString("categoria", "")
                    )
                }
                TvDataStore.actualizarProductos(lista)
            }

            "mas_vendidos" -> {
                val items = json.getJSONArray("items")
                val lista = (0 until items.length()).map { i ->
                    val o = items.getJSONObject(i)
                    TvMasVendido(nombre = o.getString("nombre"), cantidad = o.getInt("cantidad"))
                }
                TvDataStore.actualizarMasVendidos(lista)
            }

            "compras_semana" -> {
                val items = json.getJSONArray("items")
                val lista = (0 until items.length()).map { i ->
                    val o = items.getJSONObject(i)
                    TvCompraSemana(
                        fecha = o.getString("fecha"),
                        cliente = o.getString("cliente"),
                        total = o.getDouble("total")
                    )
                }
                TvDataStore.actualizarComprasSemana(lista)
            }

            "compra_grande" -> {
                Log.i(TAG, "compra_grande recibido: $linea")
                TvDataStore.emitirCompraGrande(
                    TvCompraGrandeEvent(
                        producto = json.getString("producto"),
                        monto = json.getDouble("monto")
                    )
                )
                Log.i(TAG, "compra_grande emitido a TvDataStore")
            }

            else -> Log.w(TAG, "Tipo de mensaje desconocido: $linea")
        }
    }
}

```

### `tv/src/main/java/com/artesanias/tv/ui/ProductosFragment.kt`

```kotlin
package com.artesanias.tv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artesanias.tv.data.TvDataStore
import com.artesanias.tv.data.TvProducto
import com.artesanias.tv.databinding.FragmentProductosBinding
import com.artesanias.tv.databinding.ItemProductoBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Pantalla 1: catálogo de productos en cuadrícula, en vivo desde TvDataStore. */
class ProductosFragment : Fragment() {

    private var _binding: FragmentProductosBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductosAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerProductos.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.recyclerProductos.adapter = adapter

        // viewLifecycleOwner.lifecycleScope: la corrutina se cancela sola
        // cuando se destruye la VISTA del Fragment (no el Fragment en sí),
        // que es lo correcto para un `collect` que actualiza vistas —
        // sigue escuchando el catálogo en tiempo real mientras esta
        // pantalla esté visible, y se detiene al salir de ella.
        viewLifecycleOwner.lifecycleScope.launch {
            TvDataStore.productos.collect { adapter.actualizar(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class ProductosAdapter : RecyclerView.Adapter<ProductosAdapter.VH>() {
    private var items: List<TvProducto> = emptyList()
    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    fun actualizar(nuevos: List<TvProducto>) {
        items = nuevos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.binding.txtNombre.text = p.nombre
        holder.binding.txtPrecio.text = "${formatoMoneda.format(p.precio)} MXN"
        holder.binding.txtStock.text = if (p.stock <= 5) {
            "Stock: ${p.stock} ⚠️"
        } else {
            "Stock: ${p.stock}"
        }
    }

    override fun getItemCount() = items.size

    class VH(val binding: ItemProductoBinding) : RecyclerView.ViewHolder(binding.root)
}

```

### `tv/src/main/java/com/artesanias/tv/ui/VentasFragment.kt`

```kotlin
package com.artesanias.tv.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artesanias.tv.R
import com.artesanias.tv.data.TvCompraSemana
import com.artesanias.tv.data.TvDataStore
import com.artesanias.tv.data.TvMasVendido
import com.artesanias.tv.databinding.FragmentVentasBinding
import com.artesanias.tv.databinding.ItemCompraSemanaBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla 2: gráfica de barras de los productos más vendidos (usando la
 * librería MPAndroidChart) más la tabla de compras de la semana, ambas
 * actualizadas en vivo desde TvDataStore.
 */
class VentasFragment : Fragment() {

    private var _binding: FragmentVentasBinding? = null
    private val binding get() = _binding!!
    private val adapterCompras = ComprasAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVentasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerCompras.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCompras.adapter = adapterCompras
        configurarChart()

        viewLifecycleOwner.lifecycleScope.launch {
            TvDataStore.masVendidos.collect { dibujarChart(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            TvDataStore.comprasSemana.collect { adapterCompras.actualizar(it) }
        }
    }

    /** Ajustes de apariencia de la gráfica (una sola vez, no cambian entre actualizaciones de datos). */
    private fun configurarChart() {
        val textoClaro = ContextCompat.getColor(requireContext(), R.color.colorTextPrimary)
        binding.chartMasVendidos.apply {
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.textColor = textoClaro
            axisLeft.axisMinimum = 0f
            xAxis.textColor = textoClaro
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.textSize = 10f
            xAxis.labelRotationAngle = 0f
            xAxis.setAvoidFirstLastClipping(true)
            extraBottomOffset = 12f
            setFitBars(true)
        }
    }

    /** Reconstruye las barras de la gráfica cada vez que llega un ranking nuevo del teléfono. */
    private fun dibujarChart(items: List<TvMasVendido>) {
        val entries = items.mapIndexed { i, m -> BarEntry(i.toFloat(), m.cantidad.toFloat()) }
        val dataSet = BarDataSet(entries, "Unidades vendidas").apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.colorTextPrimary)
            valueTextSize = 12f
        }
        binding.chartMasVendidos.data = BarData(dataSet)
        binding.chartMasVendidos.xAxis.valueFormatter =
            IndexAxisValueFormatter(items.map { acortar(it.nombre) })
        binding.chartMasVendidos.invalidate()
    }

    /** Reduce el nombre del producto a su primera palabra para que las etiquetas no se encimen entre barras. */
    private fun acortar(nombre: String): String {
        val primeraPalabra = nombre.substringBefore(' ')
        return if (primeraPalabra.length > 9) primeraPalabra.take(8) + "…" else primeraPalabra
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class ComprasAdapter : RecyclerView.Adapter<ComprasAdapter.VH>() {
    private var items: List<TvCompraSemana> = emptyList()
    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    fun actualizar(nuevos: List<TvCompraSemana>) {
        items = nuevos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCompraSemanaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.binding.txtFecha.text = c.fecha
        holder.binding.txtCliente.text = c.cliente
        holder.binding.txtTotal.text = formatoMoneda.format(c.total)
    }

    override fun getItemCount() = items.size

    class VH(val binding: ItemCompraSemanaBinding) : RecyclerView.ViewHolder(binding.root)
}

```

### `tv/src/main/java/com/artesanias/tv/ui/MapaFragment.kt`

```kotlin
package com.artesanias.tv.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.fragment.app.Fragment
import com.artesanias.tv.BuildConfig
import com.artesanias.tv.databinding.FragmentMapaBinding

/**
 * Mapa de talleres artesanales para la TV, usando el Google Maps JavaScript
 * API dentro de un WebView. La primera vez que se probó esta pantalla se
 * veía en negro porque el reloj del emulador estaba desincronizado (varias
 * semanas atrás) y eso invalidaba el certificado TLS de Google, colgando la
 * carga sin ningún error visible; una vez corregido el reloj del emulador
 * el mapa carga con normalidad.
 * 
 *
 * Las coordenadas son las mismas regiones que en TalleresFragment del
 * módulo de teléfono (app/ui/store/TalleresFragment.kt).
 */
class MapaFragment : Fragment() {

    private var _binding: FragmentMapaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapaBinding.inflate(inflater, container, false)
        return binding.root
    }

    // @SuppressLint("SetJavaScriptEnabled"): silencia la advertencia del
    // linter de Android sobre habilitar JavaScript (un riesgo de
    // seguridad en general, si el WebView cargara contenido de terceros
    // no confiable); aquí es seguro porque el HTML lo genera esta misma
    // app, no viene de una fuente externa.
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.webChromeClient = WebChromeClient()
        // loadDataWithBaseURL con baseURL "https://maps.googleapis.com":
        // carga el HTML/JS generado localmente (no una URL real), pero le
        // dice al WebView que lo trate como si viniera de ese origen, para
        // que las peticiones que el script de Google Maps haga por debajo
        // (tiles, API) no choquen con las reglas de mismo-origen del navegador.
        binding.webView.loadDataWithBaseURL(
            "https://maps.googleapis.com", html(), "text/html", "utf-8", null
        )
    }

    /** Arma la página HTML+JavaScript que dibuja el mapa con los 7 pines de talleres artesanales. */
    private fun html(): String = """
        <!DOCTYPE html>
        <html>
        <head>
        <style>
            html, body, #map { height:100%; margin:0; padding:0; background:#1B1512; }
            .gm-style-iw { font-family: sans-serif; }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script>
        function initMap() {
            var talleres = [
                {lat:19.0413, lng:-98.2062, nombre:"Puebla", info:"Talavera — Familia Uriarte"},
                {lat:16.9678, lng:-96.6961, nombre:"San Bartolo Coyotepec, Oaxaca", info:"Barro Negro — Rosa Nieto"},
                {lat:21.1561, lng:-100.9330, nombre:"Dolores Hidalgo, Gto", info:"Talavera — Taller del Sol"},
                {lat:19.5138, lng:-101.6157, nombre:"Michoacán", info:"Alfarería Utilitaria — Cooperativa La Barro"},
                {lat:21.0190, lng:-101.2574, nombre:"Guanajuato", info:"Mayólica — Taller Gorky"},
                {lat:20.6409, lng:-103.3121, nombre:"Tlaquepaque, Jalisco", info:"Barro Rojo — Artesanos Unidos"},
                {lat:16.9958, lng:-96.4658, nombre:"San Marcos Tlapazola, Oaxaca", info:"Barro Negro — Maestro Jiménez"}
            ];
            var map = new google.maps.Map(document.getElementById('map'), {
                zoom: 5,
                center: {lat: 20.0, lng: -100.0},
                styles: [
                    {elementType:"geometry", stylers:[{color:"#2a211d"}]},
                    {elementType:"labels.text.fill", stylers:[{color:"#f5e6d3"}]},
                    {elementType:"labels.text.stroke", stylers:[{color:"#1b1512"}]},
                    {featureType:"water", elementType:"geometry", stylers:[{color:"#0e3a4a"}]},
                    {featureType:"road", elementType:"geometry", stylers:[{color:"#4a3b30"}]}
                ]
            });
            var bounds = new google.maps.LatLngBounds();
            talleres.forEach(function(t) {
                var pos = {lat: t.lat, lng: t.lng};
                var marker = new google.maps.Marker({position: pos, map: map, title: t.nombre});
                var info = new google.maps.InfoWindow({
                    content: '<b>' + t.nombre + '</b><br>' + t.info
                });
                marker.addListener('click', function() { info.open(map, marker); });
                bounds.extend(pos);
            });
            map.fitBounds(bounds);
        }
        </script>
        <script src="https://maps.googleapis.com/maps/api/js?key=${BuildConfig.MAPS_API_KEY}&callback=initMap" async defer></script>
        </body>
        </html>
    """.trimIndent()

    override fun onDestroyView() {
        _binding?.webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        super.onDestroyView()
        _binding = null
    }
}

```

