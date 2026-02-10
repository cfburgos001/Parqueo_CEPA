package com.cepa.parqueo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.database.PagoSinCobroResult
import com.cepa.parqueo.database.VehiculoDB
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.database.VehiculoResult
import com.cepa.parqueo.databinding.ActivitySalidaVehiculoBinding
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Activity para registrar salida de vehículos
 * VERSIÓN 4: Usuario CAJA solo procesa pagos, NO abre pluma
 */
class SalidaVehiculoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalidaVehiculoBinding
    private lateinit var vehiculoRepository: VehiculoRepository
    private lateinit var dispositivoManager: DispositivoManager

    // Variables para usuario y vehículo actual
    private var tipoUsuario: String = ""
    private var idOperador: Int = 0
    private var vehiculoActual: VehiculoDB? = null

    private var nombreOperador: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalidaVehiculoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)
        dispositivoManager = DispositivoManager(this)

        // Cargar datos de sesión
        cargarDatosSesion()

        verificarTipoDispositivo()
        setupUI()

        // VALIDAR APERTURA AL INICIAR
        validarAperturaActiva()
    }

    /**
     * Cargar datos de la sesión actual
     */
    private fun cargarDatosSesion() {
        val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
        tipoUsuario = sharedPref.getString("userType", "") ?: ""
        idOperador = sharedPref.getInt("id_operador", 0)
        nombreOperador = sharedPref.getString("nombre_operador", "") ?: ""
        // Si no existe, concatenar desde nombre + apellido
        if (nombreOperador.isEmpty()) {
            val nombre = sharedPref.getString("nombre", "") ?: ""
            val apellido = sharedPref.getString("apellido", "") ?: ""
            nombreOperador = "$nombre $apellido".trim()
        }

        android.util.Log.d("SalidaVehiculo", "Tipo de usuario: $tipoUsuario, ID: $idOperador")
    }

    /**
     * Validar que haya apertura antes de permitir operaciones
     */
    private fun validarAperturaActiva() {
        binding.btnRegistrarPorPlaca.isEnabled = false
        binding.btnEscanearQR.isEnabled = false
        binding.etPlaca.isEnabled = false
        binding.btnSalidaSinCobro.isEnabled = false

        lifecycleScope.launch {
            val hayApertura = AperturaValidator.hayAperturaActiva(this@SalidaVehiculoActivity)

            if (!hayApertura) {
                // NO HAY APERTURA - Bloquear todo
                mostrarDialogoSinApertura()
            } else {
                // SÍ HAY APERTURA - Permitir operaciones
                binding.btnRegistrarPorPlaca.isEnabled = true
                binding.btnEscanearQR.isEnabled = true
                binding.etPlaca.isEnabled = true

                // Mostrar botón de pago sin cobro solo para CAJA
                if (tipoUsuario == "CAJA") {
                    binding.btnSalidaSinCobro.visibility = View.VISIBLE
                    binding.tvInfoSinCobro.visibility = View.VISIBLE
                    binding.btnSalidaSinCobro.isEnabled = false // Se habilita cuando hay placa
                }
            }
        }
    }

    /**
     * Mostrar diálogo cuando no hay apertura
     */
    private fun mostrarDialogoSinApertura() {
        AlertDialog.Builder(this)
            .setTitle("Apertura Requerida")
            .setMessage(AperturaValidator.MENSAJE_SIN_APERTURA)
            .setCancelable(false)
            .setPositiveButton("Ir a Apertura/Cierre") { _, _ ->
                finish()
            }
            .setNegativeButton("Salir") { _, _ ->
                finish()
            }
            .show()
    }

    private fun verificarTipoDispositivo() {
        if (!dispositivoManager.puedeRegistrarSalida()) {
            Toast.makeText(
                this,
                "⚠ Este dispositivo está configurado como ENTRADA\nNo puede registrar salidas",
                Toast.LENGTH_LONG
            ).show()

            binding.btnRegistrarPorPlaca.isEnabled = false
            binding.btnEscanearQR.isEnabled = false
            binding.etPlaca.isEnabled = false
            binding.btnSalidaSinCobro.isEnabled = false
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (tipoUsuario == "CAJA") "Procesar Pago" else "Registro de Salida"

        binding.btnRegistrarPorPlaca.setOnClickListener {
            procesarBusqueda()
        }

        binding.btnEscanearQR.setOnClickListener {
            iniciarEscaner()
        }

        // Botón de Pago Sin Cobro (solo para CAJA)
        binding.btnSalidaSinCobro.setOnClickListener {
            procesarPagoSinCobro()
        }

        binding.etPlaca.addTextChangedListener { text ->
            val upperCase = text.toString().uppercase()
            if (text.toString() != upperCase) {
                binding.etPlaca.setText(upperCase)
                binding.etPlaca.setSelection(upperCase.length)
            }

            // Habilitar/deshabilitar botón de pago sin cobro
            if (tipoUsuario == "CAJA") {
                binding.btnSalidaSinCobro.isEnabled = upperCase.length >= 4
            }
        }

        binding.etPlaca.addTextChangedListener {
            binding.tilPlaca.error = null
        }
        // ⭐ NUEVO: Botón de Apertura Manual de Barrera (SALIDA)
        binding.btnAperturaManualSalida.setOnClickListener {
            AperturaManualDialog.mostrar(
                activity = this,
                lifecycleScope = lifecycleScope,
                contexto = "SALIDA",
                idOperador = idOperador,
                nombreOperador = nombreOperador
            )
        }
    }

    /**
     * Procesar pago sin cobro (cortesía) - Solo marca como pagado, NO abre pluma
     */
    private fun procesarPagoSinCobro() {
        val placa = binding.etPlaca.text.toString().trim()

        if (placa.isEmpty()) {
            binding.tilPlaca.error = "Ingrese la placa del vehículo"
            return
        }

        if (placa.length < 4) {
            binding.tilPlaca.error = "Placa inválida"
            return
        }

        // Primero buscar el vehículo para confirmar
        buscarVehiculoParaPagoSinCobro(placa)
    }

    /**
     * Buscar vehículo para pago sin cobro
     */
    private fun buscarVehiculoParaPagoSinCobro(placa: String) {
        binding.btnSalidaSinCobro.isEnabled = false
        binding.btnRegistrarPorPlaca.isEnabled = false

        lifecycleScope.launch {
            when (val result = vehiculoRepository.buscarVehiculoPorPlaca(placa)) {
                is VehiculoResult.Found -> {
                    vehiculoActual = result.vehiculo

                    // Verificar si ya está pagado
                    if (result.vehiculo.bitPaid == 1) {
                        Toast.makeText(
                            this@SalidaVehiculoActivity,
                            "⚠ Este vehículo ya tiene un pago registrado",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnSalidaSinCobro.isEnabled = true
                        binding.btnRegistrarPorPlaca.isEnabled = true
                        return@launch
                    }

                    confirmarPagoSinCobro(result.vehiculo)
                }
                is VehiculoResult.NotFound -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "⚠ Vehículo no encontrado o ya salió del parqueo",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnSalidaSinCobro.isEnabled = true
                    binding.btnRegistrarPorPlaca.isEnabled = true
                }
                is VehiculoResult.Error -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnSalidaSinCobro.isEnabled = true
                    binding.btnRegistrarPorPlaca.isEnabled = true
                }
            }
        }
    }

    /**
     * Mostrar diálogo de confirmación para pago sin cobro
     */
    private fun confirmarPagoSinCobro(vehiculo: VehiculoDB) {
        val ahora = Date()
        val tiempoMinutos = ((ahora.time - vehiculo.fechaEntrada.time) / 60000).toInt()
        val tiempoTexto = if (tiempoMinutos >= 60) {
            "${tiempoMinutos / 60}h ${tiempoMinutos % 60}min"
        } else {
            "${tiempoMinutos} minutos"
        }

        // Lista de motivos predefinidos
        val motivos = arrayOf(
            "Proveedor",
            "Visita autorizada",
            "Personal de mantenimiento",
            "Emergencia",
            "Cortesía especial",
            "Otro"
        )

        var motivoSeleccionado = motivos[0]

        AlertDialog.Builder(this)
            .setTitle("🎁 Pago Sin Cobro (Cortesía)")
            .setMessage(
                "¿Confirmar PAGO SIN COBRO para:\n\n" +
                        "Placa: ${vehiculo.placa}\n" +
                        "Tiempo de estancia: $tiempoTexto\n\n" +
                        "⚠️ Se marcará como PAGADO (\$0.00)\n" +
                        "El operador deberá validar la salida después."
            )
            .setSingleChoiceItems(motivos, 0) { _, which ->
                motivoSeleccionado = motivos[which]
            }
            .setPositiveButton("✓ Confirmar Pago") { _, _ ->
                ejecutarPagoSinCobro(vehiculo.placa, motivoSeleccionado)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                binding.btnSalidaSinCobro.isEnabled = true
                binding.btnRegistrarPorPlaca.isEnabled = true
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Ejecutar el pago sin cobro - NO abre pluma
     */
    private fun ejecutarPagoSinCobro(placa: String, motivo: String) {
        lifecycleScope.launch {
            // Obtener IdPayDevice desde configuración
            val sharedPref = getSharedPreferences("DeviceConfig", MODE_PRIVATE)
            val idPayDevice = sharedPref.getInt("id_numerico", 3)

            when (val result = vehiculoRepository.marcarPagoSinCobro(
                placa = placa,
                idPayDevice = idPayDevice,
                idOperador = idOperador,
                motivo = motivo
            )) {
                is PagoSinCobroResult.Success -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "✓ Pago sin cobro registrado\n" +
                                "Placa: $placa\n" +
                                "Motivo: $motivo\n\n" +
                                "El operador debe validar la salida.",
                        Toast.LENGTH_LONG
                    ).show()

                    android.util.Log.d("SalidaVehiculo", "Pago sin cobro exitoso - ID: ${result.idVehiculo}, IdPayDevice: ${result.idPayDevice}")

                    // Limpiar campos (NO abrimos pluma)
                    limpiarCampos()
                }
                is PagoSinCobroResult.Error -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    binding.btnSalidaSinCobro.isEnabled = true
                    binding.btnRegistrarPorPlaca.isEnabled = true
                }
            }
        }
    }

    /**
     * Limpiar campos después de registrar pago sin cobro
     */
    private fun limpiarCampos() {
        binding.etPlaca.text?.clear()
        binding.btnSalidaSinCobro.isEnabled = false
        binding.btnRegistrarPorPlaca.isEnabled = true
        vehiculoActual = null
    }

    private fun procesarBusqueda() {
        val placa = binding.etPlaca.text.toString().trim()

        if (placa.isEmpty()) {
            binding.tilPlaca.error = "Ingrese la placa del vehículo"
            return
        }

        if (placa.length < 4) {
            binding.tilPlaca.error = "Placa inválida"
            return
        }

        buscarVehiculoPorPlaca(placa)
    }

    private fun buscarVehiculoPorPlaca(placa: String) {
        binding.btnRegistrarPorPlaca.isEnabled = false

        lifecycleScope.launch {
            when (val result = vehiculoRepository.buscarVehiculoPorPlaca(placa)) {
                is VehiculoResult.Found -> {
                    procesarVehiculo(result.vehiculo)
                }
                is VehiculoResult.NotFound -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "⚠ Vehículo no encontrado o ya salió del parqueo",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnRegistrarPorPlaca.isEnabled = true
                }
                is VehiculoResult.Error -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnRegistrarPorPlaca.isEnabled = true
                }
            }
        }
    }

    private fun buscarVehiculoPorCodigo(codigo: String) {
        lifecycleScope.launch {
            when (val result = vehiculoRepository.buscarVehiculoPorCodigo(codigo)) {
                is VehiculoResult.Found -> {
                    procesarVehiculo(result.vehiculo)
                }
                is VehiculoResult.NotFound -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "⚠ Ticket no encontrado o vehículo ya salió",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is VehiculoResult.Error -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun procesarVehiculo(vehiculo: VehiculoDB) {
        android.util.Log.d("SalidaVehiculo", "=== DATOS DEL VEHÍCULO ===")
        android.util.Log.d("SalidaVehiculo", "Placa: ${vehiculo.placa}")
        android.util.Log.d("SalidaVehiculo", "bitPaid: ${vehiculo.bitPaid}")
        android.util.Log.d("SalidaVehiculo", "Monto: ${vehiculo.monto}")
        android.util.Log.d("SalidaVehiculo", "FechaPago: ${vehiculo.fechaPago}")
        android.util.Log.d("SalidaVehiculo", "TipoUsuario: $tipoUsuario")
        android.util.Log.d("SalidaVehiculo", "========================")

        val ahora = Date()
        val tiempoMinutos = ((ahora.time - vehiculo.fechaEntrada.time) / 60000).toInt()

        // ⭐ USUARIO CAJA: Solo puede procesar pagos, NO puede abrir pluma
        if (tipoUsuario == "CAJA") {
            if (vehiculo.bitPaid == 1) {
                // Ya está pagado - CAJA no puede hacer nada más
                Toast.makeText(
                    this,
                    "✓ Este vehículo ya está PAGADO\n" +
                            "Placa: ${vehiculo.placa}\n" +
                            "Monto: \$${String.format("%.2f", vehiculo.monto)}\n\n" +
                            "El operador debe validar la salida.",
                    Toast.LENGTH_LONG
                ).show()
                binding.btnRegistrarPorPlaca.isEnabled = true
            } else {
                // No está pagado - CAJA procesa el pago
                abrirPantallaPagoCaja(vehiculo, tiempoMinutos)
            }
            return
        }

        // ⭐ OTROS USUARIOS (OPERADOR, ADMINISTRADOR): Flujo normal
        val cobrosHabilitados = dispositivoManager.estanCobrosHabilitados()

        if (!cobrosHabilitados) {
            android.util.Log.d("SalidaVehiculo", "Cobros deshabilitados - Saltando pantalla de pago")

            Toast.makeText(
                this,
                "ℹ️ Este POS no procesa pagos\n" +
                        "Validando tiempo de gracia...",
                Toast.LENGTH_LONG
            ).show()

            abrirConfirmacionSalida(vehiculo, tiempoMinutos)
            binding.btnRegistrarPorPlaca.isEnabled = true
            return
        }

        if (vehiculo.bitPaid != 1) {
            abrirPantallaPago(vehiculo, tiempoMinutos)
        } else {
            abrirConfirmacionSalida(vehiculo, tiempoMinutos)
        }

        binding.btnRegistrarPorPlaca.isEnabled = true
    }

    /**
     * ⭐ NUEVO: Abrir pantalla de pago para CAJA (sin ir a confirmación de salida después)
     */
    private fun abrirPantallaPagoCaja(vehiculo: VehiculoDB, tiempoMinutos: Int) {
        val intent = Intent(this, PagoVehiculoActivity::class.java)
        intent.putExtra("VEHICULO_ID", vehiculo.id)
        intent.putExtra("VEHICULO_PLACA", vehiculo.placa)
        intent.putExtra("VEHICULO_FECHA", vehiculo.fechaEntrada.time)
        intent.putExtra("VEHICULO_CODIGO", vehiculo.codigoBarras)
        intent.putExtra("BIT_PAID", vehiculo.bitPaid)
        intent.putExtra("MONTO", vehiculo.monto)
        intent.putExtra("FECHA_PAGO", vehiculo.fechaPago?.time ?: 0L)
        intent.putExtra("TIEMPO_MINUTOS", tiempoMinutos)
        intent.putExtra("ES_USUARIO_CAJA", true) // ⭐ Indicar que es usuario CAJA

        startActivityForResult(intent, REQUEST_CODE_PAGO_CAJA)
    }

    private fun abrirPantallaPago(vehiculo: VehiculoDB, tiempoMinutos: Int) {
        val intent = Intent(this, PagoVehiculoActivity::class.java)
        intent.putExtra("VEHICULO_ID", vehiculo.id)
        intent.putExtra("VEHICULO_PLACA", vehiculo.placa)
        intent.putExtra("VEHICULO_FECHA", vehiculo.fechaEntrada.time)
        intent.putExtra("VEHICULO_CODIGO", vehiculo.codigoBarras)
        intent.putExtra("BIT_PAID", vehiculo.bitPaid)
        intent.putExtra("MONTO", vehiculo.monto)
        intent.putExtra("FECHA_PAGO", vehiculo.fechaPago?.time ?: 0L)
        intent.putExtra("TIEMPO_MINUTOS", tiempoMinutos)
        intent.putExtra("ES_USUARIO_CAJA", false)

        startActivityForResult(intent, REQUEST_CODE_PAGO)
    }

    private fun abrirConfirmacionSalida(vehiculo: VehiculoDB, tiempoMinutos: Int) {
        val intent = Intent(this, SalidaConfirmacionActivity::class.java)
        intent.putExtra("VEHICULO_ID", vehiculo.id)
        intent.putExtra("VEHICULO_PLACA", vehiculo.placa)
        intent.putExtra("VEHICULO_FECHA", vehiculo.fechaEntrada.time)
        intent.putExtra("VEHICULO_CODIGO", vehiculo.codigoBarras)
        intent.putExtra("TIEMPO_MINUTOS", tiempoMinutos)
        intent.putExtra("MONTO", vehiculo.monto)
        intent.putExtra("FECHA_PAGO", vehiculo.fechaPago?.time ?: 0L)
        intent.putExtra("BIT_PAID", vehiculo.bitPaid)
        intent.putExtra("TIEMPO_ESTANCIA", vehiculo.tiempoEstancia)

        startActivityForResult(intent, REQUEST_CODE_CONFIRMACION)
    }

    private fun iniciarEscaner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(
            IntentIntegrator.QR_CODE,
            IntentIntegrator.CODE_128
        )
        integrator.setPrompt("Escanee el código del ticket")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(true)
        integrator.setOrientationLocked(false)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // ⭐ CAJA: Solo limpiar campos, NO ir a confirmación de salida
        if (requestCode == REQUEST_CODE_PAGO_CAJA) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(
                    this,
                    "✓ Pago procesado correctamente\n" +
                            "El operador debe validar la salida.",
                    Toast.LENGTH_LONG
                ).show()
            }
            binding.etPlaca.text?.clear()
            binding.btnRegistrarPorPlaca.isEnabled = true
            binding.btnSalidaSinCobro.isEnabled = false
            return
        }

        if (requestCode == REQUEST_CODE_PAGO || requestCode == REQUEST_CODE_CONFIRMACION) {
            binding.etPlaca.text?.clear()
            binding.btnRegistrarPorPlaca.isEnabled = true
            if (tipoUsuario == "CAJA") {
                binding.btnSalidaSinCobro.isEnabled = false
            }
            return
        }

        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show()
            } else {
                val codigoEscaneado = result.contents

                Toast.makeText(
                    this,
                    "Código escaneado: $codigoEscaneado",
                    Toast.LENGTH_SHORT
                ).show()

                if (codigoEscaneado.startsWith("PK-", ignoreCase = true)) {
                    buscarVehiculoPorCodigo(codigoEscaneado)
                } else {
                    buscarVehiculoPorPlaca(codigoEscaneado)
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val REQUEST_CODE_PAGO = 100
        private const val REQUEST_CODE_CONFIRMACION = 101
        private const val REQUEST_CODE_PAGO_CAJA = 102 // ⭐ Nuevo código para CAJA
    }
}