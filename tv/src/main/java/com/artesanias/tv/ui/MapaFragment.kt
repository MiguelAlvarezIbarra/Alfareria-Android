package com.artesanias.tv.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.fragment.app.Fragment
import com.artesanias.tv.BuildConfig
import com.artesanias.tv.databinding.FragmentMapaBinding

/**
 * Mapa de talleres artesanales para la TV, usando el Google Maps JavaScript
 * API dentro de un WebView. La primera vez que se probó esta pantalla se
 * veía en negro porque el reloj del emulador estaba desincronizado (varias
 * semanas atrás) y eso invalidaba el certificado TLS de Google, colgando la
 * carga sin ningún error visible; una vez corregido el reloj del emulador
 * el mapa carga con normalidad.
 *
 * Las coordenadas son las mismas regiones que en TalleresFragment del
 * módulo de teléfono (app/ui/store/TalleresFragment.kt).
 */
class MapaFragment : Fragment() {

    private var _binding: FragmentMapaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapaBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.webChromeClient = WebChromeClient()
        binding.webView.loadDataWithBaseURL(
            "https://maps.googleapis.com", html(), "text/html", "utf-8", null
        )
    }

    private fun html(): String = """
        <!DOCTYPE html>
        <html>
        <head>
        <style>
            html, body, #map { height:100%; margin:0; padding:0; background:#1B1512; }
            .gm-style-iw { font-family: sans-serif; }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script>
        function initMap() {
            var talleres = [
                {lat:19.0413, lng:-98.2062, nombre:"Puebla", info:"Talavera — Familia Uriarte"},
                {lat:16.9678, lng:-96.6961, nombre:"San Bartolo Coyotepec, Oaxaca", info:"Barro Negro — Rosa Nieto"},
                {lat:21.1561, lng:-100.9330, nombre:"Dolores Hidalgo, Gto", info:"Talavera — Taller del Sol"},
                {lat:19.5138, lng:-101.6157, nombre:"Michoacán", info:"Alfarería Utilitaria — Cooperativa La Barro"},
                {lat:21.0190, lng:-101.2574, nombre:"Guanajuato", info:"Mayólica — Taller Gorky"},
                {lat:20.6409, lng:-103.3121, nombre:"Tlaquepaque, Jalisco", info:"Barro Rojo — Artesanos Unidos"},
                {lat:16.9958, lng:-96.4658, nombre:"San Marcos Tlapazola, Oaxaca", info:"Barro Negro — Maestro Jiménez"}
            ];
            var map = new google.maps.Map(document.getElementById('map'), {
                zoom: 5,
                center: {lat: 20.0, lng: -100.0},
                styles: [
                    {elementType:"geometry", stylers:[{color:"#2a211d"}]},
                    {elementType:"labels.text.fill", stylers:[{color:"#f5e6d3"}]},
                    {elementType:"labels.text.stroke", stylers:[{color:"#1b1512"}]},
                    {featureType:"water", elementType:"geometry", stylers:[{color:"#0e3a4a"}]},
                    {featureType:"road", elementType:"geometry", stylers:[{color:"#4a3b30"}]}
                ]
            });
            var bounds = new google.maps.LatLngBounds();
            talleres.forEach(function(t) {
                var pos = {lat: t.lat, lng: t.lng};
                var marker = new google.maps.Marker({position: pos, map: map, title: t.nombre});
                var info = new google.maps.InfoWindow({
                    content: '<b>' + t.nombre + '</b><br>' + t.info
                });
                marker.addListener('click', function() { info.open(map, marker); });
                bounds.extend(pos);
            });
            map.fitBounds(bounds);
        }
        </script>
        <script src="https://maps.googleapis.com/maps/api/js?key=${BuildConfig.MAPS_API_KEY}&callback=initMap" async defer></script>
        </body>
        </html>
    """.trimIndent()

    override fun onDestroyView() {
        _binding?.webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        super.onDestroyView()
        _binding = null
    }
}
