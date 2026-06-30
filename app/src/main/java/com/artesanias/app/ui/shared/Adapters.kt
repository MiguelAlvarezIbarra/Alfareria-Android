package com.artesanias.app.ui.shared

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.artesanias.app.data.model.Producto
import com.artesanias.app.databinding.ItemProductoBinding
import com.artesanias.app.databinding.ItemProductoAdminBinding
import com.artesanias.app.util.formatearPrecio
import com.bumptech.glide.Glide
import java.io.File

// ─────────────────────────────────────────────
// ADAPTADOR TIENDA (cliente)
// ─────────────────────────────────────────────
class ProductoAdapter(
    private val onAgregar: (Producto) -> Unit,
    private val onClick: (Producto) -> Unit
) : ListAdapter<Producto, ProductoAdapter.VH>(DIFF) {

    private var listaCompleta: List<Producto> = emptyList()

    fun filtrar(query: String) {
        val filtrada = if (query.isBlank()) listaCompleta
        else listaCompleta.filter {
            it.nombre.contains(query, ignoreCase = true) ||
            it.tecnica.contains(query, ignoreCase = true) ||
            it.artesano.contains(query, ignoreCase = true)
        }
        submitList(filtrada)
    }

    override fun submitList(list: List<Producto>?) {
        listaCompleta = list ?: emptyList()
        super.submitList(list)
    }

    inner class VH(private val b: ItemProductoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Producto) {
            b.tvNombre.text = p.nombre
            b.tvPrecio.text = p.precio.formatearPrecio()
            b.tvTecnica.text = p.tecnica.ifBlank { "Alfarería" }
            b.tvOrigen.text = p.origen
            b.tvStock.text = "Stock: ${p.stock}"
            b.tvStock.setTextColor(
                if (p.stockBajo) 0xFFE53935.toInt() else 0xFF43A047.toInt()
            )

            // Imagen
            if (p.imagenPath.isNotBlank()) {
                Glide.with(b.root).load(File(p.imagenPath)).into(b.ivProducto)
            } else {
                b.ivProducto.setImageResource(com.artesanias.app.R.drawable.ic_pottery_placeholder)
            }

            b.btnAgregar.setOnClickListener { onAgregar(p) }
            b.root.setOnClickListener { onClick(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Producto>() {
            override fun areItemsTheSame(a: Producto, b: Producto) = a.id == b.id
            override fun areContentsTheSame(a: Producto, b: Producto) = a == b
        }
    }
}

// ─────────────────────────────────────────────
// ADAPTADOR ADMIN PRODUCTOS
// ─────────────────────────────────────────────
class ProductoAdminAdapter(
    private val onEditar: (Producto) -> Unit,
    private val onAgregarStock: (Producto) -> Unit
) : ListAdapter<Producto, ProductoAdminAdapter.VH>(ProductoAdapter.DIFF) {

    private var listaCompleta: List<Producto> = emptyList()

    fun filtrar(query: String) {
        val f = if (query.isBlank()) listaCompleta
        else listaCompleta.filter { it.nombre.contains(query, true) }
        super.submitList(f)
    }

    override fun submitList(list: List<Producto>?) {
        listaCompleta = list ?: emptyList()
        super.submitList(list)
    }

    inner class VH(private val b: ItemProductoAdminBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Producto) {
            b.tvNombre.text = p.nombre
            b.tvPrecio.text = p.precio.formatearPrecio()
            b.tvStock.text = "Stock: ${p.stock}"
            b.chipEstado.text = if (p.activo) "Activo" else "Inactivo"
            b.chipEstado.setChipBackgroundColorResource(
                if (p.activo) com.artesanias.app.R.color.colorSuccess
                else com.artesanias.app.R.color.colorError
            )
            b.chipStockBajo.visibility = if (p.stockBajo) View.VISIBLE else View.GONE
            b.chipStockBajo.text = "⚠️ Stock bajo"

            if (p.imagenPath.isNotBlank()) {
                Glide.with(b.root).load(File(p.imagenPath)).into(b.ivProducto)
            }

            b.btnEditar.setOnClickListener { onEditar(p) }
            b.btnStock.setOnClickListener { onAgregarStock(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductoAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(getItem(pos))
}

// ─────────────────────────────────────────────
// ADAPTADOR USUARIOS (admin)
// ─────────────────────────────────────────────
class UsuarioAdapter(
    private val onToggleActivo: (com.artesanias.app.data.model.Usuario) -> Unit
) : ListAdapter<com.artesanias.app.data.model.Usuario, UsuarioAdapter.VH>(
    object : DiffUtil.ItemCallback<com.artesanias.app.data.model.Usuario>() {
        override fun areItemsTheSame(a: com.artesanias.app.data.model.Usuario, b: com.artesanias.app.data.model.Usuario) = a.id == b.id
        override fun areContentsTheSame(a: com.artesanias.app.data.model.Usuario, b: com.artesanias.app.data.model.Usuario) = a == b
    }
) {
    inner class VH(private val b: com.artesanias.app.databinding.ItemUsuarioBinding)
        : RecyclerView.ViewHolder(b.root) {
        fun bind(u: com.artesanias.app.data.model.Usuario) {
            b.tvNombre.text = "${u.nombre} ${u.apellido}"
            b.tvEmail.text = u.email
            b.chipRol.text = if (u.rol == com.artesanias.app.data.model.RolUsuario.ADMIN) "Admin" else "Cliente"
            b.switchActivo.isChecked = u.activo
            b.switchActivo.setOnCheckedChangeListener { _, _ -> onToggleActivo(u) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(com.artesanias.app.databinding.ItemUsuarioBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(getItem(pos))
}

// ─────────────────────────────────────────────
// ADAPTADOR CARRITO
// ─────────────────────────────────────────────
class CarritoAdapter(
    private val onMasCantidad: (com.artesanias.app.data.model.ItemCarrito) -> Unit,
    private val onMenosCantidad: (com.artesanias.app.data.model.ItemCarrito) -> Unit,
    private val onEliminar: (com.artesanias.app.data.model.ItemCarrito) -> Unit
) : RecyclerView.Adapter<CarritoAdapter.VH>() {

    private val items = mutableListOf<com.artesanias.app.data.model.ItemCarrito>()

    fun updateItems(nuevos: List<com.artesanias.app.data.model.ItemCarrito>) {
        items.clear()
        items.addAll(nuevos)
        notifyDataSetChanged()
    }

    inner class VH(private val b: com.artesanias.app.databinding.ItemCarritoBinding)
        : RecyclerView.ViewHolder(b.root) {
        fun bind(item: com.artesanias.app.data.model.ItemCarrito) {
            b.tvNombre.text = item.producto.nombre
            b.tvPrecioUnit.text = item.producto.precio.formatearPrecio()
            b.tvCantidad.text = item.cantidad.toString()
            b.tvSubtotal.text = item.subtotal.formatearPrecio()
            b.btnMas.setOnClickListener { onMasCantidad(item) }
            b.btnMenos.setOnClickListener { onMenosCantidad(item) }
            b.btnEliminar.setOnClickListener { onEliminar(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(com.artesanias.app.databinding.ItemCarritoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos])
    override fun getItemCount() = items.size
}
