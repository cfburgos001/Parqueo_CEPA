package com.parking.system

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.parking.system.database.DispositivoManager
import com.parking.system.database.VehiculoDB
import com.parking.system.database.VehiculoRepository
import com.parking.system.databinding.ActivitySalidaConfirmacionBinding
import com.parking.system.hardware.PlumaController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var estaFueraDeGracia: Boolean = false // ⭐ Nueva variable para saber el estado

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalidaConfirmacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)
        dispositivoManager = DispositivoManager(this)

        lifecycleScope.launch {
            idDispositivo = dispositivoManager.obtenerIdDispositivo()
            // Cargar tiempo de gracia desde BD
            tiempoGraciaConfigurado = ParkingConfigHelper.obtenerTiempoGracia(this@SalidaConfirmacionActivity, idDispositivo)
            Log.d("SalidaConfirmacion", "Tiempo de gracia configurado: $tiempoGraciaConfigurado minutos")
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

    /**
     * Obtiene el tiempo de gracia configurado (cargado en onCreate desde BD)
     */
    private fun obtenerTiempoGraciaConfigurado(): Int {
        return tiempoGraciaConfigurado
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
            binding.tvHoraPago.text = "No registrada"
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

        // ⭐ LÓGICA PRINCIPAL: Determinar el estado
        if (bitPaid == 0) {
            // ❌ NO HA PAGADO
            mostrarEstadoNoPagado()
        } else if (fechaPago != null) {
            // ✓ HA PAGADO - Verificar tiempo de gracia
            val tiempoGraciaMinutos = ((horaSalida.time - fechaPago!!.time) / 60000).toInt()

            // Obtener límite configurado
            val limiteGracia = obtenerTiempoGraciaConfigurado()

            // Mostrar tiempo de gracia
            val graciaHoras = tiempoGraciaMinutos / 60
            val graciaMinutos = tiempoGraciaMinutos % 60
            val graciaTexto = if (graciaHoras > 0) {
                "$graciaHoras hora${if (graciaHoras > 1) "s" else ""} $graciaMinutos min"
            } else {
                "$graciaMinutos minutos"
            }
            binding.tvTiempoGracia.text = graciaTexto

            if (tiempoGraciaMinutos <= limiteGracia) {
                // ✓ DENTRO DEL TIEMPO DE GRACIA (≤ límite configurado)
                estaFueraDeGracia = false
                mostrarEstadoDentroGracia()
            } else {
                // ⚠ FUERA DEL TIEMPO DE GRACIA (> límite configurado)
                estaFueraDeGracia = true
                mostrarEstadoFueraGracia()
            }
        } else {
            // ✓ HA PAGADO pero no hay fecha de pago registrada
            binding.tvTiempoGracia.text = "N/A"
            mostrarEstadoDentroGracia()
        }

        // Monto
        binding.tvMonto.text = String.format("$%.2f", monto)
    }

    /**
     * ✓ ESTADO: Dentro del tiempo de gracia (≤ 15 minutos)
     * - Card verde
     * - Mensaje azul: "✓ Proceda a validar la salida"
     * - Botón VALIDAR habilitado
     */
    private fun mostrarEstadoDentroGracia() {
        // Card verde
        binding.cardMonto.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        binding.tvTituloMonto.text = "MONTO PAGADO"

        // Mensaje azul
        binding.tvMensajeEstado.visibility = View.VISIBLE
        binding.tvMensajeEstado.text = "✓ Proceda a validar la salida"
        binding.tvMensajeEstado.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        binding.tvMensajeEstado.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))

        // Botón VALIDAR habilitado
        binding.btnConfirmar.isEnabled = true
        binding.btnConfirmar.alpha = 1f
    }

    /**
     * ⚠ ESTADO: Fuera del tiempo de gracia (> 15 minutos)
     * - Card naranja
     * - Mensaje naranja: "⚠ Ticket fuera de tiempo de gracia"
     * - Botón VALIDAR habilitado PERO registra salida y reingreso
     */
    private fun mostrarEstadoFueraGracia() {
        // Card naranja
        binding.cardMonto.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        binding.tvTituloMonto.text = "EXCEDIÓ TIEMPO DE GRACIA"

        // Mensaje naranja sobre fondo naranja oscuro
        binding.tvMensajeEstado.visibility = View.VISIBLE
        binding.tvMensajeEstado.text = "⚠ Ticket fuera de tiempo de gracia\nPasar a estación de pago"
        binding.tvMensajeEstado.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        binding.tvMensajeEstado.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        binding.tvMensajeEstado.setPadding(16, 16, 16, 16)

        // ⭐ Botón VALIDAR habilitado (pero tendrá comportamiento especial)
        binding.btnConfirmar.isEnabled = true
        binding.btnConfirmar.alpha = 1f
        binding.btnConfirmar.text = "REGISTRAR EXCESO"
    }

    /**
     * ❌ ESTADO: No ha pagado (bitPaid = 0)
     * - Card rojo
     * - Mensaje blanco sobre rojo: "⚠️ NO HA PAGADO"
     * - Botón VALIDAR deshabilitado
     */
    private fun mostrarEstadoNoPagado() {
        // Card rojo
        binding.cardMonto.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        binding.tvTituloMonto.text = "⚠️ NO HA PAGADO"

        // Mensaje blanco sobre rojo
        binding.tvMensajeEstado.visibility = View.VISIBLE
        binding.tvMensajeEstado.text = "❌ Debe pagar en estación de pago"
        binding.tvMensajeEstado.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        binding.tvMensajeEstado.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        binding.tvMensajeEstado.setPadding(16, 16, 16, 16)

        // Botón VALIDAR deshabilitado
        binding.btnConfirmar.isEnabled = false
        binding.btnConfirmar.alpha = 0.5f

        // Tiempo de gracia N/A
        binding.tvTiempoGracia.text = "N/A"
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
                    is com.parking.system.database.ReingresoPorGraciaResult.Success -> {
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
                            "🚫 PLUMA NO SE LEVANTA\nDebe pagar en PayStation",
                            Toast.LENGTH_LONG
                        ).show()

                        delay(3000)
                        setResult(RESULT_OK)
                        finish()
                    }
                    is com.parking.system.database.ReingresoPorGraciaResult.Error -> {
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
                    is com.parking.system.database.SalidaResult.Success -> {
                        Toast.makeText(
                            this@SalidaConfirmacionActivity,
                            "✓ Salida registrada\nDispositivo: ${result.idDispositivoSalida}",
                            Toast.LENGTH_LONG
                        ).show()

                        Log.d("SalidaConfirmacion", "IdDispositivoSalida: ${result.idDispositivoSalida}")

                        levantarPlumaYSalir()
                    }
                    is com.parking.system.database.SalidaResult.Error -> {
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