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
    const val PUERTO = 8765
    private const val TAG = "TvServer"

    private var iniciado = false

    fun iniciar(scope: CoroutineScope) {
        if (iniciado) return
        iniciado = true
        scope.launch(Dispatchers.IO) {
            try {
                ServerSocket(PUERTO).use { serverSocket ->
                    Log.i(TAG, "Escuchando en puerto $PUERTO")
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
