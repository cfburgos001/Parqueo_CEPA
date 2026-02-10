package com.cepa.parqueo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.ConnectionResult
import com.cepa.parqueo.database.DatabaseHelper
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.database.ListaTarifasResult
import com.cepa.parqueo.database.ModoCobroResult
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.databinding.ActivityMantenimientoBinding
import kotlinx.coroutines.launch

/**
 * VERSIÓN 2: Con gestión de tarifas (Escalonado/Sin Máximo)
 */
class MantenimientoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMantenimientoBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var dispositivoManager: DispositivoManager
    private lateinit var vehiculoRepository: VehiculoRepository  // 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMantenimientoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        databaseHelper = DatabaseHelper(this)
        dispositivoManager = DispositivoManager(this)
        vehiculoRepository = VehiculoRepository(this)  // 

        setupUI()
        loadServerConfig()
        loadDispositivoInfo()
        loadTiempoGraciaConfig()
        loadConfigCobrosPos()
        loadModoCobro()  // 
        loadTarifas()    // 
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mantenimiento"

        binding.btnGestionUsuarios.setOnClickListener {
            val intent = Intent(this, GestionUsuariosActivity::class.java)
            startActivity(intent)
        }

        binding.btnGuardarTiempoGracia.setOnClickListener {
            guardarTiempoGracia()
        }

        binding.switchCobrosPos.setOnCheckedChangeListener { _, isChecked ->
            guardarConfigCobrosPos(isChecked)
        }

        //  Switch para Modo de Cobro
        binding.switchModoCobro.setOnCheckedChangeListener { _, isChecked ->
            cambiarModoCobro(isChecked)
        }

        //  Botón para recargar tarifas
        binding.btnRecargarTarifas.setOnClickListener {
            loadTarifas()
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
        // ⭐ NUEVO: Botón para configurar logs de apertura manual
        binding.btnConfigLogsApertura.setOnClickListener {
            val intent = Intent(this, ConfigurarLogsAperturaActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     *  Carga el modo de cobro actual (Escalonado/Sin Máximo)
     */
    private fun loadModoCobro() {
        lifecycleScope.launch {
            when (val result = vehiculoRepository.obtenerModoCobro()) {
                is ModoCobroResult.Success -> {
                    // Actualizar switch SIN disparar el listener
                    binding.switchModoCobro.setOnCheckedChangeListener(null)
                    binding.switchModoCobro.isChecked = result.cobroIndefinido
                    binding.switchModoCobro.setOnCheckedChangeListener { _, isChecked ->
                        cambiarModoCobro(isChecked)
                    }

                    // Actualizar texto de estado
                    actualizarTextoModoCobro(result.cobroIndefinido, result.modoTexto)
                }
                is ModoCobroResult.Error -> {
                    Toast.makeText(
                        this@MantenimientoActivity,
                        "⚠ Error al cargar modo de cobro: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Valores por defecto
                    binding.switchModoCobro.isChecked = false
                    actualizarTextoModoCobro(false, "Escalonado (Con Tope Diario)")
                }
            }
        }
    }

    /**
     *  Actualiza el texto del estado del modo de cobro
     */
    private fun actualizarTextoModoCobro(cobroIndefinido: Boolean, modoTexto: String) {
        binding.tvEstadoModoCobro.text = if (cobroIndefinido) {
            "✓ Modo Activo: SIN MÁXIMO\nEl cobro es por hora sin límite de tope diario"
        } else {
            "✓ Modo Activo: ESCALONADO\nCon tope diario según tipo de vehículo"
        }

        binding.tvEstadoModoCobro.setTextColor(
            if (cobroIndefinido) {
                getColor(android.R.color.holo_orange_dark)
            } else {
                getColor(android.R.color.holo_green_dark)
            }
        )
    }

    /**
     *  Cambia el modo de cobro
     */
    private fun cambiarModoCobro(cobroIndefinido: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Cambio de Modo")
            .setMessage(
                if (cobroIndefinido) {
                    "¿Cambiar a modo SIN MÁXIMO?\n\n" +
                            "• El cobro será por hora sin límite\n" +
                            "• No habrá tope diario\n" +
                            "• Aplica para todos los tipos de vehículo\n" +
                            "• Este cambio afecta a TODOS los POS conectados"
                } else {
                    "¿Cambiar a modo ESCALONADO?\n\n" +
                            "• Tarifa escalonada con tope diario\n" +
                            "• Auto: hasta $3.75/día\n" +
                            "• Moto: hasta $3.00/día\n" +
                            "• Camión: hasta $6.00/día\n" +
                            "• Este cambio afecta a TODOS los POS conectados"
                }
            )
            .setPositiveButton("Confirmar") { _, _ ->
                ejecutarCambioModoCobro(cobroIndefinido)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                // Revertir switch
                loadModoCobro()
            }
            .show()
    }

    /**
     *  Ejecuta el cambio de modo de cobro en BD
     */
    private fun ejecutarCambioModoCobro(cobroIndefinido: Boolean) {
        binding.switchModoCobro.isEnabled = false

        lifecycleScope.launch {
            val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
            val usuarioModificacion = sharedPref.getString("nombre_completo", "Sistema")

            when (val result = vehiculoRepository.cambiarModoCobro(cobroIndefinido, usuarioModificacion)) {
                is com.cepa.parqueo.database.DatabaseResult.Success -> {
                    Toast.makeText(
                        this@MantenimientoActivity,
                        "✓ ${result.message}\n\nTodos los POS conectados usarán este modo",
                        Toast.LENGTH_LONG
                    ).show()

                    // Recargar estado
                    loadModoCobro()
                    loadTarifas()
                }
                is com.cepa.parqueo.database.DatabaseResult.Error -> {
                    Toast.makeText(
                        this@MantenimientoActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Revertir switch
                    loadModoCobro()
                }
            }

            binding.switchModoCobro.isEnabled = true
        }
    }

    /**
     *  Carga y muestra las tarifas actuales
     */
    private fun loadTarifas() {
        binding.progressBarTarifas?.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = vehiculoRepository.listarTarifas()) {
                is ListaTarifasResult.Success -> {
                    val tarifas = result.tarifas

                    val tarifasTexto = buildString {
                        append("📊 TARIFAS ACTUALES:\n\n")

                        tarifas.forEach { tarifa ->
                            val icono = when (tarifa.strRateKey) {
                                "A" -> "🚗"
                                "M" -> "🏍️"
                                "C" -> "🚚"
                                else -> "🚙"
                            }

                            append("$icono ${tarifa.tipoTarifa}\n")
                            append("   Precio: $${String.format("%.2f", tarifa.precioPorHora)}/hora\n")

                            if (tarifa.cobroIndefinido) {
                                append("   Modo: Sin Máximo\n")
                            } else {
                                append("   Modo: Escalonado\n")
                                append("   • 1h: $${String.format("%.2f", tarifa.precio1Hora)}\n")
                                append("   • 2h: $${String.format("%.2f", tarifa.precio2Horas)}\n")
                                append("   • Tope: $${String.format("%.2f", tarifa.precioMax)}/día\n")
                            }
                            append("\n")
                        }
                    }

                    binding.tvTarifasActuales?.text = tarifasTexto
                    binding.tvTarifasActuales?.visibility = View.VISIBLE
                    binding.progressBarTarifas?.visibility = View.GONE
                }
                is ListaTarifasResult.Error -> {
                    Toast.makeText(
                        this@MantenimientoActivity,
                        "⚠ Error al cargar tarifas: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    binding.tvTarifasActuales?.text = "Error al cargar tarifas"
                    binding.tvTarifasActuales?.visibility = View.VISIBLE
                    binding.progressBarTarifas?.visibility = View.GONE
                }
            }
        }
    }

    // ===== MANTENER MÉTODOS EXISTENTES =====
    // (Los demás métodos permanecen igual)

    private fun loadConfigCobrosPos() {
        val cobrosHabilitados = dispositivoManager.estanCobrosHabilitados()

        binding.switchCobrosPos.setOnCheckedChangeListener(null)
        binding.switchCobrosPos.isChecked = cobrosHabilitados
        binding.switchCobrosPos.setOnCheckedChangeListener { _, isChecked ->
            guardarConfigCobrosPos(isChecked)
        }

        actualizarTextoEstadoCobros(cobrosHabilitados)
    }

    private fun guardarConfigCobrosPos(habilitado: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Cambio")
            .setMessage(
                if (habilitado) {
                    "¿Habilitar cobros en este POS?\n\n" +
                            "Los operadores podrán procesar pagos desde esta terminal."
                } else {
                    "¿Deshabilitar cobros en este POS?\n\n" +
                            "⚠️ IMPORTANTE: Los operadores NO podrán cobrar desde esta terminal.\n" +
                            "• Los vehículos deberán pagar en la PayStation\n" +
                            "• La validación de tiempo de gracia se mantendrá ACTIVA\n" +
                            "• Se seguirán generando reingresos si exceden el tiempo de gracia"
                }
            )
            .setPositiveButton("Confirmar") { _, _ ->
                dispositivoManager.configurarCobrosHabilitados(habilitado)
                actualizarTextoEstadoCobros(habilitado)

                Toast.makeText(
                    this,
                    if (habilitado) {
                        "✓ Cobros HABILITADOS en este POS"
                    } else {
                        "⚠️ Cobros DESHABILITADOS en este POS\n" +
                                "Validación de tiempo de gracia: ACTIVA"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                loadConfigCobrosPos()
            }
            .show()
    }

    private fun actualizarTextoEstadoCobros(habilitado: Boolean) {
        binding.tvEstadoCobrosPos.text = if (habilitado) {
            "✓ Este POS puede procesar pagos"
        } else {
            "⚠️ Cobros deshabilitados (solo validación de salidas)\n" +
                    "Validación de tiempo de gracia: ACTIVA"
        }

        binding.tvEstadoCobrosPos.setTextColor(
            if (habilitado) {
                getColor(android.R.color.holo_green_dark)
            } else {
                getColor(android.R.color.holo_orange_dark)
            }
        )
    }

    private fun loadTiempoGraciaConfig() {
        lifecycleScope.launch {
            val idDispositivo = dispositivoManager.obtenerIdDispositivo()
            val tiempoGracia = ParkingConfigHelper.obtenerTiempoGracia(this@MantenimientoActivity, idDispositivo)

            binding.tvTiempoGraciaActual.text = "$tiempoGracia minutos"
            binding.etTiempoGracia.setText(tiempoGracia.toString())
        }
    }

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

        val etIdDispositivo = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIdDispositivo)
        val etNombreDispositivo = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombreDispositivo)
        val etIdNumerico = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIdNumerico)
        val etIdEntryDevice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIdEntryDevice)
        val etIdExitDevice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etIdExitDevice)
        val spinnerTipo = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerTipoDispositivo)

        lifecycleScope.launch {
            val idActual = dispositivoManager.obtenerIdDispositivo()
            val idNumerico = dispositivoManager.obtenerIdNumerico()
            val idEntryDevice = dispositivoManager.obtenerIdEntryDevice()
            val idExitDevice = dispositivoManager.obtenerIdExitDevice()
            val tipoActual = dispositivoManager.obtenerTipoDispositivo()

            etIdDispositivo.setText(idActual)

            if (idNumerico > 0) {
                etIdNumerico.setText(idNumerico.toString())
            }

            if (idEntryDevice > 0) {
                etIdEntryDevice.setText(idEntryDevice.toString())
            }

            if (idExitDevice > 0) {
                etIdExitDevice.setText(idExitDevice.toString())
            }

            val tipos = resources.getStringArray(R.array.tipos_dispositivo)
            val posicion = tipos.indexOf(tipoActual)
            if (posicion >= 0) {
                spinnerTipo.setSelection(posicion)
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Configurar Dispositivo")
            .setView(dialogView)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val btnGuardar = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btnGuardar.setOnClickListener {
                val nuevoId = etIdDispositivo.text.toString().trim()
                val nombre = etNombreDispositivo.text.toString().trim()
                val tipo = spinnerTipo.selectedItem.toString()
                val idNumStr = etIdNumerico.text.toString().trim()
                val idEntryStr = etIdEntryDevice.text.toString().trim()
                val idExitStr = etIdExitDevice.text.toString().trim()

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

                val idEntry = if (idEntryStr.isEmpty()) 1 else {
                    idEntryStr.toIntOrNull() ?: run {
                        Toast.makeText(this, "ID Entry Device debe ser un número válido", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }

                val idExit = if (idExitStr.isEmpty()) 2 else {
                    idExitStr.toIntOrNull() ?: run {
                        Toast.makeText(this, "ID Exit Device debe ser un número válido", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }

                val sharedPref = getSharedPreferences("DeviceConfig", MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("id_dispositivo", nuevoId)
                    putInt("id_numerico", idNum)
                    putInt("id_entry_device", idEntry)
                    putInt("id_exit_device", idExit)
                    putString("tipo_dispositivo", tipo)
                    apply()
                }

                lifecycleScope.launch {
                    try {
                        dispositivoManager.registrarDispositivoEnBD(
                            nuevoId,
                            nombre,
                            tipo,
                            idNum,
                            idEntry,
                            idExit
                        )

                        Toast.makeText(
                            this@MantenimientoActivity,
                            "✓ Configuración guardada\n" +
                                    "ID: $nuevoId\n" +
                                    "ID Numérico: $idNum\n" +
                                    "ID Entry: $idEntry | ID Exit: $idExit\n" +
                                    "Tipo: $tipo",
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