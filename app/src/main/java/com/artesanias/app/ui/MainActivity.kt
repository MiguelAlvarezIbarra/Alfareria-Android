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

        // Determinar destino inicial
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

    private fun setupNavigation() {
        val bottomNav = binding.bottomNavView

        // Ocultar bottom nav en pantallas de auth
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.loginFragment, R.id.registroFragment -> {
                    bottomNav.visibility = android.view.View.GONE
                    supportActionBar?.hide()
                }
                else -> {
                    bottomNav.visibility = android.view.View.VISIBLE
                    supportActionBar?.show()
                    updateMenuForRole(bottomNav)
                }
            }
        }

        val topLevelAdmin = setOf(
            R.id.adminDashboardFragment, R.id.adminProductosFragment,
            R.id.adminUsuariosFragment, R.id.camaraFragment
        )
        val topLevelCliente = setOf(
            R.id.tiendaFragment, R.id.carritoFragment, R.id.misOrdenesFragment
        )

        val appBarConfig = AppBarConfiguration(topLevelAdmin + topLevelCliente)
        setupActionBarWithNavController(navController, appBarConfig)
        bottomNav.setupWithNavController(navController)
    }

    private fun updateMenuForRole(nav: BottomNavigationView) {
        nav.menu.clear()
        if (session.isAdmin) {
            nav.inflateMenu(R.menu.menu_admin)
        } else {
            nav.inflateMenu(R.menu.menu_cliente)
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
