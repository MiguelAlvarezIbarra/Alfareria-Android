package com.artesanias.app.ui.admin

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import com.artesanias.app.R
import com.artesanias.app.data.model.*
import com.artesanias.app.databinding.*
import com.artesanias.app.ui.AdminProductosViewModel
import com.artesanias.app.ui.AdminUsuariosViewModel
import com.artesanias.app.ui.AuthViewModel
import com.artesanias.app.ui.shared.ProductoAdapter
import com.artesanias.app.ui.shared.ProductoAdminAdapter
import com.artesanias.app.ui.shared.UsuarioAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint

// ─────────────────────────────────────────────────────────────────────────
// Pantallas del panel de administrador. Todas siguen el mismo patrón:
//   @AndroidEntryPoint   habilita que Hilt inyecte dependencias en este
//       Fragment (necesario para que `by viewModels()` pueda resolver el
//       ViewModel y sus propias dependencias inyectadas).
//   View Binding (`_binding` / `binding`)  en vez de `findViewById`, Android
//       genera una clase `FragmentXxxBinding` con una propiedad por cada
//       vista del layout XML, con seguridad de tipos en tiempo de
//       compilación. `_binding` es nullable y se limpia en onDestroyView
//       porque la vista del Fragment se destruye antes que el Fragment
//       mismo (para no dejar una referencia fuga a una vista muerta); el
//       getter `binding` (sin guion bajo) es el que se usa en el resto del
//       código y solo es válido mientras la vista exista.
//   by viewModels() / by activityViewModels()  piden el ViewModel a Hilt.
//       `viewModels()` crea uno propio de este Fragment; `activityViewModels()`
//       reutiliza el mismo que cualquier otro Fragment de la misma Activity
//       que lo pida (necesario para AuthViewModel, que debe reflejar el
//       mismo estado de sesión en todas las pantallas).
// ─────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────
// ADMIN DASHBOARD
// ─────────────────────────────────────────────
@AndroidEntryPoint
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val productoViewModel: AdminProductosViewModel by viewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAdminDashboardBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        productoViewModel.productos.observe(viewLifecycleOwner) { lista ->
            binding.tvTotalProductos.text = lista.size.toString()
            val activos = lista.count { it.activo }
            binding.tvProductosActivos.text = "$activos activos"
        }

        productoViewModel.productosStockBajo.observe(viewLifecycleOwner) { lista ->
            binding.tvStockBajo.text = lista.size.toString()
            binding.cardStockBajo.visibility =
                if (lista.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // Navegación rápida
        binding.cardProductos.setOnClickListener {
            findNavController().navigate(R.id.adminProductosFragment)
        }
        binding.cardUsuarios.setOnClickListener {
            findNavController().navigate(R.id.adminUsuariosFragment)
        }
        binding.cardCamara.setOnClickListener {
            findNavController().navigate(R.id.camaraFragment)
        }
        binding.btnCerrarSesion.setOnClickListener {
            authViewModel.cerrarSesion()
            // popUpTo el grafo completo: al cerrar sesión no debe quedar
            // nada del panel de admin en la pila de retroceso.
            findNavController().navigate(R.id.loginFragment, null, navOptions {
                popUpTo(findNavController().graph.id) { inclusive = true }
            })
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// ADMIN PRODUCTOS
// ─────────────────────────────────────────────
/** Lista de inventario con buscador, edición y botón para agregar stock por producto. */
@AndroidEntryPoint
class AdminProductosFragment : Fragment() {

    private var _binding: FragmentAdminProductosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminProductosViewModel by viewModels()
    private lateinit var adapter: ProductoAdminAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAdminProductosBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProductoAdminAdapter(
            onEditar = { producto ->
                val bundle = Bundle().apply { putParcelable("producto", producto) }
                findNavController().navigate(R.id.action_adminProductos_to_editarProducto, bundle)
            },
            onAgregarStock = { producto -> mostrarDialogoStock(producto) }
        )

        binding.rvProductos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProductos.adapter = adapter

        viewModel.productos.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.fabNuevoProducto.setOnClickListener {
            findNavController().navigate(R.id.camaraFragment)
        }

        binding.etBuscar.addTextChangedListener { text ->
            adapter.filtrar(text.toString())
        }

        viewModel.operacionResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "✓ Operación exitosa", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Diálogo simple para capturar cuántas unidades reabastecer de un producto. */
    private fun mostrarDialogoStock(producto: Producto) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_agregar_stock, null)
        val etCantidad = dialogView.findViewById<TextInputEditText>(R.id.et_cantidad)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Agregar stock\n${producto.nombre}")
            .setMessage("Stock actual: ${producto.stock}")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val cantidad = etCantidad.text.toString().toIntOrNull() ?: 0
                if (cantidad > 0) {
                    viewModel.agregarStock(producto.id, cantidad)
                } else {
                    Toast.makeText(requireContext(), "Ingresa una cantidad válida", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// ADMIN USUARIOS
// ─────────────────────────────────────────────
/** Lista de cuentas registradas, con acción para activar/desactivar y un diálogo para dar de alta nuevas. */
@AndroidEntryPoint
class AdminUsuariosFragment : Fragment() {

    private var _binding: FragmentAdminUsuariosBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminUsuariosViewModel by viewModels()
    private lateinit var adapter: UsuarioAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAdminUsuariosBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = UsuarioAdapter { usuario -> viewModel.toggleActivo(usuario) }

        binding.rvUsuarios.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsuarios.adapter = adapter

        viewModel.usuarios.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.fabNuevoUsuario.setOnClickListener { mostrarDialogoNuevoUsuario() }

        viewModel.operacionResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "✓ Usuario creado", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(requireContext(), e.message ?: "Error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarDialogoNuevoUsuario() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_nuevo_usuario, null)
        val etNombre   = dialogView.findViewById<TextInputEditText>(R.id.et_nombre)
        val etApellido = dialogView.findViewById<TextInputEditText>(R.id.et_apellido)
        val etEmail    = dialogView.findViewById<TextInputEditText>(R.id.et_email)
        val etPass     = dialogView.findViewById<TextInputEditText>(R.id.et_password)
        val switchAdmin = dialogView.findViewById<android.widget.Switch>(R.id.switch_admin)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nuevo usuario")
            .setView(dialogView)
            .setPositiveButton("Crear") { _, _ ->
                val nombre   = etNombre.text.toString().trim()
                val apellido = etApellido.text.toString().trim()
                val email    = etEmail.text.toString().trim()
                val pass     = etPass.text.toString()
                val rol      = if (switchAdmin.isChecked) RolUsuario.ADMIN else RolUsuario.CLIENTE

                if (nombre.isBlank() || email.isBlank() || pass.isBlank()) {
                    Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.agregarUsuario(nombre, apellido, email, pass, rol)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─────────────────────────────────────────────
// NUEVO/EDITAR PRODUCTO FRAGMENT
// ─────────────────────────────────────────────
/**
 * Formulario único para dar de alta un producto o editar uno existente:
 * `productoExistente` (recibido por argumento de navegación) decide cuál
 * de los dos modos usar y precarga los campos si aplica.
 */
@AndroidEntryPoint
class EditarProductoFragment : Fragment() {

    private var _binding: FragmentEditarProductoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminProductosViewModel by viewModels()

    private var productoExistente: Producto? = null
    private var imagenPath: String = ""

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentEditarProductoBinding.inflate(i, c, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recibir producto (edición) o imagenPath (nuevo desde cámara)
        productoExistente = arguments?.getParcelable("producto")
        imagenPath = arguments?.getString("imagenPath") ?: ""

        productoExistente?.let { p ->
            binding.etNombre.setText(p.nombre)
            binding.etDescripcion.setText(p.descripcion)
            binding.etPrecio.setText(p.precio.toString())
            binding.etStock.setText(p.stock.toString())
            binding.etTecnica.setText(p.tecnica)
            binding.etOrigen.setText(p.origen)
            binding.etArtesano.setText(p.artesano)
            binding.tvTitulo.text = "Editar producto"
        } ?: run {
            binding.tvTitulo.text = "Nuevo producto"
            if (imagenPath.isNotBlank()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(imagenPath)
                binding.ivProducto.setImageBitmap(bmp)
            }
        }

        viewModel.operacionResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Toast.makeText(requireContext(), "✓ Producto guardado", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }.onFailure { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.btnGuardar.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        binding.btnGuardar.setOnClickListener { guardar() }
    }

    /** Valida el formulario y arma el `Producto` (nuevo o copia editada) antes de guardarlo. */
    private fun guardar() {
        val nombre = binding.etNombre.text.toString().trim()
        val precio = binding.etPrecio.text.toString().toDoubleOrNull()
        val stock  = binding.etStock.text.toString().toIntOrNull()

        if (nombre.isBlank()) { binding.tilNombre.error = "Obligatorio"; return }
        if (precio == null || precio <= 0) { binding.tilPrecio.error = "Precio inválido"; return }
        if (stock == null || stock < 0) { binding.tilStock.error = "Stock inválido"; return }

        val producto = (productoExistente ?: Producto(
            nombre = "", descripcion = "", precio = 0.0, stock = 0
        )).copy(
            nombre = nombre,
            descripcion = binding.etDescripcion.text.toString().trim(),
            precio = precio,
            stock = stock,
            tecnica = binding.etTecnica.text.toString().trim(),
            origen = binding.etOrigen.text.toString().trim(),
            artesano = binding.etArtesano.text.toString().trim(),
            imagenPath = if (imagenPath.isNotBlank()) imagenPath else (productoExistente?.imagenPath ?: "")
        )

        viewModel.guardarProducto(producto)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
