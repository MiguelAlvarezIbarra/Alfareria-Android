package com.artesanias.app.data.remote

import android.util.Log
import com.artesanias.app.data.local.CompraResumen
import com.artesanias.app.data.local.VentaProducto
import com.artesanias.app.data.model.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envía datos a la app de Android TV por un socket TCP en la red local
 * (la Wearable Data Layer que usa WearDataSender es exclusiva de Wear OS,
 * Android TV no la soporta). Ver TvServer.kt en el módulo tv para el
 * protocolo y el puerto.
 *
 * En emuladores, la IP por defecto (10.0.2.2) es el alias estándar del host
 * desde dentro del emulador del teléfono; el puerto de la TV se expone al
 * host con `adb -s <tv> forward tcp:8765 tcp:8765`.
 */
@Singleton
class TvDataSender @Inject constructor() {
    private val TAG = "TvDataSender"
    private val diasSemana = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

    companion object {
        const val HOST_DEFAULT = "10.0.2.2"
        const val PUERTO = 8765
    }

    private suspend fun enviar(json: JSONObject) = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST_DEFAULT, PUERTO), 1500)
                socket.getOutputStream().write((json.toString() + "\n").toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }
        } catch (e: Exception) {
            // La TV es opcional: si no está conectada, no debe afectar el flujo normal de la app.
            Log.w(TAG, "No se pudo enviar a la TV: ${e.message}")
        }
    }

    suspend fun enviarCatalogo(productos: List<Producto>) {
        val items = JSONArray()
        productos.filter { it.activo }.forEach { p ->
            items.put(JSONObject().apply {
                put("nombre", p.nombre)
                put("precio", p.precio)
                put("stock", p.stock)
                put("categoria", "")
            })
        }
        enviar(JSONObject().apply {
            put("tipo", "productos")
            put("items", items)
        })
    }

    suspend fun enviarMasVendidos(items: List<VentaProducto>) {
        val arr = JSONArray()
        items.forEach { v ->
            arr.put(JSONObject().apply {
                put("nombre", v.nombre)
                put("cantidad", v.cantidad)
            })
        }
        enviar(JSONObject().apply {
            put("tipo", "mas_vendidos")
            put("items", arr)
        })
    }

    suspend fun enviarComprasSemana(items: List<CompraResumen>) {
        val formatoDia = SimpleDateFormat("EEE", Locale("es", "MX"))
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(JSONObject().apply {
                put("fecha", formatoDia.format(c.fecha).replaceFirstChar { it.uppercase() })
                put("cliente", c.cliente)
                put("total", c.total)
            })
        }
        enviar(JSONObject().apply {
            put("tipo", "compras_semana")
            put("items", arr)
        })
    }

    suspend fun enviarCompraGrande(producto: String, monto: Double) {
        enviar(JSONObject().apply {
            put("tipo", "compra_grande")
            put("producto", producto)
            put("monto", monto)
        })
    }
}
