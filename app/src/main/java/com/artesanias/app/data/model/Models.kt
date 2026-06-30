package com.artesanias.app.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

// ─────────────────────────────────────────────
// USUARIO
// ─────────────────────────────────────────────
@Parcelize
@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val apellido: String,
    val email: String,
    val passwordHash: String,
    val rol: RolUsuario = RolUsuario.CLIENTE,
    val telefono: String = "",
    val activo: Boolean = true,
    val fechaRegistro: Long = System.currentTimeMillis()
) : Parcelable

enum class RolUsuario { ADMIN, CLIENTE }

// ─────────────────────────────────────────────
// CATEGORÍA
// ─────────────────────────────────────────────
@Parcelize
@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val descripcion: String = "",
    val imagenUrl: String = ""
) : Parcelable

// ─────────────────────────────────────────────
// PRODUCTO
// ─────────────────────────────────────────────
@Parcelize
@Entity(
    tableName = "productos",
    foreignKeys = [ForeignKey(
        entity = Categoria::class,
        parentColumns = ["id"],
        childColumns = ["categoriaId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoriaId")]
)
data class Producto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val descripcion: String,
    val precio: Double,
    val stock: Int,
    val categoriaId: Int? = null,
    val imagenPath: String = "",   // ruta local de foto tomada con cámara
    val tecnica: String = "",       // Talavera, Barro negro, etc.
    val origen: String = "",        // ciudad/estado artesano
    val artesano: String = "",
    val activo: Boolean = true,
    val fechaCreacion: Long = System.currentTimeMillis()
) : Parcelable {
    val stockBajo: Boolean get() = stock <= 5
}

// ─────────────────────────────────────────────
// ORDEN / PEDIDO
// ─────────────────────────────────────────────
@Parcelize
@Entity(
    tableName = "ordenes",
    foreignKeys = [ForeignKey(
        entity = Usuario::class,
        parentColumns = ["id"],
        childColumns = ["usuarioId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("usuarioId")]
)
data class Orden(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val usuarioId: Int,
    val total: Double,
    val estado: EstadoOrden = EstadoOrden.PENDIENTE,
    val fecha: Long = System.currentTimeMillis(),
    val notas: String = "",
    val confirmada: Boolean = false   // para compras > 1000 MXN
) : Parcelable {
    val esCompraGrande: Boolean get() = total > 500.0
    val requiereConfirmacion: Boolean get() = total > 1000.0
}

enum class EstadoOrden { PENDIENTE, CONFIRMADA, EN_PROCESO, ENVIADA, ENTREGADA, CANCELADA }

// ─────────────────────────────────────────────
// DETALLE DE ORDEN
// ─────────────────────────────────────────────
@Parcelize
@Entity(
    tableName = "detalle_orden",
    foreignKeys = [
        ForeignKey(
            entity = Orden::class,
            parentColumns = ["id"],
            childColumns = ["ordenId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Producto::class,
            parentColumns = ["id"],
            childColumns = ["productoId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("ordenId"), Index("productoId")]
)
data class DetalleOrden(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ordenId: Int,
    val productoId: Int,
    val cantidad: Int,
    val precioUnitario: Double,
    val subtotal: Double = cantidad * precioUnitario
) : Parcelable

// ─────────────────────────────────────────────
// NOTIFICACIÓN (para historial)
// ─────────────────────────────────────────────
@Entity(tableName = "notificaciones")
data class Notificacion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tipo: TipoNotificacion,
    val titulo: String,
    val mensaje: String,
    val datos: String = "",   // JSON extra (productoId, ordenId, etc.)
    val leida: Boolean = false,
    val fecha: Long = System.currentTimeMillis()
)

enum class TipoNotificacion {
    STOCK_BAJO,
    COMPRA_GRANDE,
    COMPRA_MUY_GRANDE,
    SISTEMA
}

// ─────────────────────────────────────────────
// CARRITO (en memoria / Room)
// ─────────────────────────────────────────────
data class ItemCarrito(
    val producto: Producto,
    var cantidad: Int = 1
) {
    val subtotal: Double get() = producto.precio * cantidad
}
