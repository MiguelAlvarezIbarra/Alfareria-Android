package com.artesanias.app.ui.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.artesanias.app.R
import com.artesanias.app.data.model.Producto
import com.artesanias.app.ui.TiendaViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint

/**
 * Mapa de talleres artesanales: agrupa los productos por su campo `origen`
 * y pone un pin por región usando un catálogo fijo de coordenadas (los
 * productos de ejemplo no traen lat/lng, solo texto de ciudad/estado).
 */
@AndroidEntryPoint
class TalleresFragment : Fragment(), com.google.android.gms.maps.OnMapReadyCallback {

    private val viewModel: TiendaViewModel by activityViewModels()
    private var mapa: GoogleMap? = null

    companion object {
        // Coordenadas aproximadas de las regiones artesanales que aparecen
        // en los productos de ejemplo (data/local/ArtesaniasDatabase.kt).
        private val COORDENADAS = mapOf(
            "Puebla" to LatLng(19.0413, -98.2062),
            "San Bartolo Coyotepec, Oaxaca" to LatLng(16.9678, -96.6961),
            "Dolores Hidalgo, Gto" to LatLng(21.1561, -100.9330),
            "Michoacán" to LatLng(19.5138, -101.6157),
            "Guanajuato" to LatLng(21.0190, -101.2574),
            "Tlaquepaque, Jalisco" to LatLng(20.6409, -103.3121),
            "San Marcos Tlapazola, Oaxaca" to LatLng(16.9958, -96.4658)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_talleres, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        viewModel.productos.observe(viewLifecycleOwner) { productos ->
            dibujarPines(productos)
        }
    }

    private fun dibujarPines(productos: List<Producto>) {
        val mapa = mapa ?: return
        mapa.clear()

        val porOrigen = productos.filter { it.origen.isNotBlank() }.groupBy { it.origen }
        val bounds = LatLngBounds.builder()
        var huboPines = false

        porOrigen.forEach { (origen, items) ->
            val coordenada = COORDENADAS[origen] ?: return@forEach
            val artesanos = items.map { it.artesano }.distinct().joinToString(", ")
            mapa.addMarker(
                MarkerOptions()
                    .position(coordenada)
                    .title(origen)
                    .snippet("${items.size} producto(s) — $artesanos")
            )
            bounds.include(coordenada)
            huboPines = true
        }

        if (huboPines) {
            mapa.setOnMapLoadedCallback {
                mapa.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80))
            }
        }
    }
}
