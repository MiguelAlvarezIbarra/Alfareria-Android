package com.artesanias.app.data.remote

import android.util.Log
import com.artesanias.app.data.local.CompraResumen
import com.artesanias.app.data.local.VentaProducto
import com.artesanias.app.data.model.Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * Envía datos a la app de Android TV por un socket TCP (la Wearable Data
 * Layer que usa WearDataSender es exclusiva de Wear OS, Android TV no la
 * soporta). Ver TvServer.kt en el módulo tv para el protocolo y el puerto.
 *
 * HOST_DEFAULT es "127.0.0.1" a propósito: se probó primero con la IP WiFi
 * de la PC en la red local, pero el router bloquea la conexión iniciada
 * desde el celular hacia la PC (aislamiento de clientes WiFi) aunque el
 * sentido contrario sí funciona — no hay forma de arreglar eso desde la
 * app o desde el firewall de Windows, así que en vez de WiFi se usa el
 * cable USB con `adb reverse`, que crea un túnel 127.0.0.1:8765 (celular)
 * -> 127.0.0.1:8765 (PC) sin pasar por el router:
 *   adb -s <celular> reverse tcp:8765 tcp:8765
 *   adb -s <tv> forward tcp:8765 tcp:8765
 *   netsh interface portproxy add v4tov4 listenport=8765 listenaddress=0.0.0.0 connectport=8765 connectaddress=127.0.0.1
 * (el portproxy ya no es necesario para este flujo por USB, pero se deja
 * documentado por si se vuelve a usar WiFi en una red sin aislamiento).
 * El celular debe permanecer conectado por USB para que esto funcione.
 *
 * `@Singleton` + `@Inject constructor()`: le dice a Hilt (el framework de
 * inyección de dependencias) que exista una sola instancia de esta clase
 * en toda la app, compartida por quien la pida en su constructor (aquí no
 * se necesita porque no tiene estado que valga la pena compartir, pero es
 * el patrón consistente con el resto de los repositorios/servicios).
 */
@Singleton
class TvDataSender @Inject constructor() {
    private val TAG = "TvDataSender"
    private val diasSemana = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

    companion object {
        const val HOST_DEFAULT = "127.0.0.1"
        const val PUERTO = 8766
    }

    private suspend fun enviar(json: JSONObject) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Intentando enviar a $HOST_DEFAULT:$PUERTO -> ${json.optString("tipo")}")
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST_DEFAULT, PUERTO), 1500)
                socket.getOutputStream().write((json.toString() + "\n").toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                // Al ir por USB (adb reverse -> adb forward, dos saltos
                // encadenados) hay más latencia que en loopback puro; sin
                // esta espera el socket se cierra antes de que el dato
                // alcance a cruzar el segundo salto hacia el emulador de TV.
                delay(300)
            }
            Log.d(TAG, "Enviado a la TV correctamente: ${json.optString("tipo")}")
        } catch (e: Exception) {
            // La TV es opcional: si no está conectada, no debe afectar el flujo normal de la app.
            Log.w(TAG, "No se pudo enviar a la TV: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Manda el catálogo completo (solo productos activos) a la Pantalla 1 de la TV. */
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

    /** Manda el top de productos más vendidos para la gráfica de barras de la Pantalla 2. */
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

    /** Manda la tabla de compras de los últimos 7 días para la Pantalla 2. */
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

    /** Dispara la alerta de "compra grande" que la TV muestra como overlay sobre cualquier pantalla. */
    suspend fun enviarCompraGrande(producto: String, monto: Double) {
        enviar(JSONObject().apply {
            put("tipo", "compra_grande")
            put("producto", producto)
            put("monto", monto)
        })
    }
}
