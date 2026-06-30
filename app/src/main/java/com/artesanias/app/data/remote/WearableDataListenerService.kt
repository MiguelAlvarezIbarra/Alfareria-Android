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
@Singleton
class WearDataSender @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "WearDataSender"

    suspend fun enviarMensaje(path: String, mensaje: String) {
        try {
            val nodes = Wearable.getNodeClient(context)
                .connectedNodes
                .await()

            if (nodes.isEmpty()) {
                Log.w(TAG, "Sin nodos conectados para enviar: $path")
                return
            }

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
class WearableDataListenerService : WearableListenerService() {

    private val TAG = "WearListener"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val datos = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "Mensaje recibido del reloj: ${messageEvent.path} -> $datos")

        when (messageEvent.path) {

            "/stock/lista" -> {
                val nodeId = messageEvent.sourceNodeId
                scope.launch {
                    responderListaStock(nodeId)
                }
            }

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