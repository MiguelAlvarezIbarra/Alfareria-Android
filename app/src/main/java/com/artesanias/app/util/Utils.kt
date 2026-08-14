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
/** Convierte una contraseña en su hash SHA-256 (en hexadecimal) para nunca guardarla en texto plano. */
object HashUtil {
    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        // "%02x" formatea cada byte como 2 dígitos hexadecimales (00-ff);
        // unidos, dan la representación de texto habitual de un hash.
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

// ───────────── SESSION MANAGER ─────────────
/**
 * Guarda la sesión activa (quién inició sesión y con qué rol) en
 * SharedPreferences, el almacén clave-valor persistente de Android — a
 * diferencia de Room, no es para datos relacionales, sino para unos pocos
 * valores simples que sobreviven a que se cierre la app. `MODE_PRIVATE`
 * significa que solo esta app puede leer este archivo de preferencias.
 */
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

    // Todas son propiedades calculadas (`get()`): leen SharedPreferences
    // en cada acceso, así que siempre reflejan el estado más reciente (no
    // hay que "refrescar" nada manualmente tras guardarSesion/cerrarSesion).
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

    /** Borra toda la sesión guardada (equivalente a "cerrar sesión"). */
    fun cerrarSesion() {
        prefs.edit().clear().apply()
    }
}

// ───────────── QR UTIL ─────────────
/** Generación y lectura de códigos QR (con la librería ZXing) para emparejar el reloj Wear OS. */
object QRUtil {
    // Genera QR con el nodeId del teléfono para conectar Wear OS
    fun generarQRParaWear(nodeId: String, tamano: Int = 512): Bitmap {
        val contenido = "artesanias://wear/connect?nodeId=$nodeId"
        return generarQR(contenido, tamano)
    }

    /**
     * Codifica cualquier texto como una imagen de código QR. `BitMatrix`
     * es la cuadrícula de bits (true = módulo negro, false = blanco) que
     * ZXing genera a partir del texto; aquí se recorre pixel por pixel
     * para convertirla en un `Bitmap` real que se pueda mostrar en un
     * ImageView.
     */
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
// Funciones de extensión: le agregan un método a un tipo que ya existe
// (Double, String) sin tener que heredar de él ni modificar su código
// fuente. Se llaman igual que un método normal: `total.formatearPrecio()`.

/** Formatea un monto como precio en pesos mexicanos, p.ej. `1234.5` → `"$1,234.50 MXN"`. */
fun Double.formatearPrecio(): String = "\$${String.format("%,.2f", this)} MXN"

/** Valida el formato de un correo usando el patrón estándar de Android (`Patterns.EMAIL_ADDRESS`). */
fun String.isEmailValido(): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

/** Regla mínima de seguridad de contraseña para este proyecto: al menos 6 caracteres. */
fun String.isPasswordSeguro(): Boolean = length >= 6
