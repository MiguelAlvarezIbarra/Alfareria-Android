package com.artesanias.app.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

// ─────────────────────────────────────────────────────────────────────────
// Entidades de Room: cada `data class` marcada con `@Entity` representa una
// tabla de la base de datos SQLite (una fila por instancia, una columna
// por propiedad). Anotaciones usadas en este archivo:
//   @Entity(tableName = ...)  nombre real de la tabla en SQLite.
//   @PrimaryKey(autoGenerate = true)  la columna `id` es la llave primaria
//       y SQLite le asigna el siguiente número disponible al insertar
//       (no hace falta indicarlo a mano).
//   @ForeignKey  declara una relación con otra tabla y qué hacer si se
//       borra la fila "padre": CASCADE borra también las filas hijas
//       (p.ej. borrar una orden borra su detalle), RESTRICT bloquea el
//       borrado si hay hijos dependientes (no se puede borrar un producto
//       que ya aparece en el detalle de una orden), SET_NULL deja la
//       referencia en null en vez de borrar (una categoría se puede
//       eliminar sin arrastrar sus productos).
//   @Index  crea un índice sobre la columna de la llave foránea, para que
//       Room no tenga que recorrer toda la tabla al buscar por ella.
//   @Parcelize  genera automáticamente la implementación de `Parcelable`
//       (el mecanismo de Android para pasar objetos entre pantallas/
//       Activities vía Bundle/Intent) a partir de las propiedades del
//       constructor, sin tener que escribirla a mano.
// ─────────────────────────────────────────────────────────────────────────

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
    // Si se borra una categoría, sus productos NO se borran: solo pierden
    // la referencia (categoriaId queda en null) para no perder el catálogo.
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
    // Propiedad calculada (no es una columna real): se recalcula cada vez
    // que se lee, siempre a partir del stock actual.
    val stockBajo: Boolean get() = stock <= 5
}

// ─────────────────────────────────────────────
// ORDEN / PEDIDO
// ─────────────────────────────────────────────
@Parcelize
@Entity(
    tableName = "ordenes",
    // Si se borra un usuario, sus órdenes se borran con él (no tiene
    // sentido conservar pedidos huérfanos de una cuenta que ya no existe).
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
    // Umbrales de negocio que disparan las alertas al reloj y a la TV
    // (ver OrdenRepository.crearOrden): toda compra > $500 se considera
    // "grande" y se notifica; > $1000 además requiere una confirmación
    // explícita desde el reloj antes de darse por buena.
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
        // Si se borra la orden, se borra su detalle (las líneas de
        // productos no tienen sentido sin la orden a la que pertenecen).
        ForeignKey(
            entity = Orden::class,
            parentColumns = ["id"],
            childColumns = ["ordenId"],
            onDelete = ForeignKey.CASCADE
        ),
        // En cambio, un producto que ya aparece en el detalle de alguna
        // orden NO se puede borrar (RESTRICT): se protege el historial de
        // ventas. Por eso el catálogo usa borrado lógico (activo = 0) en
        // vez de un DELETE real (ver ProductoDao.desactivarProducto).
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
// CARRITO (en memoria, no es una tabla de Room)
// ─────────────────────────────────────────────
// El carrito de compras vive solo en memoria (en el ViewModel) mientras el
// cliente arma su pedido; no se guarda en la base de datos hasta que se
// confirma la compra y se convierte en una Orden + una lista de
// DetalleOrden. Por eso no lleva `@Entity`.
data class ItemCarrito(
    val producto: Producto,
    var cantidad: Int = 1
) {
    val subtotal: Double get() = producto.precio * cantidad
}
