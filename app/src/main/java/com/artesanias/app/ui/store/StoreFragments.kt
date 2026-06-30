package com.artesanias.app.ui.store

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.artesanias.app.R
import com.artesanias.app.data.model.*
import com.artesanias.app.databinding.*
import com.artesanias.app.ui.AuthViewModel
import com.artesanias.app.ui.TiendaViewModel
import com.artesanias.app.ui.shared.CarritoAdapter
import com.artesanias.app.ui.shared.ProductoAdapter
import com.artesanias.app.util.formatearPrecio
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

// ─────────────────────────────────────────────
// TIENDA FRAGMENT
// ─────────────────────────────────────────────
@AndroidEntryPoint
class TiendaFragment : Fragment() {

    private var _binding: FragmentTiendaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TiendaViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private lateinit var adapter: ProductoAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentTiendaBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProductoAdapter(
            onAgregar = { producto -> agregarAlCarrito(producto) },
            onClick   = { producto -> mostrarDetalleProducto(producto) }
        )

        binding.rvProductos.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProductos.adapter = adapter

        viewModel.productos.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }

        // Badge en el carrito
        viewModel.cantidadCarrito.observe(viewLifecycleOwner) { cantidad ->
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_nav_view)
            val badge: BadgeDrawable? = bottomNav?.getOrCreateBadge(R.id.carritoFragment)
            if (cantidad > 0) {
                badge?.isVisible = true
                badge?.number = cantidad
            } else {
                badge?.isVisible = false
            }
        }

        // Búsqueda
        binding.etBuscar.addTextChangedListener { text -> viewModel.buscar(text.toString()) }

        binding.btnCerrarSesion.setOnClickListener {
            authViewModel.cerrarSesion()
            findNavController().navigate(R.id.loginFragment)
        }

        // Swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.buscar("")
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun agregarAlCarrito(producto: Producto) {
        viewModel.agregarAlCarrito(producto)
        Toast.makeText(requireContext(), "✓ ${producto.nombre} en el carrito", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarDetalleProducto(producto: Producto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(producto.nombre)
            .setMessage(buildString {
                appendLine(producto.descripcion)
                appendLine()
                appendLine("💰 Precio: ${producto.precio.formatearPrecio()}")
                appendLine("🏺 Técnica: ${producto.tecnica}")
                appendLine("📍 Origen: ${producto.origen}")
                appendLine("👨‍🎨 Artesano: ${producto.artesano}")
                appendLine("📦 Stock: ${producto.stock} disponibles")
            })
            .setPositiveButton("Agregar al carrito") { _, _ -> agregarAlCarrito(producto) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// CARRITO FRAGMENT
// ─────────────────────────────────────────────
@AndroidEntryPoint
class CarritoFragment : Fragment() {

    private var _binding: FragmentCarritoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TiendaViewModel by activityViewModels()
    private lateinit var adapter: CarritoAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentCarritoBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CarritoAdapter(
            onMasCantidad  = { item -> viewModel.cambiarCantidad(item.producto.id, item.cantidad + 1) },
            onMenosCantidad = { item -> viewModel.cambiarCantidad(item.producto.id, item.cantidad - 1) },
            onEliminar = { item -> viewModel.quitarDelCarrito(item.producto.id) }
        )

        binding.rvCarrito.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCarrito.adapter = adapter

        viewModel.carrito.observe(viewLifecycleOwner) { items ->
            adapter.updateItems(items)
            binding.tvVacio.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.btnComprar.isEnabled = items.isNotEmpty()
        }

        viewModel.totalCarrito.observe(viewLifecycleOwner) { total ->
            binding.tvTotal.text = "Total: ${total.formatearPrecio()}"

            // Advertencia visual si requiere confirmación
            binding.tvAviso.visibility = if (total > 1000) View.VISIBLE else View.GONE
            binding.tvAviso.text = "⚠️ Compra mayor a $1,000 MXN — requiere confirmación"
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.btnComprar.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.ordenResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { orden ->
                val mensaje = when {
                    orden.requiereConfirmacion ->
                        "✅ Orden #${orden.id} creada.\n⚠️ Requiere confirmación (>${1000.0.formatearPrecio()})"
                    orden.esCompraGrande ->
                        "✅ Orden #${orden.id} creada.\n💰 Compra grande notificada al reloj"
                    else ->
                        "✅ ¡Compra realizada! Orden #${orden.id}"
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Compra exitosa")
                    .setMessage(mensaje)
                    .setPositiveButton("OK") { _, _ ->
                        findNavController().navigate(R.id.misOrdenesFragment)
                    }
                    .show()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnComprar.setOnClickListener {
            val total = viewModel.totalCarrito.value ?: 0.0
            if (total > 1000) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirmar compra grande")
                    .setMessage("Esta compra supera los \$1,000 MXN.\n¿Deseas proceder?\n\nSe notificará al administrador.")
                    .setPositiveButton("Sí, confirmar") { _, _ -> viewModel.realizarCompra() }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else {
                viewModel.realizarCompra()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// MIS ORDENES FRAGMENT (cliente)
// ─────────────────────────────────────────────
@AndroidEntryPoint
class MisOrdenesFragment : Fragment() {

    private var _binding: FragmentMisOrdenesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TiendaViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentMisOrdenesBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = OrdenAdapter()
        binding.rvOrdenes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrdenes.adapter = adapter

        viewModel.misOrdenes.observe(viewLifecycleOwner) { ordenes ->
            adapter.submitList(ordenes)
            binding.tvVacio.visibility = if (ordenes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// Adaptador simple para órdenes
class OrdenAdapter : androidx.recyclerview.widget.ListAdapter<Orden, OrdenAdapter.VH>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Orden>() {
        override fun areItemsTheSame(a: Orden, b: Orden) = a.id == b.id
        override fun areContentsTheSame(a: Orden, b: Orden) = a == b
    }
) {
    inner class VH(private val b: com.artesanias.app.databinding.ItemOrdenBinding)
        : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
        fun bind(o: Orden) {
            b.tvOrdenId.text = "Orden #${o.id}"
            b.tvTotal.text = o.total.formatearPrecio()
            b.tvEstado.text = o.estado.name.replace("_", " ")
            b.tvFecha.text = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", o.fecha).toString()
            b.chipConfirmacion.visibility = if (o.requiereConfirmacion && !o.confirmada) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(com.artesanias.app.databinding.ItemOrdenBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(getItem(pos))
}
