package com.artesanias.app.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import com.artesanias.app.data.model.RolUsuario
import com.artesanias.app.data.model.Usuario
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.security.MessageDigest

// ───────────── HASH UTIL ─────────────
object HashUtil {
    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

// ───────────── SESSION MANAGER ─────────────
class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("artesanias_session", Context.MODE_PRIVATE)

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_ROL = "user_rol"
        const val KEY_LOGGED_IN = "logged_in"
        const val NO_SESSION = -1
    }

    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_LOGGED_IN, false)
    val userId: Int get() = prefs.getInt(KEY_USER_ID, NO_SESSION)
    val userName: String get() = prefs.getString(KEY_USER_NAME, "") ?: ""
    val userEmail: String get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    val userRol: RolUsuario get() {
        val rolStr = prefs.getString(KEY_USER_ROL, RolUsuario.CLIENTE.name) ?: RolUsuario.CLIENTE.name
        return RolUsuario.valueOf(rolStr)
    }
    val isAdmin: Boolean get() = userRol == RolUsuario.ADMIN

    fun guardarSesion(usuario: Usuario) {
        prefs.edit().apply {
            putBoolean(KEY_LOGGED_IN, true)
            putInt(KEY_USER_ID, usuario.id)
            putString(KEY_USER_NAME, "${usuario.nombre} ${usuario.apellido}")
            putString(KEY_USER_EMAIL, usuario.email)
            putString(KEY_USER_ROL, usuario.rol.name)
            apply()
        }
    }

    fun cerrarSesion() {
        prefs.edit().clear().apply()
    }
}

// ───────────── QR UTIL ─────────────
object QRUtil {
    // Genera QR con el nodeId del teléfono para conectar Wear OS
    fun generarQRParaWear(nodeId: String, tamano: Int = 512): Bitmap {
        val contenido = "artesanias://wear/connect?nodeId=$nodeId"
        return generarQR(contenido, tamano)
    }

    fun generarQR(contenido: String, tamano: Int = 512): Bitmap {
        val writer = MultiFormatWriter()
        val bitMatrix: BitMatrix = writer.encode(contenido, BarcodeFormat.QR_CODE, tamano, tamano)
        val bmp = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.RGB_565)
        for (x in 0 until tamano) {
            for (y in 0 until tamano) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bmp
    }

    // Parsear QR escaneado
    fun parsearQRWear(qrContent: String): String? {
        return if (qrContent.startsWith("artesanias://wear/connect?nodeId=")) {
            qrContent.removePrefix("artesanias://wear/connect?nodeId=")
        } else null
    }
}

// ───────────── EXTENSIONES ÚTILES ─────────────
fun Double.formatearPrecio(): String = "\$${String.format("%,.2f", this)} MXN"

fun String.isEmailValido(): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

fun String.isPasswordSeguro(): Boolean = length >= 6
