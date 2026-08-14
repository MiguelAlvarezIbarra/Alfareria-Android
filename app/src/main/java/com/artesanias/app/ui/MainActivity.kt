package com.artesanias.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.artesanias.app.R
import com.artesanias.app.databinding.ActivityMainBinding
import com.artesanias.app.util.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity de la app (arquitectura "single-Activity"): todas las
 * pantallas son Fragments manejados por el Navigation Component sobre un
 * único `nav_graph`, y esta clase solo se encarga de la navegación de más
 * alto nivel (mostrar/ocultar la barra inferior, cambiar su menú según el
 * rol de la sesión activa).
 *
 * `@AndroidEntryPoint`: habilita la inyección de dependencias de Hilt en
 * esta Activity (permite usar `by viewModels()` / `by activityViewModels()`
 * más abajo en los Fragments, que a su vez inyectan repositorios).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Determinar destino inicial: si ya hay una sesión guardada (el
        // usuario no cerró sesión la última vez), se salta la pantalla de
        // login y entra directo a la pantalla que le corresponde según su
        // rol; si no, arranca en el login.
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            if (session.isLoggedIn) {
                if (session.isAdmin) R.id.adminDashboardFragment else R.id.tiendaFragment
            } else {
                R.id.loginFragment
            }
        )
        navController.graph = graph

        setupNavigation()
    }

    /**
     * Conecta el NavController con la barra de navegación inferior y con
     * la ActionBar, y decide cuándo mostrar/ocultar la barra inferior y
     * con qué menú (de administrador o de cliente).
     */
    private fun setupNavigation() {
        val bottomNav = binding.bottomNavView

        // Se ejecuta en cada cambio de pantalla (NavController lo llama
        // automáticamente): oculta la barra inferior en las pantallas de
        // autenticación, donde no aplica ningún menú todavía.
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.loginFragment, R.id.registroFragment -> {
                    bottomNav.visibility = android.view.View.GONE
                    supportActionBar?.hide()
                }
                else -> {
                    bottomNav.visibility = android.view.View.VISIBLE
                    supportActionBar?.show()
                    // Solo reconstruir el menú cuando cambia el rol (no en cada
                    // navegación): limpiar/reinflar el menú en cada destino
                    // desincroniza el ítem seleccionado de BottomNavigationView
                    // del NavController. Se compara contra el menú realmente
                    // inflado usando un ítem ancla fijo por rol (no un flag
                    // aparte, que se podía desincronizar al cerrar sesión y
                    // volver a entrar con otro rol dentro de la misma Activity,
                    // dejando visible el menú del rol anterior) y no un
                    // destino cualquiera (que fallaría en pantallas que no
                    // son pestañas del menú, como editar producto).
                    val anclaRol = if (session.isAdmin) R.id.adminDashboardFragment else R.id.tiendaFragment
                    if (bottomNav.menu.findItem(anclaRol) == null) {
                        updateMenuForRole(bottomNav)
                    }
                }
            }
        }

        // Destinos "de primer nivel": NavigationUI los trata como raíces
        // (no muestran flecha de "atrás" en la ActionBar, y el botón atrás
        // del sistema en ellos sale de la app en vez de navegar hacia atrás).
        val topLevelAdmin = setOf(
            R.id.adminDashboardFragment, R.id.adminProductosFragment,
            R.id.adminUsuariosFragment, R.id.camaraFragment
        )
        val topLevelCliente = setOf(
            R.id.tiendaFragment, R.id.carritoFragment, R.id.misOrdenesFragment, R.id.talleresFragment
        )

        val appBarConfig = AppBarConfiguration(topLevelAdmin + topLevelCliente)
        setupActionBarWithNavController(navController, appBarConfig)
        // Conecta cada ítem del menú de la barra inferior con el destino
        // del mismo id en el nav_graph: tocar un ítem navega solo, sin
        // necesidad de un OnItemSelectedListener escrito a mano.
        bottomNav.setupWithNavController(navController)
    }

    /** Reemplaza el menú de la barra inferior por el que corresponde al rol de la sesión activa. */
    private fun updateMenuForRole(nav: BottomNavigationView) {
        nav.menu.clear()
        if (session.isAdmin) {
            nav.inflateMenu(R.menu.menu_admin)
        } else {
            nav.inflateMenu(R.menu.menu_cliente)
        }
    }

    // Hace que la flecha de "atrás" de la ActionBar (en pantallas que no
    // son de primer nivel) navegue hacia atrás en el grafo.
    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
