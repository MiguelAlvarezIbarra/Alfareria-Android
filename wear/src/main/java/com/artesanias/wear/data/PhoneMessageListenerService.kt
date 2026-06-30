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

class PhoneMessageListenerService : WearableListenerService() {

    private val TAG = "WearListener"
    private val CHANNEL_ID = "artesanias_wear_channel"
    private var notifId = 0

    override fun onCreate() {
        super.onCreate()
        crearCanal()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val datos = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "Mensaje recibido: ${messageEvent.path} -> $datos")

        when (messageEvent.path) {

            "/alerta/stock" -> {
                val partes = datos.split(":")
                if (partes.size >= 3) {
                    val productoId = partes[0].toIntOrNull() ?: -1
                    val nombre = partes[1]
                    val stock = partes[2].toIntOrNull() ?: 0
                    val mensajeVisible = "⚠️ $nombre\nSolo $stock unidades en stock"

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