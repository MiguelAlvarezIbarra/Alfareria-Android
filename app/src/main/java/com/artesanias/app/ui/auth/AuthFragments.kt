package com.artesanias.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.artesanias.app.R
import com.artesanias.app.data.model.RolUsuario
import com.artesanias.app.databinding.FragmentLoginBinding
import com.artesanias.app.databinding.FragmentRegistroBinding
import com.artesanias.app.ui.AuthViewModel
import com.artesanias.app.util.isEmailValido
import com.artesanias.app.util.isPasswordSeguro
import dagger.hilt.android.AndroidEntryPoint

// ─────────────────────────────────────────────
// LOGIN FRAGMENT
// ─────────────────────────────────────────────
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentLoginBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.btnLogin.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { usuario ->
                val dest = if (usuario.rol == RolUsuario.ADMIN)
                    R.id.adminDashboardFragment else R.id.tiendaFragment
                findNavController().navigate(dest)
            }.onFailure { e ->
                Toast.makeText(requireContext(), e.message ?: "Error al iniciar sesión", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val pass  = binding.etPassword.text.toString()

            when {
                email.isBlank() -> binding.tilEmail.error = "Ingresa tu correo"
                !email.isEmailValido() -> binding.tilEmail.error = "Correo inválido"
                pass.isBlank() -> binding.tilPassword.error = "Ingresa tu contraseña"
                else -> {
                    binding.tilEmail.error = null
                    binding.tilPassword.error = null
                    viewModel.login(email, pass)
                }
            }
        }

        binding.btnIrRegistro.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_registro)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// REGISTRO FRAGMENT
// ─────────────────────────────────────────────
@AndroidEntryPoint
class RegistroFragment : Fragment() {

    private var _binding: FragmentRegistroBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentRegistroBinding.inflate(inflater, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.btnRegistrar.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.registerResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "¡Cuenta creada! Inicia sesión", Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }.onFailure { e ->
                Toast.makeText(requireContext(), e.message ?: "Error al registrar", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnRegistrar.setOnClickListener {
            val nombre   = binding.etNombre.text.toString().trim()
            val apellido = binding.etApellido.text.toString().trim()
            val email    = binding.etEmail.text.toString().trim()
            val pass     = binding.etPassword.text.toString()
            val confirm  = binding.etConfirmPassword.text.toString()

            var valid = true
            if (nombre.isBlank()) { binding.tilNombre.error = "Campo obligatorio"; valid = false }
            else binding.tilNombre.error = null

            if (apellido.isBlank()) { binding.tilApellido.error = "Campo obligatorio"; valid = false }
            else binding.tilApellido.error = null

            if (!email.isEmailValido()) { binding.tilEmail.error = "Correo inválido"; valid = false }
            else binding.tilEmail.error = null

            if (!pass.isPasswordSeguro()) { binding.tilPassword.error = "Mínimo 6 caracteres"; valid = false }
            else binding.tilPassword.error = null

            if (pass != confirm) { binding.tilConfirmPassword.error = "Las contraseñas no coinciden"; valid = false }
            else binding.tilConfirmPassword.error = null

            if (valid) viewModel.registrar(nombre, apellido, email, pass)
        }

        binding.btnVolver.setOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
