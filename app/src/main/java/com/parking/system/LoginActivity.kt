package com.parking.system

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.parking.system.database.OperadorRepository
import com.parking.system.database.OperadorResult
import com.parking.system.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var operadorRepository: OperadorRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        operadorRepository = OperadorRepository(this)

        setupUI()

        // Botón de debug (mantén presionado el botón de login)
        binding.btnLogin.setOnLongClickListener {
            mostrarInfoConexion()
            true
        }
    }

    private fun setupUI() {
        binding.etUsername.addTextChangedListener {
            binding.tilUsername.error = null
        }

        binding.etPassword.addTextChangedListener {
            binding.tilPassword.error = null
        }

        binding.btnLogin.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty()) {
            binding.tilUsername.error = "Ingrese el usuario"
            return
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingrese la contraseña"
            return
        }

        binding.btnLogin.isEnabled = false

        // Mostrar progreso
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Iniciando sesión")
            .setMessage("Conectando a Datapark...\nUsuario: $username")
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            try {
                when (val result = operadorRepository.validarOperador(username, password)) {
                    is OperadorResult.Success -> {
                        progressDialog.dismiss()

                        val operador = result.operador

                        Toast.makeText(
                            this@LoginActivity,
                            "Bienvenido, ${operador.getNombreCompleto()}",
                            Toast.LENGTH_SHORT
                        ).show()

                        saveSession(operador)

                        val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                        intent.putExtra("USER_TYPE", operador.tipoUsuario)
                        intent.putExtra("USERNAME", operador.getNombreCompleto())
                        intent.putExtra("ID_OPERADOR", operador.id)
                        startActivity(intent)
                        finish()
                    }
                    is OperadorResult.Error -> {
                        progressDialog.dismiss()

                        // Mostrar error detallado
                        AlertDialog.Builder(this@LoginActivity)
                            .setTitle("Error de Login")
                            .setMessage("${result.message}\n\nUsuario: $username\nBD: Datapark (10.0.1.39)")
                            .setPositiveButton("Reintentar") { _, _ ->
                                binding.btnLogin.isEnabled = true
                            }
                            .setNegativeButton("Ver Configuración") { _, _ ->
                                mostrarInfoConexion()
                                binding.btnLogin.isEnabled = true
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()

                AlertDialog.Builder(this@LoginActivity)
                    .setTitle("Error Crítico")
                    .setMessage("Error inesperado: ${e.message}\n\n${e.stackTraceToString().take(200)}")
                    .setPositiveButton("OK") { _, _ ->
                        binding.btnLogin.isEnabled = true
                    }
                    .show()
            }
        }
    }

    private fun mostrarInfoConexion() {
        val sharedPref = getSharedPreferences("ServerConfig", MODE_PRIVATE)

        val info = buildString {
            appendLine("📋 CONFIGURACIÓN ACTUAL:")
            appendLine()
            appendLine("Servidor: ${sharedPref.getString("server_ip", "NO CONFIG")}")
            appendLine("Puerto: ${sharedPref.getString("server_port", "NO CONFIG")}")
            appendLine("Base de Datos: ${sharedPref.getString("database_name", "NO CONFIG")}")
            appendLine("Usuario BD: ${sharedPref.getString("db_username", "NO CONFIG")}")
            appendLine("Contraseña: ${if (sharedPref.getString("db_password", "")?.isNotEmpty() == true) "****" else "NO CONFIG"}")
            appendLine()
            appendLine("🎯 CONFIGURACIÓN ESPERADA:")
            appendLine()
            appendLine("Servidor: 10.0.1.39")
            appendLine("Puerto: 1433")
            appendLine("Base de Datos: Datapark")
            appendLine("Usuario BD: pos")
            appendLine("Contraseña: Po\$2025#")
        }

        AlertDialog.Builder(this)
            .setTitle("Debug - Configuración")
            .setMessage(info)
            .setPositiveButton("Ir a Mantenimiento") { _, _ ->
                // Navegar a mantenimiento sin login
                val intent = Intent(this, MantenimientoActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun saveSession(operador: com.parking.system.database.Operador) {
        val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("username", operador.username)
            putString("nombre_completo", operador.getNombreCompleto())
            putInt("id_operador", operador.id)
            putString("userType", operador.tipoUsuario)
            putBoolean("isLoggedIn", true)
            apply()
        }
    }
}