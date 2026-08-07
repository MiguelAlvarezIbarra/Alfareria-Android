package com.artesanias.tv.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.fragment.app.Fragment
import com.artesanias.tv.databinding.FragmentVideoBinding

/**
 * Pantalla 3: video del proceso de alfarería.
 * Busca "video_proceso.mp4" en assets/; si no existe todavía, muestra un
 * aviso en vez de fallar, para que el módulo compile aunque el archivo real
 * se agregue después.
 */
class VideoFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val NOMBRE_ARCHIVO = "video_proceso.mp4"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val existe = requireContext().assets.list("")?.contains(NOMBRE_ARCHIVO) == true

        if (!existe) {
            binding.videoView.visibility = View.GONE
            binding.txtVideoPendiente.visibility = View.VISIBLE
            return
        }

        binding.videoView.setVideoURI(Uri.parse("file:///android_asset/$NOMBRE_ARCHIVO"))
        binding.videoView.setMediaController(MediaController(requireContext()).apply {
            setAnchorView(binding.videoView)
        })
        binding.videoView.setOnPreparedListener { it.isLooping = true }
        binding.videoView.setOnErrorListener { _, _, _ ->
            // Evita el diálogo genérico del sistema si el decodificador del
            // dispositivo no soporta el códec del archivo (frecuente en
            // emuladores x86 con video grabado en H.265/HEVC).
            binding.videoView.visibility = View.GONE
            binding.txtVideoPendiente.visibility = View.VISIBLE
            binding.txtVideoPendiente.text =
                "⚠️ Este dispositivo no puede reproducir el video (códec no soportado).\n" +
                "Prueba con un MP4 en H.264."
            true
        }
        binding.videoView.start()
    }

    override fun onPause() {
        super.onPause()
        if (binding.videoView.isPlaying) binding.videoView.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
