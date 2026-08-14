package com.artesanias.wear.presentation

import androidx.activity.ComponentActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableRecyclerView
import com.artesanias.wear.R
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ─────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────
/**
 * Pantalla principal del reloj. Usa `ComponentActivity` (no
 * `AppCompatActivity`/Compose) porque el layout es un XML clásico simple;
 * este módulo no usa Navigation Component, cada pantalla es una Activity
 * separada que se abre con `startActivity`, ya sea desde aquí o desde
 * PhoneMessageListenerService al recibir un mensaje del teléfono.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_wear)

        // Desde Android 13 (Tiramisu) las notificaciones requieren permiso
        // explícito en tiempo de ejecución; en versiones anteriores se
        // conceden solo con declararlas en el manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    .launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val tvEstado = findViewById<TextView>(R.id.tv_estado)
        val btnPing = findViewById<Button>(R.id.btn_ping)
        val btnQR = findViewById<Button>(R.id.btn_mostrar_qr)
        val btnInventario = findViewById<Button>(R.id.btn_ajustar_inventario)

        tvEstado.text = "Artesanías Wear\nEsperando conexión..."

        // Botón de prueba de conexión: pide los nodos (teléfonos)
        // emparejados vía Wearable Data Layer y, si hay alguno, le manda
        // un mensaje "/ping" para confirmar que la comunicación funciona.
        btnPing.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val nodes = Wearable.getNodeClient(this@MainActivity)
                        .connectedNodes.await()
                    if (nodes.isNotEmpty()) {
                        Wearable.getMessageClient(this@MainActivity)
                            .sendMessage(nodes[0].id, "/ping", "hola".toByteArray()).await()
                        tvEstado.text = "✅ Conectado con\n${nodes[0].displayName}"
                    } else {
                        tvEstado.text = "❌ Sin conexión\ncon el teléfono"
                    }
                } catch (e: Exception) {
                    tvEstado.text = "Error: ${e.message}"
                }
            }
        }

        btnQR.setOnClickListener {
            tvEstado.text = "Abre la app\nen el teléfono\ny usa Cámara > QR"
        }

        btnInventario.setOnClickListener {
            startActivity(Intent(this, StockListActivity::class.java))
        }
    }
}

// ─────────────────────────────────────────────
// LISTA DE PRODUCTOS CON STOCK BAJO
// ─────────────────────────────────────────────
/**
 * Pantalla "Ajustar Inventario": lista los productos con poco stock que el
 * teléfono ha ido reportando. Al tocar uno abre StockAlertActivity para
 * pedir más unidades.
 */
class StockListActivity : ComponentActivity() {

    companion object {
        // Lista en memoria compartida entre esta Activity y
        // PhoneMessageListenerService: el servicio la llena/actualiza
        // cuando llegan mensajes del teléfono, y esta Activity solo la
        // lee y la dibuja. Vive mientras el proceso del reloj esté vivo
        // (no sobrevive un reinicio del sistema, pero no lo necesita).
        val productosStockBajo = mutableListOf<ProductoStockItem>()
    }

    private lateinit var adapter: StockProductoAdapter
    private lateinit var tvSinProductos: TextView
    private lateinit var rv: WearableRecyclerView

    // Se dispara cuando PhoneMessageListenerService modifica
    // productosStockBajo y avisa con un broadcast local, para refrescar
    // la lista sin tener que volver a pedirle nada al teléfono.
    private val stockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            actualizarVista()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_list)

        rv = findViewById(R.id.rv_productos_stock)
        tvSinProductos = findViewById(R.id.tv_sin_productos)

        adapter = StockProductoAdapter(productosStockBajo) { item ->
            val intent = Intent(this, StockAlertActivity::class.java).apply {
                putExtra("productoId", item.id)
                putExtra("mensaje", "⚠️ ${item.nombre}\nSolo ${item.stock} unidades en stock")
            }
            startActivity(intent)
        }

        rv.apply {
            // isEdgeItemsCenteringEnabled: comportamiento típico de listas
            // en reloj circular/redondo, centra el primer y último item
            // en vez de dejarlos pegados al borde de la pantalla.
            isEdgeItemsCenteringEnabled = true
            layoutManager = LinearLayoutManager(this@StockListActivity)
            adapter = this@StockListActivity.adapter
        }

        solicitarListaStock()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            stockReceiver,
            IntentFilter("com.artesanias.wear.STOCK_ACTUALIZADO")
        )
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stockReceiver)
    }

    /** Pide al teléfono la lista completa de productos con stock bajo (mensaje "/stock/lista"). */
    private fun solicitarListaStock() {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@StockListActivity)
                    .connectedNodes.await()

                if (nodes.isEmpty()) {
                    tvSinProductos.text = "❌ Sin conexión\ncon el teléfono"
                    tvSinProductos.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                    return@launch
                }

                tvSinProductos.text = "⏳ Cargando inventario..."
                tvSinProductos.visibility = View.VISIBLE
                rv.visibility = View.GONE

                nodes.forEach { node ->
                    Wearable.getMessageClient(this@StockListActivity)
                        .sendMessage(node.id, "/stock/lista", "solicitar".toByteArray())
                        .await()
                }

                // La respuesta llega asíncrona por PhoneMessageListenerService
                // ("/stock/lista/respuesta"); si ya había datos en memoria de
                // antes, se muestran de inmediato mientras llega la respuesta fresca.
                if (productosStockBajo.isNotEmpty()) actualizarVista()

            } catch (e: Exception) {
                tvSinProductos.text = "Error: ${e.message}"
                tvSinProductos.visibility = View.VISIBLE
                rv.visibility = View.GONE
            }
        }
    }

    private fun actualizarVista() {
        if (productosStockBajo.isEmpty()) {
            tvSinProductos.text = "✅ Todo el inventario\nestá en orden"
            tvSinProductos.visibility = View.VISIBLE
            rv.visibility = View.GONE
        } else {
            tvSinProductos.visibility = View.GONE
            rv.visibility = View.VISIBLE
            adapter.notifyDataSetChanged()
        }
    }

    /** Punto de entrada público usado por PhoneMessageListenerService para refrescar la vista si esta Activity está visible. */
    fun refrescarLista() = actualizarVista()
}

// ─────────────────────────────────────────────
// DATA CLASS para item de stock
// ─────────────────────────────────────────────
/** Representación mínima de un producto con stock bajo, tal como la maneja el reloj (sin el resto de campos del modelo completo del teléfono). */
data class ProductoStockItem(
    val id: Int,
    val nombre: String,
    val stock: Int
)

// ─────────────────────────────────────────────
// ADAPTER de la lista
// ─────────────────────────────────────────────
/** Adapter de RecyclerView clásico (no ListAdapter/DiffUtil) para la lista de productos con stock bajo; la lista es corta y se reconstruye completa con notifyDataSetChanged() cada vez. */
class StockProductoAdapter(
    private val items: List<ProductoStockItem>,
    private val onClick: (ProductoStockItem) -> Unit
) : RecyclerView.Adapter<StockProductoAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tv_nombre_producto)
        val tvStock: TextView = view.findViewById(R.id.tv_stock_actual)
        val viewIndicador: View = view.findViewById(R.id.view_indicador)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto_stock, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvNombre.text = item.nombre

        // Rojo para "sin stock" (0 unidades), naranja para "stock bajo"
        // (>0 pero por debajo del umbral que ya filtró el teléfono).
        if (item.stock == 0) {
            holder.tvStock.text = "⛔ Sin stock"
            holder.tvStock.setTextColor(Color.parseColor("#F44336"))
            holder.viewIndicador.setBackgroundColor(Color.parseColor("#F44336"))
            holder.tvNombre.setTextColor(Color.parseColor("#FF8A80"))
        } else {
            holder.tvStock.text = "⚠️ ${item.stock} unidades"
            holder.tvStock.setTextColor(Color.parseColor("#FF7043"))
            holder.viewIndicador.setBackgroundColor(Color.parseColor("#FF7043"))
            holder.tvNombre.setTextColor(Color.parseColor("#F5E6D3"))
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}

// ─────────────────────────────────────────────
// ALERTA DE STOCK BAJO (individual)
// ─────────────────────────────────────────────
/** Pantalla que se abre automáticamente (desde PhoneMessageListenerService) o al tocar un item en StockListActivity, para pedir que se agreguen unidades a un producto específico. */
class StockAlertActivity : ComponentActivity() {

    private var productoId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_alert)

        productoId = intent.getIntExtra("productoId", -1)
        val mensaje = intent.getStringExtra("mensaje") ?: "Stock bajo"

        val tvMensaje = findViewById<TextView>(R.id.tv_mensaje)
        val etCantidad = findViewById<EditText>(R.id.et_cantidad)
        val btnAgregar = findViewById<Button>(R.id.btn_agregar_stock)
        val btnCancelar = findViewById<Button>(R.id.btn_cancelar)

        tvMensaje.text = mensaje

        btnAgregar.setOnClickListener {
            val cantidad = etCantidad.text.toString().trim().toIntOrNull() ?: 0
            if (cantidad > 0 && productoId != -1) {
                enviarComandoStock(productoId, cantidad)
            } else {
                Toast.makeText(this, "Ingresa una cantidad válida", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancelar.setOnClickListener { finish() }
    }

    /** Envía al teléfono la orden de agregar `cantidad` unidades al producto `productoId` (mensaje "/stock/agregar"). */
    private fun enviarComandoStock(productoId: Int, cantidad: Int) {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@StockAlertActivity)
                    .connectedNodes.await()

                if (nodes.isEmpty()) {
                    Toast.makeText(this@StockAlertActivity,
                        "Sin conexión con el teléfono", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                nodes.forEach { node ->
                    Wearable.getMessageClient(this@StockAlertActivity)
                        .sendMessage(
                            node.id,
                            "/stock/agregar",
                            "$productoId:$cantidad".toByteArray()
                        ).await()
                }

                // Optimista: se quita de la lista local antes de que el
                // teléfono confirme, para que StockListActivity no siga
                // mostrando un producto que ya se está resolviendo.
                StockListActivity.productosStockBajo.removeAll { it.id == productoId }

                Toast.makeText(
                    this@StockAlertActivity,
                    "✅ Solicitud enviada: +$cantidad unidades",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@StockAlertActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

// ─────────────────────────────────────────────
// ALERTA DE COMPRA GRANDE — ACTUALIZADA
// Ahora recibe el detalle de productos comprados
// y al confirmar verifica el stock resultante
// ─────────────────────────────────────────────
/**
 * Pantalla de alerta cuando el cliente hace una compra grande ($500+ MXN).
 * Tiene dos modos según el mensaje recibido:
 *  - Aviso informativo (compra $500-$1000): solo se muestra, botón "Entendido".
 *  - Confirmación requerida (compra >$1000): el reloj debe confirmar la
 *    orden explícitamente antes de que se procese del todo.
 */
class CompraAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compra_alert)

        val mensaje = intent.getStringExtra("mensaje") ?: "Nueva compra grande"
        val esConfirmacion = mensaje.contains("confirmación") || mensaje.contains("confirmar")

        // Detalle de productos: viene como "nombre:cant:precio|nombre:cant:precio|..."
        // Lo envía el teléfono junto con el mensaje de compra grande
        val detalle = intent.getStringExtra("detalle") ?: ""

        val tvMensaje = findViewById<TextView>(R.id.tv_mensaje)
        val tvTitulo = findViewById<TextView>(R.id.tv_titulo)
        val tvDetalle = findViewById<TextView>(R.id.tv_detalle_productos)
        val btnConfirmar = findViewById<Button>(R.id.btn_confirmar)
        val btnDesestimar = findViewById<Button>(R.id.btn_desestimar)
        val tvAviso = findViewById<TextView>(R.id.tv_aviso_confirmacion)

        tvMensaje.text = mensaje
        tvTitulo.text = if (esConfirmacion) "🔔 Confirmar compra" else "💰 Compra grande"

        // Mostrar detalle de productos si viene
        if (detalle.isNotBlank()) {
            val lineas = detalle.split("|").mapNotNull { entry ->
                val p = entry.split(":")
                if (p.size >= 3) "• ${p[0]}  x${p[1]}  $${p[2]}" else null
            }
            tvDetalle.text = lineas.joinToString("\n")
            tvDetalle.visibility = View.VISIBLE
        } else {
            tvDetalle.visibility = View.GONE
        }

        if (esConfirmacion) tvAviso.visibility = View.VISIBLE

        btnConfirmar.text = if (esConfirmacion) "Confirmar" else "Entendido"

        btnConfirmar.setOnClickListener {
            if (esConfirmacion) {
                val ordenId = extraerOrdenId(mensaje)
                if (ordenId > 0) confirmarOrden(ordenId)
                else { Toast.makeText(this, "Compra registrada", Toast.LENGTH_SHORT).show(); finish() }
            } else {
                finish()
            }
        }

        btnDesestimar.setOnClickListener { finish() }
    }

    /** Manda al teléfono el mensaje "/orden/confirmar" con el id de la orden para que la procese del todo. */
    private fun confirmarOrden(ordenId: Int) {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@CompraAlertActivity)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@CompraAlertActivity)
                        .sendMessage(node.id, "/orden/confirmar",
                            ordenId.toString().toByteArray()).await()
                }
                Toast.makeText(this@CompraAlertActivity,
                    "✅ Orden #$ordenId confirmada", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@CompraAlertActivity,
                    "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Extrae el número de orden del texto del mensaje (formato "...#123..."), o -1 si no lo encuentra. */
    private fun extraerOrdenId(mensaje: String): Int {
        return try {
            Regex("#(\\d+)").find(mensaje)?.groupValues?.get(1)?.toInt() ?: -1
        } catch (e: Exception) { -1 }
    }
}

// ─────────────────────────────────────────────
// RESULTADO DE STOCK TRAS COMPRA
// Se abre automáticamente después de confirmar
// una compra grande. Muestra si el stock quedó
// suficiente o bajo, y en ese caso ofrece ir
// a agregar más unidades.
// ─────────────────────────────────────────────
/** Pantalla de cierre del flujo de compra grande: informa si el stock quedó suficiente o si algún producto quedó bajo, con acceso directo a StockAlertActivity para reabastecer. */
class StockResultadoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_resultado)

        // stockBajo viene como "id:nombre:stock|id:nombre:stock|..."
        // Si viene vacío = todo el stock quedó suficiente
        val stockBajoRaw = intent.getStringExtra("stockBajo") ?: ""

        val tvTitulo = findViewById<TextView>(R.id.tv_resultado_titulo)
        val tvIcono = findViewById<TextView>(R.id.tv_resultado_icono)
        val tvDetalle = findViewById<TextView>(R.id.tv_resultado_detalle)
        val btnAceptar = findViewById<Button>(R.id.btn_resultado_aceptar)
        val btnCancelar = findViewById<Button>(R.id.btn_resultado_cancelar)

        if (stockBajoRaw.isBlank()) {
            // ── Stock suficiente ──
            tvIcono.text = "✅"
            tvTitulo.text = "Stock suficiente"
            tvDetalle.visibility = View.GONE
            btnAceptar.text = "Cerrar"
            btnCancelar.visibility = View.GONE

            btnAceptar.setOnClickListener { finish() }

        } else {
            // ── Stock bajo en uno o más productos ──
            val productosConStockBajo = stockBajoRaw.split("|").mapNotNull { entry ->
                val p = entry.split(":")
                if (p.size >= 3) {
                    val id = p[0].toIntOrNull() ?: return@mapNotNull null
                    val nombre = p[1]
                    val stock = p[2].toIntOrNull() ?: 0
                    ProductoStockItem(id, nombre, stock)
                } else null
            }

            tvIcono.text = "⚠️"
            tvTitulo.text = "Stock bajo"

            val lineas = productosConStockBajo.joinToString("\n") { p ->
                "• ${p.nombre}: ${p.stock} uds"
            }
            tvDetalle.text = "Fabricar más producto?\n\n$lineas"
            tvDetalle.visibility = View.VISIBLE

            btnAceptar.text = "Aceptar → Agregar"
            btnCancelar.visibility = View.VISIBLE
            btnCancelar.text = "Cancelar"

            btnAceptar.setOnClickListener {
                // Abrir StockAlertActivity con el primer producto con stock bajo
                // Si hay varios, el usuario puede regresar y el reloj los mostrará
                // todos en StockListActivity
                val primero = productosConStockBajo.first()
                val intent = Intent(this, StockAlertActivity::class.java).apply {
                    putExtra("productoId", primero.id)
                    putExtra("mensaje", "⚠️ ${primero.nombre}\nSolo ${primero.stock} unidades")
                }
                startActivity(intent)
                finish()
            }

            btnCancelar.setOnClickListener {
                // Volver a la pantalla principal del reloj
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
        }
    }
}
