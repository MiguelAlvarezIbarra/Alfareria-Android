package com.artesanias.tv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artesanias.tv.data.TvDataStore
import com.artesanias.tv.data.TvProducto
import com.artesanias.tv.databinding.FragmentProductosBinding
import com.artesanias.tv.databinding.ItemProductoBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ProductosFragment : Fragment() {

    private var _binding: FragmentProductosBinding? = null
    private val binding get() = _binding!!
    private val adapter = ProductosAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerProductos.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.recyclerProductos.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            TvDataStore.productos.collect { adapter.actualizar(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class ProductosAdapter : RecyclerView.Adapter<ProductosAdapter.VH>() {
    private var items: List<TvProducto> = emptyList()
    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    fun actualizar(nuevos: List<TvProducto>) {
        items = nuevos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProductoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.binding.txtNombre.text = p.nombre
        holder.binding.txtPrecio.text = "${formatoMoneda.format(p.precio)} MXN"
        holder.binding.txtStock.text = if (p.stock <= 5) {
            "Stock: ${p.stock} ⚠️"
        } else {
            "Stock: ${p.stock}"
        }
    }

    override fun getItemCount() = items.size

    class VH(val binding: ItemProductoBinding) : RecyclerView.ViewHolder(binding.root)
}
