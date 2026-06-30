package com.artesanias.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.artesanias.app.R
import com.artesanias.app.databinding.FragmentCamaraBinding
import com.artesanias.app.util.QRUtil
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CamaraFragment : Fragment() {

    private var _binding: FragmentCamaraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var modoQR = false   // false = foto producto, true = scan QR

    // Launcher de permisos de cámara
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) iniciarCamara()
        else Toast.makeText(requireContext(), "Permiso de cámara requerido", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentCamaraBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        actualizarUI()

        binding.btnCambiarModo.setOnClickListener {
            modoQR = !modoQR
            actualizarUI()
            iniciarCamara()
        }

        binding.btnCapturar.setOnClickListener {
            if (modoQR) escanearQR() else tomarFoto()
        }

        // Verificar y solicitar permiso
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun actualizarUI() {
        if (modoQR) {
            binding.tvModo.text = "Modo: Escanear QR (Wear OS)"
            binding.btnCambiarModo.text = "📷 Modo Producto"
            binding.btnCapturar.text = "Escanear QR"
            binding.overlayQr.visibility = View.VISIBLE
        } else {
            binding.tvModo.text = "Modo: Fotografiar Producto"
            binding.btnCambiarModo.text = "🔲 Modo QR"
            binding.btnCapturar.text = "Tomar Foto"
            binding.overlayQr.visibility = View.GONE
        }
    }

    private fun iniciarCamara() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CamaraFragment", "Error al iniciar cámara", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun tomarFoto() {
        val capture = imageCapture ?: return
        val photoFile = crearArchivoFoto()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = Uri.fromFile(photoFile)
                    Toast.makeText(requireContext(), "Foto guardada", Toast.LENGTH_SHORT).show()
                    // Navegar a formulario de nuevo producto con la ruta de la foto
                    val bundle = Bundle().apply {
                        putString("imagenPath", photoFile.absolutePath)
                    }
                    findNavController().navigate(R.id.action_camara_to_nuevoProducto, bundle)
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Error al capturar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun escanearQR() {
        val capture = imageCapture ?: return
        val photoFile = crearArchivoFoto()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    procesarQR(photoFile)
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Error al capturar", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun procesarQR(archivo: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(archivo.absolutePath)
            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            val qrContent = result.text

            val nodeId = QRUtil.parsearQRWear(qrContent)
            if (nodeId != null) {
                Toast.makeText(
                    requireContext(),
                    "✅ Reloj conectado: ${nodeId.take(8)}...",
                    Toast.LENGTH_LONG
                ).show()
                // Guardar nodeId para comunicación Wear
                // En producción: conectar vía Wearable.MessageClient
            } else {
                Toast.makeText(requireContext(), "QR no reconocido: $qrContent", Toast.LENGTH_LONG).show()
            }
        } catch (e: NotFoundException) {
            Toast.makeText(requireContext(), "No se encontró QR en la imagen", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error procesando QR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun crearArchivoFoto(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = requireContext().getExternalFilesDir("artesanias") ?: requireContext().filesDir
        return File(dir, "IMG_$timestamp.jpg")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
