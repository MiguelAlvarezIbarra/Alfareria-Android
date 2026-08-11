package com.artesanias.tv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.artesanias.tv.data.TvDataStore
import com.artesanias.tv.databinding.ActivityMainBinding
import com.artesanias.tv.ui.ProductosFragment
import com.artesanias.tv.ui.VentasFragment
import com.artesanias.tv.ui.VideoFragment
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ocultarOverlay = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            mostrarPantalla(ProductosFragment(), binding.navProductos)
        }

        binding.navProductos.setOnClickListener { mostrarPantalla(ProductosFragment(), binding.navProductos) }
        binding.navVentas.setOnClickListener { mostrarPantalla(VentasFragment(), binding.navVentas) }
        binding.navVideo.setOnClickListener { mostrarPantalla(VideoFragment(), binding.navVideo) }

        // El overlay escucha el evento global sin importar qué Fragment esté
        // activo: por eso vive en la Activity y no en cada pantalla. Se usa
        // un Dialog (ventana propia) en vez de alternar la visibilidad de un
        // View hijo: es el patrón más confiable para "mostrar algo encima
        // de todo" y evita depender de que el layout padre recalcule bien
        // un View que arrancó en GONE.
        lifecycleScope.launch {
            TvDataStore.compraGrandeEvents.collect { evento ->
                mostrarNotificacionCompraGrande(evento)
            }
        }
    }

    private var dialogoCompraGrande: android.app.Dialog? = null

    private fun mostrarNotificacionCompraGrande(evento: com.artesanias.tv.data.TvCompraGrandeEvent) {
        dialogoCompraGrande?.dismiss()
        ocultarOverlay.removeCallbacksAndMessages(null)

        val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        val dialogBinding = com.artesanias.tv.databinding.DialogCompraGrandeBinding.inflate(layoutInflater)
        dialogBinding.txtOverlayProducto.text = evento.producto
        dialogBinding.txtOverlayMonto.text = "${formatoMoneda.format(evento.monto)} MXN"

        val dialog = android.app.Dialog(this, R.style.Theme_CompraGrandeDialog).apply {
            setContentView(dialogBinding.root)
            setCancelable(true)
            setOnDismissListener { dialogoCompraGrande = null }
        }
        dialogBinding.root.setOnClickListener { dialog.dismiss() }
        dialogoCompraGrande = dialog
        dialog.show()

        ocultarOverlay.postDelayed(8000) { dialog.dismiss() }
    }

    private fun mostrarPantalla(fragment: Fragment, itemSeleccionado: android.view.View) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()

        listOf(binding.navProductos, binding.navVentas, binding.navVideo).forEach {
            it.isSelected = it == itemSeleccionado
        }

        // El contenido de cada pantalla (RecyclerView, VideoView...) puede
        // robarse el foco de control remoto al aparecer; lo regresamos al
        // riel de navegación para no perder la navegación por D-pad.
        itemSeleccionado.post { itemSeleccionado.requestFocus() }
    }
}
