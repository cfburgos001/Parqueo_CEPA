package com.parking.system

import android.content.Intent
import android.os.Bundle
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.parking.system.database.ConnectionResult
import com.parking.system.database.DatabaseHelper
import com.parking.system.database.DispositivoManager
import com.parking.system.databinding.ActivityMantenimientoBinding
import kotlinx.coroutines.launch

class MantenimientoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMantenimientoBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var dispositivoManager: DispositivoManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMantenimientoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        databaseHelper = DatabaseHelper(this)
        dispositivoManager = DispositivoManager(this)

        setupUI()
        loadServerConfig()
        loadDispositivoInfo()
        loadTiempoGraciaConfig()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mantenimiento"

        binding.btnGestionUsuarios.setOnClickListener {
            val intent = Intent(this, GestionUsuariosActivity::class.java)
            startActivity(intent)
        }

        // ⭐ NUEVO: Botón para guardar tiempo de gracia
        binding.btnGuardarTiempoGracia.setOnClickListener {
            guardarTiempoGracia()
        }

        binding.btnConfigDispositivo.setOnClickListener {
            mostrarDialogoConfigDispositivo()
        }

        binding.etServerIp.addTextChangedListener {
            binding.tilServerIp.error = null
        }

        binding.etServerPort.addTextChangedListener {
            binding.tilServerPort.error = null
        }

        binding.etDatabaseName.addTextChangedListener {
            binding.tilDatabaseName.error = null
        }

        binding.etDbUsername.addTextChangedListener {
            binding.tilDbUsername.error = null
        }

        binding.etDbPassword.addTextChangedListener {
            binding.tilDbPassword.error = null
        }

        binding.etTiempoGracia.addTextChangedListener {
            binding.tilTiempoGracia.error = null
        }

        binding.btnGuardar.setOnClickListener {
            guardarConfiguracion()
        }

        binding.btnProbarConexion.setOnClickListener {
            probarConexion()
        }

        binding.btnRestaurar.setOnClickListener {
            mostrarDialogoRestaurar()
        }
    }

    /**
     * ⭐ NUEVA FUNCIÓN: Carga la configuración actual del tiempo de gracia
     */
    private fun loadTiempoGraciaConfig() {
        lifecycleScope.launch {
            val idDispositivo = dispositivoManager.obtenerIdDispositivo()
            val tiempoGracia = ParkingConfigHelper.obtenerTiempoGracia(this@MantenimientoActivity, idDispositivo)

            binding.tvTiempoGraciaActual.text = "$tiempoGracia minutos"
            binding.etTiempoGracia.setText(tiempoGracia.toString())
        }
    }

    /**
     * ⭐ NUEVA FUNCIÓN: Guarda el tiempo de gracia configurado
     */
    private fun guardarTiempoGracia() {
        val tiempoTexto = binding.etTiempoGracia.text.toString().trim()

        if (tiempoTexto.isEmpty()) {
            binding.tilTiempoGracia.error = "Ingrese el tiempo de gracia"
            return
        }

        val tiempoGracia = tiempoTexto.toIntOrNull()

        if (tiempoGracia == null || tiempoGracia < 1) {
            binding.tilTiempoGracia.error = "El tiempo debe ser mayor a 0"
            return
        }

        if (tiempoGracia > 120) {
            binding.tilTiempoGracia.error = "El tiempo máximo es 120 minutos (2 horas)"
            return
        }

        // Mostrar confirmación
        AlertDialog.Builder(this)
            .setTitle("Confirmar Cambio")
            .setMessage("¿Desea cambiar el tiempo de gracia a $tiempoGracia minutos?\n\nEsto afectará todas las validaciones de salida y se sincronizará con el servidor.")
            .setPositiveButton("Confirmar") { _, _ ->
                binding.btnGuardarTiempoGracia.isEnabled = false

                lifecycleScope.launch {
                    val idDispositivo = dispositivoManager.obtenerIdDispositivo()
                    val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
                    val usuarioModificacion = sharedPref.getString("nombre_completo", "Sistema")

                    val resultado = ParkingConfigHelper.guardarTiempoGracia(
                        this@MantenimientoActivity,
                        idDispositivo,
                        tiempoGracia,
                        usuarioModificacion
                    )

                    // Actualizar UI
                    binding.tvTiempoGraciaActual.text = "$tiempoGracia minutos"

                    Toast.makeText(
                        this@MantenimientoActivity,
                        "✓ ${resultado.mensaje}",
                        Toast.LENGTH_LONG
                    ).show()

                    binding.btnGuardarTiempoGracia.isEnabled = true
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun loadDispositivoInfo() {
        lifecycleScope.launch {
            val idDispositivo = dispositivoManager.obtenerIdDispositivo()
            val tipoDispositivo = dispositivoManager.obtenerTipoDispositivo()
            val idNumerico = dispositivoManager.obtenerIdNumerico()

            binding.tvIdDispositivo.text = idDispositivo

            if (idNumerico > 0) {
                binding.tvTipoDispositivo.text = "$tipoDispositivo (ID: $idNumerico)"
            } else {
                binding.tvTipoDispositivo.text = "$tipoDispositivo (Sin ID numérico)"
            }
        }
    }

    private fun mostrarDialogoConfigDispositivo() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_config_dispositivo, null)

        val etIdDispositivo = dialogView.findViewById<TextInputEditText>(R.id.etIdDispositivo)
        val etNombreDispositivo = dialogView.findViewById<TextInputEditText>(R.id.etNombreDispositivo)
        val etIdNumerico = dialogView.findViewById<TextInputEditText>(R.id.etIdNumerico)
        val spinnerTipo = dialogView.findViewById<Spinner>(R.id.spinnerTipoDispositivo)

        // Cargar valores actuales
        lifecycleScope.launch {
            val idActual = dispositivoManager.obtenerIdDispositivo()
            val idNumerico = dispositivoManager.obtenerIdNumerico()
            val tipoActual = dispositivoManager.obtenerTipoDispositivo()

            etIdDispositivo.setText(idActual)

            if (idNumerico > 0) {
                etIdNumerico.setText(idNumerico.toString())
            }

            // Pre-seleccionar tipo
            val tipos = resources.getStringArray(R.array.tipos_dispositivo)
            val posicion = tipos.indexOf(tipoActual)
            if (posicion >= 0) {
                spinnerTipo.setSelection(posicion)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Configurar Dispositivo")
            .setView(dialogView)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val btnGuardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnGuardar.setOnClickListener {
                val nuevoId = etIdDispositivo.text.toString().trim()
                val nombre = etNombreDispositivo.text.toString().trim()
                val tipo = spinnerTipo.selectedItem.toString()
                val idNumStr = etIdNumerico.text.toString().trim()

                // Validaciones
                if (nuevoId.isEmpty()) {
                    Toast.makeText(this, "Ingrese el ID del dispositivo", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (nombre.isEmpty()) {
                    Toast.makeText(this, "Ingrese el nombre del dispositivo", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (idNumStr.isEmpty()) {
                    Toast.makeText(this, "Ingrese el ID numérico", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val idNum = idNumStr.toIntOrNull()
                if (idNum == null || idNum <= 0) {
                    Toast.makeText(this, "ID numérico debe ser mayor a 0", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Guardar configuración LOCAL (SharedPreferences)
                val sharedPref = getSharedPreferences("DeviceConfig", MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("id_dispositivo", nuevoId)
                    putInt("id_numerico", idNum)
                    putString("tipo_dispositivo", tipo)
                    apply()
                }

                // Intentar registrar en BD (sin bloquear si falla)
                lifecycleScope.launch {
                    try {
                        dispositivoManager.registrarDispositivoEnBD(
                            nuevoId,
                            nombre,
                            tipo,
                            idNum
                        )

                        Toast.makeText(
                            this@MantenimientoActivity,
                            "✓ Configuración guardada\nID: $nuevoId\nID Numérico: $idNum\nTipo: $tipo",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MantenimientoActivity,
                            "⚠ Configuración guardada localmente\n(No se pudo sincronizar con BD)",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    dialog.dismiss()
                    loadDispositivoInfo()
                }
            }
        }

        dialog.show()
    }

    private fun loadServerConfig() {
        val sharedPref = getSharedPreferences("ServerConfig", MODE_PRIVATE)

        binding.etServerIp.setText(sharedPref.getString("server_ip", "10.0.1.39"))
        binding.etServerPort.setText(sharedPref.getString("server_port", "1433"))
        binding.etDatabaseName.setText(sharedPref.getString("database_name", "Datapark"))
        binding.etDbUsername.setText(sharedPref.getString("db_username", "pos"))
        binding.etDbPassword.setText(sharedPref.getString("db_password", "Po\$2025#"))
    }

    private fun guardarConfiguracion() {
        val serverIp = binding.etServerIp.text.toString().trim()
        val serverPort = binding.etServerPort.text.toString().trim()
        val databaseName = binding.etDatabaseName.text.toString().trim()
        val dbUsername = binding.etDbUsername.text.toString().trim()
        val dbPassword = binding.etDbPassword.text.toString().trim()

        if (serverIp.isEmpty()) {
            binding.tilServerIp.error = "Ingrese la IP del servidor"
            return
        }

        if (serverPort.isEmpty()) {
            binding.tilServerPort.error = "Ingrese el puerto"
            return
        }

        if (databaseName.isEmpty()) {
            binding.tilDatabaseName.error = "Ingrese el nombre de la base de datos"
            return
        }

        if (dbUsername.isEmpty()) {
            binding.tilDbUsername.error = "Ingrese el usuario de la BD"
            return
        }

        if (!isValidIP(serverIp)) {
            binding.tilServerIp.error = "Formato de IP inválido"
            return
        }

        val port = serverPort.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            binding.tilServerPort.error = "Puerto inválido (1-65535)"
            return
        }

        val sharedPref = getSharedPreferences("ServerConfig", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("server_ip", serverIp)
            putString("server_port", serverPort)
            putString("database_name", databaseName)
            putString("db_username", dbUsername)
            putString("db_password", dbPassword)
            apply()
        }

        Toast.makeText(this, "✓ Configuración guardada correctamente", Toast.LENGTH_SHORT).show()
    }

    private fun probarConexion() {
        val serverIp = binding.etServerIp.text.toString().trim()
        val serverPort = binding.etServerPort.text.toString().trim()
        val databaseName = binding.etDatabaseName.text.toString().trim()

        if (serverIp.isEmpty() || serverPort.isEmpty() || databaseName.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos primero", Toast.LENGTH_SHORT).show()
            return
        }

        guardarConfiguracion()

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Probando Conexión")
            .setMessage("Conectando a $serverIp:$serverPort/$databaseName...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            val result = databaseHelper.testConnection()

            progressDialog.dismiss()

            when (result) {
                is ConnectionResult.Success -> {
                    AlertDialog.Builder(this@MantenimientoActivity)
                        .setTitle("✓ Conexión Exitosa")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
                is ConnectionResult.Error -> {
                    AlertDialog.Builder(this@MantenimientoActivity)
                        .setTitle("✗ Error de Conexión")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun mostrarDialogoRestaurar() {
        AlertDialog.Builder(this)
            .setTitle("Restaurar Valores")
            .setMessage("¿Desea restaurar los valores por defecto?")
            .setPositiveButton("Restaurar") { _, _ ->
                restaurarValoresPorDefecto()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun restaurarValoresPorDefecto() {
        binding.etServerIp.setText("10.0.1.39")
        binding.etServerPort.setText("1433")
        binding.etDatabaseName.setText("Datapark")
        binding.etDbUsername.setText("pos")
        binding.etDbPassword.setText("Po\$2025#")

        Toast.makeText(this, "Valores restaurados (no guardados)", Toast.LENGTH_SHORT).show()
    }

    private fun isValidIP(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false

        return parts.all { part ->
            val num = part.toIntOrNull()
            num != null && num in 0..255
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}