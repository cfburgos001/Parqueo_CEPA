package com.cepa.parqueo

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cepa.parqueo.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var username: String
    private lateinit var userType: UserType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra("USERNAME") ?: "Usuario"
        val userTypeString = intent.getStringExtra("USER_TYPE") ?: "OPERADOR"
        userType = UserType.valueOf(userTypeString)

        setupUI()
        setupBottomNavigation()
        applyPermissions()
    }

    private fun setupUI() {
        binding.tvWelcome.text = "Bienvenido, $username"

        binding.btnIngresoVehiculo.setOnClickListener {
            if (userType.canAccessEntry()) {
                startActivity(Intent(this, IngresoVehiculoActivity::class.java))
            } else {
                showNoPermissionDialog()
            }
        }

        binding.btnSalidaVehiculo.setOnClickListener {
            if (userType.canAccessExit()) {
                // Pasar el tipo de usuario a SalidaVehiculoActivity
                val intent = Intent(this, SalidaVehiculoActivity::class.java)
                intent.putExtra("USER_TYPE", userType.name)
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
            if (userType.canAccessMaintenance()) {
                startActivity(Intent(this, MantenimientoActivity::class.java))
            } else {
                showNoPermissionDialog()
            }
        }

        binding.btnCerrarSesion.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun applyPermissions() {
        // Ocultar Mantenimiento si no es ADMINISTRADOR
        if (!userType.canAccessMaintenance()) {
            binding.btnMantenimiento.visibility = View.GONE
        }

        // ⭐ NUEVO: Si es CAJA, ocultar botón de Ingreso
        if (!userType.canAccessEntry()) {
            binding.btnIngresoVehiculo.visibility = View.GONE
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_ingreso -> {
                    if (userType.canAccessEntry()) {
                        startActivity(Intent(this, IngresoVehiculoActivity::class.java))
                    } else {
                        showNoPermissionDialog()
                    }
                    true
                }
                R.id.nav_salida -> {
                    if (userType.canAccessExit()) {
                        val intent = Intent(this, SalidaVehiculoActivity::class.java)
                        intent.putExtra("USER_TYPE", userType.name)
                        startActivity(intent)
                    } else {
                        showNoPermissionDialog()
                    }
                    true
                }
                R.id.nav_mantenimiento -> {
                    if (userType.canAccessMaintenance()) {
                        startActivity(Intent(this, MantenimientoActivity::class.java))
                    } else {
                        showNoPermissionDialog()
                    }
                    true
                }
                R.id.nav_home -> true
                else -> false
            }
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_home

        // Ocultar opciones del menú inferior según permisos
        if (!userType.canAccessMaintenance()) {
            binding.bottomNavigation.menu.removeItem(R.id.nav_mantenimiento)
        }

        // ⭐ NUEVO: Si es CAJA, ocultar opción de Ingreso del menú inferior
        if (!userType.canAccessEntry()) {
            binding.bottomNavigation.menu.removeItem(R.id.nav_ingreso)
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

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        showLogoutDialog()
    }
}