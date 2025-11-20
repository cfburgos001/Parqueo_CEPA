package com.cepa.parqueo

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.database.VehiculoDB
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.databinding.ActivitySalidaConfirmacionBinding
import com.cepa.parqueo.hardware.PlumaController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity para confirmar y ejecutar la salida del vehículo
 * SIMPLIFICADO: El pago ya se procesó antes, aquí solo validamos tiempo de gracia
 */
class SalidaConfirmacionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalidaConfirmacionBinding
    private lateinit var vehiculoRepository: VehiculoRepository

    private lateinit var vehiculo: VehiculoDB
    private var tiempoMinutos: Int = 0
    private var monto: Double = 0.0
    private var fechaPago: Date? = null
    private var bitPaid: Int = 0

    private lateinit var dispositivoManager: DispositivoManager
    private var idDispositivo: String = ""
    private var tiempoGraciaConfigurado: Int = 15
    private var estaFueraDeGracia: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalidaConfirmacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)
        dispositivoManager = DispositivoManager(this)

        lifecycleScope.launch {
            idDispositivo = dispositivoManager.obtenerIdDispositivo()
            tiempoGraciaConfigurado = ParkingConfigHelper.obtenerTiempoGracia(
                this@SalidaConfirmacionActivity,
                idDispositivo
            )
            Log.d("SalidaConfirmacion", "Tiempo de gracia: $tiempoGraciaConfigurado min")
        }

        cargarDatosIntent()
        setupUI()
        mostrarInformacion()
    }

    private fun cargarDatosIntent() {
        val vehiculoId = intent.getIntExtra("VEHICULO_ID", -1)
        val placa = intent.getStringExtra("VEHICULO_PLACA") ?: ""
        val fechaEntrada = intent.getLongExtra("VEHICULO_FECHA", 0)
        val codigoBarras = intent.getStringExtra("VEHICULO_CODIGO") ?: ""
        tiempoMinutos = intent.getIntExtra("TIEMPO_MINUTOS", 0)
        monto = intent.getDoubleExtra("MONTO", 0.0)
        val fechaPagoLong = intent.getLongExtra("FECHA_PAGO", 0L)
        bitPaid = intent.getIntExtra("BIT_PAID", 0)

        if (fechaPagoLong > 0) {
            fechaPago = Date(fechaPagoLong)
        }

        vehiculo = VehiculoDB(
            id = vehiculoId,
            placa = placa,
            fechaEntrada = Date(fechaEntrada),
            codigoBarras = codigoBarras,
            estado = "DENTRO",
            bitPaid = bitPaid,
            fechaPago = fechaPago,
            monto = monto
        )
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Confirmar Salida"

        binding.btnCancelar.setOnClickListener {
            finish()
        }

        binding.btnConfirmar.setOnClickListener {
            confirmarSalida()
        }
    }

    private fun mostrarInformacion() {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Placa
        binding.tvPlaca.text = vehiculo.placa

        // Fecha de entrada
        binding.tvFechaEntrada.text = dateFormat.format(vehiculo.fechaEntrada)

        // Hora de pago
        if (fechaPago != null) {
            binding.tvHoraPago.text = dateFormat.format(fechaPago)
        } else {
            binding.tvHoraPago.text = "Sin pago registrado"
        }

        // Hora de salida (ahora)
        val horaSalida = Date()
        binding.tvHoraSalida.text = dateFormat.format(horaSalida)

        // Tiempo de estancia
        val horas = tiempoMinutos / 60
        val minutos = tiempoMinutos % 60
        val tiempoTexto = if (horas > 0) {
            "$horas hora${if (horas > 1) "s" else ""} $minutos min"
        } else {
            "$minutos minutos"
        }
        binding.tvTiempoEstancia.text = tiempoTexto

        // ⭐ LÓGICA SIMPLIFICADA: Solo evaluar tiempo de gracia
        if (bitPaid == 1 && fechaPago != null) {
            // Vehículo pagó - Evaluar tiempo de gracia
            val tiempoGraciaMinutos = ((horaSalida.time - fechaPago!!.time) / 60000).toInt()

            val graciaHoras = tiempoGraciaMinutos / 60
            val graciaMinutos = tiempoGraciaMinutos % 60
            val graciaTexto = if (graciaHoras > 0) {
                "$graciaHoras hora${if (graciaHoras > 1) "s" else ""} $graciaMinutos min"
            } else {
                "$graciaMinutos minutos"
            }
            binding.tvTiempoGracia.text = graciaTexto

            val limiteGracia = tiempoGraciaConfigurado

            if (tiempoGraciaMinutos <= limiteGracia) {
                // ✓ DENTRO DE GRACIA
                estaFueraDeGracia = false
                mostrarEstadoDentroGracia()
            } else {
                // ⚠ FUERA DE GRACIA
                estaFueraDeGracia = true
                mostrarEstadoFueraGracia()
            }
        } else {
            // Sin pago (no debería llegar aquí, pero por seguridad)
            binding.tvTiempoGracia.text = "N/A"
            mostrarEstadoError()
        }

        // Monto
        binding.tvMonto.text = String.format("$%.2f", monto)
    }

    /**
     * ✓ DENTRO DE GRACIA: Verde, puede salir
     */
    private fun mostrarEstadoDentroGracia() {
        binding.cardMonto.setCardBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        )
        binding.tvTituloMonto.text = "PAGO CONFIRMADO"

        binding.tvMensajeEstado.visibility = View.VISIBLE
        binding.tvMensajeEstado.text = "✓ Vehículo dentro del tiempo de gracia\nPuede proceder a validar la salida"
        binding.tvMensajeEstado.setTextColor(
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        )
        binding.tvMensajeEstado.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.transparent)
        )

        binding.btnConfirmar.isEnabled = true
        binding.btnConfirmar.alpha = 1f
        binding.btnConfirmar.text = "VALIDAR Y ABRIR PLUMA"
    }

    /**
     * ⚠ FUERA DE GRACIA: Naranja, debe volver a pagar
     */
    private fun mostrarEstadoFueraGracia() {
        binding.cardMonto.setCardBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        )
        binding.tvTituloMonto.text = "TIEMPO DE GRACIA EXCEDIDO"

        binding.tvMensajeEstado.visibility = View.VISIBLE
        binding.tvMensajeEstado.text =
            "⚠ El vehículo excedió el tiempo de gracia\n" +
                    "Se registrará salida y nuevo ingreso\n" +
                    "Debe pagar periodo adicional en siguiente validación"
        binding.tvMensajeEstado.setTextColor(
            ContextCompat.getColor(this, android.R.color.holo_orange_light)
        )
        binding.tvMensajeEstado.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        )
        binding.tvMensajeEstado.setPadding(16, 16, 16, 16)

        binding.btnConfirmar.isEnabled = true
        binding.btnConfirmar.alpha = 1f
        binding.btnConfirmar.text = "REGISTRAR EXCESO (NO ABRE PLUMA)"
    }

    /**
     * ❌ ERROR: No debería estar aquí sin pago
     */
    private fun mostrarEstadoError() {
        binding.cardMonto.setCardBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        binding.tvTituloMonto.text = "ERROR"

        binding.tvMensajeEstado.visibility = View.VISIBLE
        binding.tvMensajeEstado.text = "❌ El vehículo debe realizar el pago primero"
        binding.tvMensajeEstado.setTextColor(
            ContextCompat.getColor(this, android.R.color.white)
        )
        binding.tvMensajeEstado.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        binding.tvMensajeEstado.setPadding(16, 16, 16, 16)

        binding.btnConfirmar.isEnabled = false
        binding.btnConfirmar.alpha = 0.5f
    }

    private fun confirmarSalida() {
        binding.btnConfirmar.isEnabled = false
        binding.btnCancelar.isEnabled = false

        lifecycleScope.launch {
            if (estaFueraDeGracia) {
                // ⚠️ FUERA DE GRACIA: Registrar salida Y crear nuevo ingreso
                val result = vehiculoRepository.registrarSalidaYReingresoPorGracia(
                    vehiculo.placa,
                    idDispositivo
                )

                when (result) {
                    is com.cepa.parqueo.database.ReingresoPorGraciaResult.Success -> {
                        Toast.makeText(
                            this@SalidaConfirmacionActivity,
                            "⚠ ${result.mensaje}\n\n" +
                                    "Nuevo código: ${result.nuevoCodigoBarras}\n" +
                                    "Debe pagar periodo adicional",
                            Toast.LENGTH_LONG
                        ).show()

                        Log.d("SalidaConfirmacion", "Registro anterior: ${result.idRegistroAnterior}")
                        Log.d("SalidaConfirmacion", "Nuevo registro: ${result.idNuevoRegistro}")

                        // NO levantar pluma (bitExit = 0)
                        Toast.makeText(
                            this@SalidaConfirmacionActivity,
                            "🚫 PLUMA NO SE LEVANTA\nDebe realizar nuevo pago",
                            Toast.LENGTH_LONG
                        ).show()

                        delay(3000)
                        setResult(RESULT_OK)
                        finish()
                    }
                    is com.cepa.parqueo.database.ReingresoPorGraciaResult.Error -> {
                        Toast.makeText(
                            this@SalidaConfirmacionActivity,
                            "✗ Error: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnConfirmar.isEnabled = true
                        binding.btnCancelar.isEnabled = true
                    }
                }
            } else {
                // ✅ DENTRO DE GRACIA: Salida normal
                val result = vehiculoRepository.registrarSalida(vehiculo.placa, idDispositivo)

                when (result) {
                    is com.cepa.parqueo.database.SalidaResult.Success -> {
                        Toast.makeText(
                            this@SalidaConfirmacionActivity,
                            "✓ Salida registrada\nDispositivo: ${result.idDispositivoSalida}",
                            Toast.LENGTH_LONG
                        ).show()

                        Log.d("SalidaConfirmacion", "IdDispositivoSalida: ${result.idDispositivoSalida}")

                        levantarPlumaYSalir()
                    }
                    is com.cepa.parqueo.database.SalidaResult.Error -> {
                        Toast.makeText(
                            this@SalidaConfirmacionActivity,
                            "✗ Error: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnConfirmar.isEnabled = true
                        binding.btnCancelar.isEnabled = true
                    }
                }
            }
        }
    }

    private fun levantarPlumaYSalir() {
        lifecycleScope.launch {
            Toast.makeText(
                this@SalidaConfirmacionActivity,
                "🚧 LEVANTANDO LA PLUMA...",
                Toast.LENGTH_LONG
            ).show()

            PlumaController.levantarPluma(duracionSegundos = 5)

            delay(3000)
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}