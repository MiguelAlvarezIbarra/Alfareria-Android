package com.artesanias.app.data.remote

import android.content.Context
import android.util.Log
import com.artesanias.app.data.local.ArtesaniasDatabase
import com.google.android.gms.wearable.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// ─── Envío de mensajes al Wear OS ───
// Implementa la navegación phone-watch: cada "path" enviado aquí es escuchado
// por PhoneMessageListenerService en el módulo wear, que abre la Activity
// correspondiente (StockAlertActivity, CompraAlertActivity, etc.) usando
// el Wearable Data Layer API de Google Play Services.
/**
 * `@ApplicationContext`: calificador de Hilt que le pide específicamente el
 * Context de la aplicación (no el de una Activity), correcto aquí porque
 * esta clase es un singleton que vive más que cualquier pantalla.
 */
@Singleton
class WearDataSender @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "WearDataSender"

    /**
     * Envía un mensaje de texto simple al reloj por el Wearable Data
     * Layer API. `Wearable.getNodeClient(...).connectedNodes` devuelve los
     * dispositivos Wear OS actualmente emparejados y conectados (los
     * "nodos"); si no hay ninguno, no tiene caso intentar enviar. `path`
     * es la ruta que identifica el tipo de mensaje (p.ej.
     * "/alerta/stock"), que `PhoneMessageListenerService` en el reloj usa
     * como un `when` para decidir qué hacer con el mensaje.
     */
    suspend fun enviarMensaje(path: String, mensaje: String) {
        try {
            val nodes = Wearable.getNodeClient(context)
                .connectedNodes
                .await()

            if (nodes.isEmpty()) {
                Log.w(TAG, "Sin nodos conectados para enviar: $path")
                return
            }

            // Normalmente hay un solo reloj emparejado, pero se recorre
            // por si hubiera más de uno.
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, path, mensaje.toByteArray(Charsets.UTF_8))
                    .await()
                Log.d(TAG, "Mensaje enviado a ${node.displayName}: $path -> $mensaje")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje Wear: ${e.message}")
        }
    }

    /**
     * Variante que usa el DataClient en vez del MessageClient: en lugar de
     * un mensaje puntual, deja un valor persistente ("DataItem") que
     * Google Play Services sincroniza con el reloj incluso si en ese
     * momento no está conectado (se entrega en cuanto se reconecta).
     * `.setUrgent()` le pide al sistema que lo entregue lo antes posible
     * en vez de esperar a agrupar varios envíos.
     */
    suspend fun enviarDato(path: String, clave: String, valor: String) {
        try {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putString(clave, valor)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
                .await()
            Log.d(TAG, "Dato enviado: $path/$clave = $valor")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando dato Wear: ${e.message}")
        }
    }
}

// ─── Listener de mensajes DESDE el Wear OS ───
/**
 * `WearableListenerService` es una clase base de Google Play Services que
 * el sistema instancia automáticamente (no hace falta arrancarla a mano)
 * cada vez que llega un mensaje cuyo "path" coincide con alguno de los
 * `<intent-filter>` declarados para este servicio en AndroidManifest.xml.
 * Es el equivalente, en la dirección reloj → teléfono, de
 * PhoneMessageListenerService en el módulo wear.
 */
class WearableDataListenerService : WearableListenerService() {

    private val TAG = "WearListener"

    // Alcance de corrutinas propio del Service: SupervisorJob evita que si
    // una tarea falla, cancele a las demás; se cancela por completo en
    // onDestroy para no dejar corrutinas huérfanas corriendo de fondo.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /** Se llama automáticamente cada vez que el reloj manda un mensaje. */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val datos = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "Mensaje recibido del reloj: ${messageEvent.path} -> $datos")

        when (messageEvent.path) {

            // El reloj pide la lista de productos con stock bajo (pantalla
            // "Ajustar Inventario"): se responde de forma asíncrona porque
            // consultar la base de datos es una operación suspend.
            "/stock/lista" -> {
                val nodeId = messageEvent.sourceNodeId
                scope.launch {
                    responderListaStock(nodeId)
                }
            }

            // El reloj confirmó agregar stock a un producto. Formato del
            // mensaje: "productoId:cantidad".
            "/stock/agregar" -> {
                val partes = datos.split(":")
                if (partes.size == 2) {
                    val productoId = partes[0].toIntOrNull() ?: return
                    val cantidad = partes[1].toIntOrNull() ?: return
                    scope.launch {
                        agregarStockDirecto(productoId, cantidad)
                    }
                }
            }

            // El reloj confirmó una compra que requería aprobación
            // (> $1000). Se reenvía como broadcast local para que la
            // Activity del teléfono que esté abierta en ese momento pueda
            // reaccionar (p.ej. refrescar la lista de órdenes).
            "/orden/confirmar" -> {
                val ordenId = datos.toIntOrNull() ?: return
                val intent = android.content.Intent("com.artesanias.app.CONFIRMAR_ORDEN").apply {
                    putExtra("ordenId", ordenId)
                }
                sendBroadcast(intent)
            }

            "/ping" -> {
                Log.d(TAG, "Ping recibido del reloj")
            }
        }
    }

    /** Responde a "/stock/lista" con el catálogo bajo en stock, en formato "id:nombre:stock|...". */
    private suspend fun responderListaStock(nodeId: String) {
        try {
            // getInstance() es el método correcto según ArtesaniasDatabase
            val db = ArtesaniasDatabase.getInstance(applicationContext)
            val productos = db.productoDao().getProductosConStockBajoSync()

            val payload = if (productos.isEmpty()) {
                ""
            } else {
                productos.joinToString("|") { p -> "${p.id}:${p.nombre}:${p.stock}" }
            }

            Log.d(TAG, "Enviando lista de stock al reloj: ${productos.size} productos -> $payload")

            Wearable.getMessageClient(applicationContext)
                .sendMessage(nodeId, "/stock/lista/respuesta", payload.toByteArray())
                .await()

        } catch (e: Exception) {
            Log.e(TAG, "Error respondiendo lista de stock: ${e.message}")
        }
    }

    /** Aplica el "+cantidad" de stock que el reloj pidió y le confirma de vuelta. */
    private suspend fun agregarStockDirecto(productoId: Int, cantidad: Int) {
        try {
            val db = ArtesaniasDatabase.getInstance(applicationContext)
            db.productoDao().agregarStock(productoId, cantidad)
            Log.d(TAG, "Stock agregado directo: producto=$productoId, cantidad=$cantidad")

            val nodes = Wearable.getNodeClient(applicationContext).connectedNodes.await()
            nodes.forEach { node ->
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(
                        node.id,
                        "/stock/actualizado",
                        "Producto ID $productoId: +$cantidad unidades".toByteArray()
                    ).await()
            }

            // Broadcast para refrescar UI del teléfono si está abierta
            val intent = android.content.Intent("com.artesanias.app.AGREGAR_STOCK").apply {
                putExtra("productoId", productoId)
                putExtra("cantidad", cantidad)
            }
            sendBroadcast(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Error agregando stock directo: ${e.message}")
        }
    }
}
