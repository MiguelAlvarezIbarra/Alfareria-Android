package com.artesanias.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.fragment.app.Fragment
import com.artesanias.tv.databinding.FragmentVideoBinding

/**
 * Pantalla 3: video del proceso de alfarería, embebido desde YouTube (no
 * listado) vía WebView. Se eligió este camino porque el reproductor nativo
 * de Android (VideoView/MediaPlayer) depende del servicio de códecs del
 * sistema, que falla en varios emuladores de Android TV; el reproductor
 * embebido de YouTube usa el motor de WebView y no depende de eso.
 */
class VideoFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val VIDEO_ID = "szTeNNCdJxg"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!hayInternet()) {
            binding.webView.visibility = View.GONE
            binding.txtVideoPendiente.visibility = View.VISIBLE
            return
        }

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.mediaPlaybackRequiresUserGesture = false
        binding.webView.webChromeClient = WebChromeClient()
        // YouTube bloquea la reproducción embebida ("Error 152") cuando
        // detecta el user-agent genérico de WebView; se le hace pasar por
        // un Chrome normal para que el embed funcione.
        binding.webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

        val html = """
            <html>
            <body style="margin:0;padding:0;background:#000;">
                <iframe width="100%" height="100%"
                    src="https://www.youtube.com/embed/$VIDEO_ID?autoplay=1&playsinline=1&rel=0"
                    frameborder="0"
                    allow="autoplay; encrypted-media"
                    allowfullscreen></iframe>
            </body>
            </html>
        """.trimIndent()

        binding.webView.loadDataWithBaseURL(
            "https://www.youtube.com", html, "text/html", "utf-8", null
        )
    }

    private fun hayInternet(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val red = cm.activeNetwork ?: return false
        val capacidades = cm.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onPause() {
        super.onPause()
        _binding?.webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        _binding?.webView?.onResume()
    }

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
