package com.artesanias.tv.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artesanias.tv.R
import com.artesanias.tv.data.TvCompraSemana
import com.artesanias.tv.data.TvDataStore
import com.artesanias.tv.data.TvMasVendido
import com.artesanias.tv.databinding.FragmentVentasBinding
import com.artesanias.tv.databinding.ItemCompraSemanaBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class VentasFragment : Fragment() {

    private var _binding: FragmentVentasBinding? = null
    private val binding get() = _binding!!
    private val adapterCompras = ComprasAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVentasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerCompras.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCompras.adapter = adapterCompras
        configurarChart()

        viewLifecycleOwner.lifecycleScope.launch {
            TvDataStore.masVendidos.collect { dibujarChart(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            TvDataStore.comprasSemana.collect { adapterCompras.actualizar(it) }
        }
    }

    private fun configurarChart() {
        val textoClaro = ContextCompat.getColor(requireContext(), R.color.colorTextPrimary)
        binding.chartMasVendidos.apply {
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.textColor = textoClaro
            axisLeft.axisMinimum = 0f
            xAxis.textColor = textoClaro
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.textSize = 10f
            xAxis.labelRotationAngle = -35f
            xAxis.setAvoidFirstLastClipping(true)
            extraBottomOffset = 24f
            setFitBars(true)
        }
    }

    private fun dibujarChart(items: List<TvMasVendido>) {
        val entries = items.mapIndexed { i, m -> BarEntry(i.toFloat(), m.cantidad.toFloat()) }
        val dataSet = BarDataSet(entries, "Unidades vendidas").apply {
            color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.colorTextPrimary)
            valueTextSize = 12f
        }
        binding.chartMasVendidos.data = BarData(dataSet)
        binding.chartMasVendidos.xAxis.valueFormatter =
            IndexAxisValueFormatter(items.map { it.nombre })
        binding.chartMasVendidos.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class ComprasAdapter : RecyclerView.Adapter<ComprasAdapter.VH>() {
    private var items: List<TvCompraSemana> = emptyList()
    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    fun actualizar(nuevos: List<TvCompraSemana>) {
        items = nuevos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCompraSemanaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.binding.txtFecha.text = c.fecha
        holder.binding.txtCliente.text = c.cliente
        holder.binding.txtTotal.text = formatoMoneda.format(c.total)
    }

    override fun getItemCount() = items.size

    class VH(val binding: ItemCompraSemanaBinding) : RecyclerView.ViewHolder(binding.root)
}
