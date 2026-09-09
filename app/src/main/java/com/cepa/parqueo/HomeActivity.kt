package com.cepa.parqueo

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import com.cepa.parqueo.databinding.ActivityHomeBinding

class HomeActivity : DrawerActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var username: String
    private lateinit var currentUserType: UserType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)

        username = intent.getStringExtra("USERNAME") ?: "Usuario"
        val userTypeString = intent.getStringExtra("USER_TYPE") ?: "OPERADOR"
        currentUserType = UserType.valueOf(userTypeString)

        setContentViewWithDrawer(binding.root, R.id.nav_dashboard)
        supportActionBar?.subtitle = com.cepa.parqueo.database.SiteConfigCache.nombreComercial()

        setupUI()
        applyPermissions()
    }

    private fun setupUI() {
        binding.tvWelcome.text = "Bienvenido, $username"
        binding.tvUserRole.text = "Rol: ${currentUserType.name}"

        binding.btnIngresoVehiculo.setOnClickListener {
            if (currentUserType.canAccessEntry()) {
                startActivity(Intent(this, IngresoVehiculoActivity::class.java))
            } else {
                showNoPermissionDialog()
            }
        }

        binding.btnSalidaVehiculo.setOnClickListener {
            if (currentUserType.canAccessExit()) {
                // Pasar el tipo de usuario a SalidaVehiculoActivity
                val intent = Intent(this, SalidaVehiculoActivity::class.java)
                intent.putExtra("USER_TYPE", currentUserType.name)
                startActivity(intent)
            } else {
                showNoPermissionDialog()
            }
        }

        // Botón Apertura/Cierre - Visible para todos
        binding.btnAperturaCierre.setOnClickListener {
            startActivity(Intent(this, AperturaCierreActivity::class.java))
        }

        binding.btnMantenimiento.setOnClickListener {
            if (currentUserType.canAccessMaintenance()) {
                startActivity(Intent(this, MantenimientoActivity::class.java))
            } else {
                showNoPermissionDialog()
            }
        }

        // Reimpresión — disponible para todos los tipos de usuario, igual que Salida,
        // ya que cualquiera en caseta puede necesitar reimprimir un ticket o factura.
        binding.btnReimpresion.setOnClickListener {
            startActivity(Intent(this, ReimpresionActivity::class.java))
        }

        // Ticket Extraviado — disponible para todos, mismo criterio que Reimpresión.
        binding.btnTicketPerdido.setOnClickListener {
            startActivity(Intent(this, TicketPerdidoActivity::class.java))
        }
    }

    private fun applyPermissions() {
        // Ocultar Mantenimiento si no es ADMINISTRADOR
        if (!currentUserType.canAccessMaintenance()) {
            binding.btnMantenimiento.visibility = View.GONE
        }

        //  Si es CAJA, ocultar botón de Ingreso
        if (!currentUserType.canAccessEntry()) {
            binding.btnIngresoVehiculo.visibility = View.GONE
        }
    }

    /**
     * El menú lateral reutiliza EXACTAMENTE la acción de cada botón (mismos
     * intents, extras, chequeos de permiso y diálogos). Home es la pantalla
     * raíz: aquí NO se hace finish() al navegar.
     */
    override fun onDrawerItemSelected(itemId: Int) {
        when (itemId) {
            R.id.nav_dashboard -> { /* ya estamos en Home */ }
            R.id.nav_ingreso -> binding.btnIngresoVehiculo.performClick()
            R.id.nav_salida -> binding.btnSalidaVehiculo.performClick()
            R.id.nav_apertura_cierre -> binding.btnAperturaCierre.performClick()
            R.id.nav_reimpresion -> binding.btnReimpresion.performClick()
            R.id.nav_ticket_perdido -> binding.btnTicketPerdido.performClick()
            R.id.nav_mantenimiento -> binding.btnMantenimiento.performClick()
            R.id.nav_cerrar_sesion -> showLogoutDialog()
        }
    }

    private fun showNoPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Acceso Denegado")
            .setMessage("No tienes permisos para acceder a esta sección.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Está seguro que desea cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                logout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun logout() {
        val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
        with(sharedPref.edit()) {
            clear()
            apply()
        }

        // Limpia el nombre/dirección del sitio en memoria — la próxima sesión
        // (posiblemente en otro sitio con el mismo APK) debe recargarlo.
        com.cepa.parqueo.database.SiteConfigCache.clear()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            showLogoutDialog()
        }
    }
}
