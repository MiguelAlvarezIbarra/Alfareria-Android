package com.artesanias.wear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.artesanias.wear.presentation.CompraAlertActivity
import com.artesanias.wear.presentation.ProductoStockItem
import com.artesanias.wear.presentation.StockAlertActivity
import com.artesanias.wear.presentation.StockListActivity
import com.artesanias.wear.presentation.StockResultadoActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * `WearableListenerService` es una clase base de Google Play Services que
 * el sistema instancia automáticamente cuando llega un mensaje del
 * teléfono cuyo "path" coincide con alguno de los `<intent-filter>`
 * declarados para este servicio en el AndroidManifest.xml de este módulo.
 * Es el equivalente, en la dirección teléfono → reloj, de
 * WearableDataListenerService en el módulo de teléfono.
 */
class PhoneMessageListenerService : WearableListenerService() {

    private val TAG = "WearListener"
    private val CHANNEL_ID = "artesanias_wear_channel"
    // Cada notificación necesita un id distinto para no reemplazar a la
    // anterior en la bandeja del sistema; se incrementa en mostrarNotificacion().
    private var notifId = 0

    override fun onCreate() {
        super.onCreate()
        crearCanal()
    }

    /** Se llama automáticamente cada vez que el teléfono manda un mensaje al reloj. */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val datos = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "Mensaje recibido: ${messageEvent.path} -> $datos")

        when (messageEvent.path) {

            // El teléfono avisó que un producto quedó con poco stock.
            // Formato del mensaje: "productoId:nombre:stock".
            "/alerta/stock" -> {
                val partes = datos.split(":")
                if (partes.size >= 3) {
                    val productoId = partes[0].toIntOrNull() ?: -1
                    val nombre = partes[1]
                    val stock = partes[2].toIntOrNull() ?: 0
                    val mensajeVisible = "⚠️ $nombre\nSolo $stock unidades en stock"

                    // Actualiza (o agrega) este producto en la lista en
                    // memoria compartida con StockListActivity, para que
                    // la pantalla "Ajustar Inventario" quede consistente
                    // sin tener que volver a pedirle todo al teléfono.
                    val yaExiste = StockListActivity.productosStockBajo.any { it.id == productoId }
                    if (!yaExiste && productoId != -1) {
                        StockListActivity.productosStockBajo.add(
                            ProductoStockItem(productoId, nombre, stock)
                        )
                    } else if (productoId != -1) {
                        val idx = StockListActivity.productosStockBajo.indexOfFirst { it.id == productoId }
                        if (idx >= 0) {
                            StockListActivity.productosStockBajo[idx] =
                                ProductoStockItem(productoId, nombre, stock)
                        }
                    }

                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(Intent("com.artesanias.wear.STOCK_ACTUALIZADO"))

                    vibrarCorto()

                    val intent = Intent(this, StockAlertActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("productoId", productoId)
                        putExtra("mensaje", mensajeVisible)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        this, productoId, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    mostrarNotificacion("⚠️ Stock Bajo", mensajeVisible, pendingIntent)
                    startActivity(intent)

                } else {
                    Log.w(TAG, "Formato de stock inválido: $datos")
                }
            }

            // Respuesta del teléfono a la solicitud "/stock/lista" (ver
            // StockListActivity): reemplaza toda la lista en memoria con
            // el inventario bajo en stock más reciente. Formato:
            // "id:nombre:stock|id:nombre:stock|..." (vacío si no hay ninguno).
            "/stock/lista/respuesta" -> {
                StockListActivity.productosStockBajo.clear()
                if (datos.isNotBlank()) {
                    datos.split("|").forEach { entry ->
                        val partes = entry.split(":")
                        if (partes.size >= 3) {
                            val id = partes[0].toIntOrNull() ?: return@forEach
                            val nombre = partes[1]
                            val stock = partes[2].toIntOrNull() ?: 0
                            StockListActivity.productosStockBajo.add(
                                ProductoStockItem(id, nombre, stock)
                            )
                        }
                    }
                }
                Log.d(TAG, "Lista de stock actualizada: ${StockListActivity.productosStockBajo.size} productos")
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent("com.artesanias.wear.STOCK_ACTUALIZADO"))
            }

            "/alerta/compra-grande" -> {
                // Formato extendido:
                // "mensaje principal||detalle=nombre:cant:precio|...||stockBajo=id:nombre:stock|..."
                vibrarLargo()

                // Parsear las tres secciones separadas por "||"
                val secciones = datos.split("||")
                val mensajePrincipal = secciones.getOrElse(0) { datos }
                val detalle = secciones.find { it.startsWith("detalle=") }
                    ?.removePrefix("detalle=") ?: ""
                val stockBajo = secciones.find { it.startsWith("stockBajo=") }
                    ?.removePrefix("stockBajo=") ?: ""

                val intent = Intent(this, CompraAlertActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("mensaje", mensajePrincipal)
                    putExtra("detalle", detalle)
                    putExtra("stockBajo", stockBajo)   // lo usará StockResultadoActivity
                }
                val pendingIntent = PendingIntent.getActivity(
                    this, notifId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                mostrarNotificacion("💰 Compra Grande", mensajePrincipal, pendingIntent)
                startActivity(intent)
            }

            "/stock/actualizado" -> {
                mostrarNotificacion("✅ Stock actualizado", datos, null)

                // Abrir pantalla de resultado de stock tras agregar unidades
                // El payload es el mismo "id:nombre:stock|..." que stockBajo
                // Si viene vacío, todo el stock quedó bien
                val intent = Intent(this, StockResultadoActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("stockBajo", "")   // stock actualizado = ya no hay problema
                }
                startActivity(intent)
            }

            "/orden/confirmada" -> {
                mostrarNotificacion("✅ Orden confirmada", datos, null)
            }

            "/ping/pong" -> {
                Log.d(TAG, "Pong recibido del teléfono")
            }
        }
    }

    /**
     * Vibración corta (300ms) para alertas de stock bajo. La rama por
     * versión de Android existe porque `VibratorManager` (la forma actual
     * de pedir el vibrador) solo existe desde Android 12 (S); en
     * versiones anteriores hay que usar la clase `Vibrator` directa, que
     * Google marcó como obsoleta pero sigue siendo la única opción ahí.
     */
    private fun vibrarCorto() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(300)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrando: ${e.message}")
        }
    }

    /** Patrón de vibración largo e intermitente, reservado para la alerta de compra grande. */
    private fun vibrarLargo() {
        try {
            val patron = longArrayOf(0, 400, 200, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(patron, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(patron, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(patron, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrando: ${e.message}")
        }
    }

    /**
     * Publica una notificación del sistema en el reloj. `pendingIntent`
     * (si se da) hace que tocar la notificación abra la Activity
     * correspondiente, igual que tocar la alerta que ya está en pantalla.
     */
    private fun mostrarNotificacion(titulo: String, mensaje: String, pendingIntent: PendingIntent?) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (pendingIntent != null) builder.setContentIntent(pendingIntent)

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId++, builder.build())
    }

    /**
     * Los canales de notificación (`NotificationChannel`) son obligatorios
     * desde Android 8 (Oreo): agrupan notificaciones bajo una categoría
     * que el usuario puede silenciar o ajustar desde Ajustes, sin afectar
     * a otras. Se crea una sola vez, al arrancar el servicio.
     */
    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "Alertas de Artesanías",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de stock y compras grandes"
                enableVibration(true)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(canal)
        }
    }
}