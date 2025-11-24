package com.cepa.parqueo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.VehiculoDB
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.databinding.ActivityPagoVehiculoBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity para procesar el pago de un vehículo desde la app
 * Reemplaza la funcionalidad de PayStation
 */
class PagoVehiculoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPagoVehiculoBinding
    private lateinit var vehiculoRepository: VehiculoRepository

    private lateinit var vehiculo: VehiculoDB
    private var montoCalculado: Double = 0.0
    private var tiempoCobrableMinutos: Int = 0
    private var estadoCobro: String = ""
    private var strRateKey: String = "A"

    companion object {
        private const val TAG = "PagoVehiculo"
        private const val REQUEST_CODE_CONFIRMACION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPagoVehiculoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)

        cargarDatosIntent()
        setupUI()
        cargarCalculoMonto()
    }

    private fun cargarDatosIntent() {
        val vehiculoId = intent.getIntExtra("VEHICULO_ID", -1)
        val placa = intent.getStringExtra("VEHICULO_PLACA") ?: ""
        val fechaEntrada = intent.getLongExtra("VEHICULO_FECHA", 0)
        val codigoBarras = intent.getStringExtra("VEHICULO_CODIGO") ?: ""
        val bitPaid = intent.getIntExtra("BIT_PAID", 0)
        val monto = intent.getDoubleExtra("MONTO", 0.0)
        val fechaPago = intent.getLongExtra("FECHA_PAGO", 0L)

        vehiculo = VehiculoDB(
            id = vehiculoId,
            placa = placa,
            fechaEntrada = Date(fechaEntrada),
            codigoBarras = codigoBarras,
            estado = "DENTRO",
            bitPaid = bitPaid,
            fechaPago = if (fechaPago > 0) Date(fechaPago) else null,
            monto = monto
        )
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Procesar Pago"

        binding.btnCancelar.setOnClickListener {
            finish()
        }

        binding.btnProcesarPago.setOnClickListener {
            procesarPago()
        }
    }

    private fun cargarCalculoMonto() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnProcesarPago.isEnabled = false

        lifecycleScope.launch {
            when (val result = vehiculoRepository.calcularMonto(vehiculo.placa)) {
                is com.cepa.parqueo.database.CalculoMontoResult.Success -> {
                    val calculo = result.calculo

                    montoCalculado = calculo.montoCalculado
                    tiempoCobrableMinutos = calculo.tiempoCobrableMinutos
                    estadoCobro = calculo.estadoCobro

                    // Obtener strRateKey desde la tarifa
                    strRateKey = obtenerStrRateKey()

                    mostrarInformacion(calculo)

                    binding.progressBar.visibility = View.GONE

                    // Habilitar botón siempre (con texto diferente según estado)
                    binding.btnProcesarPago.isEnabled = true
                }
                is com.cepa.parqueo.database.CalculoMontoResult.Error -> {
                    Toast.makeText(
                        this@PagoVehiculoActivity,
                        "Error al calcular monto: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.progressBar.visibility = View.GONE
                    finish()
                }
            }
        }
    }

    private suspend fun obtenerStrRateKey(): String {
        // Obtener la tarifa actual y su strRateKey
        return when (val result = vehiculoRepository.obtenerTarifa()) {
            is com.cepa.parqueo.database.TarifaResult.Success -> {
                // Por defecto 'A' para tarifa normal
                "A"
            }
            is com.cepa.parqueo.database.TarifaResult.Error -> {
                "A" // Default
            }
        }
    }

    private fun mostrarInformacion(calculo: com.cepa.parqueo.database.CalculoMonto) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Placa
        binding.tvPlaca.text = vehiculo.placa

        // Fecha de Entrada
        binding.tvFechaEntrada.text = dateFormat.format(vehiculo.fechaEntrada)

        // Tiempo Total
        val totalHoras = calculo.tiempoTotalMinutos / 60
        val totalMinutos = calculo.tiempoTotalMinutos % 60
        binding.tvTiempoTotal.text = if (totalHoras > 0) {
            "$totalHoras h $totalMinutos min"
        } else {
            "$totalMinutos minutos"
        }

        // Tiempo Cobrable
        val cobrableHoras = calculo.tiempoCobrableMinutos / 60
        val cobrableMinutos = calculo.tiempoCobrableMinutos % 60
        binding.tvTiempoCobrable.text = if (cobrableHoras > 0) {
            "$cobrableHoras h $cobrableMinutos min"
        } else {
            "$cobrableMinutos minutos"
        }

        // Monto
        binding.tvMonto.text = String.format("$%.2f", calculo.montoCalculado)

        // ⭐ Mostrar desglose de tarifa escalonada
        binding.tvTarifa.text = buildString {
            append("1h: ${String.format("%.2f", calculo.precioPorHora)}")
            append(" | 2h: $2.50")
            append(" | +2h: $3.75/día")
        }

        // Estado y UI según tipo de cobro
        mostrarEstadoCobro(calculo)
    }

    private fun mostrarEstadoCobro(calculo: com.cepa.parqueo.database.CalculoMonto) {
        when (calculo.estadoCobro) {
            "GRACIA_ENTRADA" -> {
                // Primeros 15 minutos - GRATIS
                binding.cardEstado.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_green_light)
                )
                binding.tvEstadoTitulo.text = "✓ DENTRO DE PERÍODO DE GRACIA"
                binding.tvEstadoMensaje.text =
                    "Los primeros 15 minutos son gratuitos.\nNo hay cargo por este vehículo."

                binding.btnProcesarPago.text = "Continuar Sin Cobro"
                binding.btnProcesarPago.isEnabled = true

                // ⭐ Cambiar el monto a 0 para registrar gratis
                montoCalculado = 0.0
            }

            "GRACIA_SALIDA" -> {
                // Ya pagó y está dentro de 15 min de gracia
                binding.cardEstado.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_blue_light)
                )
                binding.tvEstadoTitulo.text = "✓ YA PAGÓ - TIEMPO DE GRACIA"
                binding.tvEstadoMensaje.text =
                    "El vehículo ya realizó el pago.\nTiene 15 minutos para salir sin cargo adicional."

                binding.btnProcesarPago.text = "Continuar Sin Cobro"
                binding.btnProcesarPago.isEnabled = true

                // ⭐ Ya pagó, no cobrar adicional
                montoCalculado = 0.0
            }

            "DEBE_PAGAR" -> {
                // Debe pagar
                binding.cardEstado.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                )

                if (calculo.yaPago) {
                    binding.tvEstadoTitulo.text = "⚠ TIEMPO DE GRACIA EXCEDIDO"
                    binding.tvEstadoMensaje.text =
                        "El vehículo ya pagó anteriormente.\n" +
                                "Ha excedido el tiempo de gracia de 15 minutos.\n" +
                                "Se debe cobrar el tiempo adicional."
                } else {
                    binding.tvEstadoTitulo.text = "💵 COBRO REQUERIDO"
                    binding.tvEstadoMensaje.text =
                        "El vehículo debe pagar antes de salir.\n" +
                                "Después del pago tiene 15 minutos para salir."
                }

                binding.btnProcesarPago.text = "Procesar Pago ($${String.format("%.2f", calculo.montoCalculado)})"
                binding.btnProcesarPago.isEnabled = true
            }

            else -> {
                // GRATIS o cualquier otro caso
                binding.cardEstado.setCardBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
                )
                binding.tvEstadoTitulo.text = "✓ SIN CARGO"
                binding.tvEstadoMensaje.text = "No hay cargo para este vehículo."

                binding.btnProcesarPago.text = "Continuar Sin Cobro"
                binding.btnProcesarPago.isEnabled = true

                // ⭐ Sin cargo
                montoCalculado = 0.0
            }
        }
    }

    private fun procesarPago() {
        // ⭐ Si no hay monto a cobrar (gracia o gratis), registrar pago de $0.00
        if (montoCalculado <= 0) {
            ejecutarRegistroPagoGratis()
            return
        }

        // Si hay monto, mostrar confirmación
        val mensaje = buildString {
            append("¿Confirmar pago de $${String.format("%.2f", montoCalculado)}?\n\n")
            append("Placa: ${vehiculo.placa}\n")
            append("Tiempo cobrable: ${tiempoCobrableMinutos} minutos\n")

            if (vehiculo.bitPaid == 1) {
                append("\n⚠ Nota: Este es un cargo adicional por exceder el tiempo de gracia")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Confirmar Pago")
            .setMessage(mensaje)
            .setPositiveButton("Confirmar Pago") { _, _ ->
                ejecutarRegistroPago()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * ⭐ NUEVO: Registra pago de $0.00 para casos de gracia/gratis
     */
    private fun ejecutarRegistroPagoGratis() {
        binding.btnProcesarPago.isEnabled = false
        binding.btnCancelar.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val sharedPref = getSharedPreferences("DeviceConfig", MODE_PRIVATE)
            val idNumerico = sharedPref.getInt("id_numerico", 3)

            val result = vehiculoRepository.registrarPagoDesdeApp(
                placa = vehiculo.placa,
                monto = 0.00,
                idPayDevice = idNumerico,
                strRateKey = strRateKey
            )

            binding.progressBar.visibility = View.GONE

            when (result) {
                is com.cepa.parqueo.database.PagoResult.Success -> {
                    Toast.makeText(
                        this@PagoVehiculoActivity,
                        "✓ Registrado - Sin cobro\n(Período de gracia)",
                        Toast.LENGTH_SHORT
                    ).show()

                    irAConfirmacionSalida()
                }
                is com.cepa.parqueo.database.PagoResult.Error -> {
                    Toast.makeText(
                        this@PagoVehiculoActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    binding.btnProcesarPago.isEnabled = true
                    binding.btnCancelar.isEnabled = true
                }
            }
        }
    }

    private fun ejecutarRegistroPago() {
        binding.btnProcesarPago.isEnabled = false
        binding.btnCancelar.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Obtener IdPayDevice desde configuración del dispositivo
            val sharedPref = getSharedPreferences("DeviceConfig", MODE_PRIVATE)
            val idNumerico = sharedPref.getInt("id_numerico", 3) // Default 3 para app móvil

            val result = vehiculoRepository.registrarPagoDesdeApp(
                placa = vehiculo.placa,
                monto = montoCalculado,
                idPayDevice = idNumerico,
                strRateKey = strRateKey
            )

            binding.progressBar.visibility = View.GONE

            when (result) {
                is com.cepa.parqueo.database.PagoResult.Success -> {
                    Toast.makeText(
                        this@PagoVehiculoActivity,
                        "✓ Pago registrado correctamente\nMonto: $${String.format("%.2f", result.montoRegistrado)}",
                        Toast.LENGTH_LONG
                    ).show()

                    Log.d(TAG, "Pago registrado - ID: ${result.idVehiculo}, Monto: ${result.montoRegistrado}")

                    // Ir a confirmación de salida
                    irAConfirmacionSalida()
                }
                is com.cepa.parqueo.database.PagoResult.Error -> {
                    Toast.makeText(
                        this@PagoVehiculoActivity,
                        "✗ Error al procesar pago: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    binding.btnProcesarPago.isEnabled = true
                    binding.btnCancelar.isEnabled = true
                }
            }
        }
    }

    private fun irAConfirmacionSalida() {
        val intent = Intent(this, SalidaConfirmacionActivity::class.java)

        // Actualizar datos del vehículo con el pago
        intent.putExtra("VEHICULO_ID", vehiculo.id)
        intent.putExtra("VEHICULO_PLACA", vehiculo.placa)
        intent.putExtra("VEHICULO_FECHA", vehiculo.fechaEntrada.time)
        intent.putExtra("VEHICULO_CODIGO", vehiculo.codigoBarras)
        intent.putExtra("BIT_PAID", 1) // Ahora sí está pagado
        intent.putExtra("MONTO", montoCalculado)
        intent.putExtra("FECHA_PAGO", System.currentTimeMillis()) // Pago recién hecho
        intent.putExtra("TIEMPO_MINUTOS", tiempoCobrableMinutos)

        startActivityForResult(intent, REQUEST_CODE_CONFIRMACION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_CONFIRMACION) {
            // Cerrar esta activity y volver a la lista
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

