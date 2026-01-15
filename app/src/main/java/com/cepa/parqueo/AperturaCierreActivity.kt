package com.cepa.parqueo

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.AperturaCierreRepository
import com.cepa.parqueo.database.AperturaResult
import com.cepa.parqueo.database.CierreResult
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.database.EstadoAperturaResult
import com.cepa.parqueo.databinding.ActivityAperturaCierreBinding
import com.cepa.parqueo.printer.CierreTicketPrinter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity para Apertura y Cierre de Caja
 */
class AperturaCierreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAperturaCierreBinding
    private lateinit var repository: AperturaCierreRepository
    private lateinit var dispositivoManager: DispositivoManager

    private var idOperador: Int = 0
    private var nombreOperador: String = ""
    private var idDispositivo: String = ""

    private var hayAperturaActiva: Boolean = false
    private var fechaAperturaActual: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAperturaCierreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AperturaCierreRepository(this)
        dispositivoManager = DispositivoManager(this)

        cargarDatosSesion()
        setupUI()
        verificarEstadoApertura()
    }

    private fun cargarDatosSesion() {
        val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
        idOperador = sharedPref.getInt("id_operador", 0)
        nombreOperador = sharedPref.getString("nombre_completo", "Desconocido") ?: "Desconocido"

        lifecycleScope.launch {
            idDispositivo = dispositivoManager.obtenerIdDispositivo()
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Apertura y Cierre de Caja"

        val dateFormat = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "SV"))
        binding.tvFechaActual.text = dateFormat.format(Date())

        binding.tvOperador.text = nombreOperador

        binding.btnApertura.setOnClickListener {
            confirmarApertura()
        }

        binding.btnCierre.setOnClickListener {
            confirmarCierre()
        }

        // ⭐ NUEVO: Botón Reporte X
        binding.btnReporteX.setOnClickListener {
            confirmarReporteX()
        }
    }

    private fun verificarEstadoApertura() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = repository.obtenerEstadoApertura(idDispositivo)) {
                is EstadoAperturaResult.Success -> {
                    hayAperturaActiva = result.hayApertura
                    fechaAperturaActual = result.fechaApertura

                    actualizarUI()
                    binding.progressBar.visibility = View.GONE
                }
                is EstadoAperturaResult.Error -> {
                    Toast.makeText(
                        this@AperturaCierreActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun actualizarUI() {
        if (hayAperturaActiva) {
            // Ya hay apertura - Mostrar botón de cierre
            binding.cardApertura.visibility = View.GONE
            binding.cardCierre.visibility = View.VISIBLE

            val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvFechaApertura.text = fechaAperturaActual?.let { dateTimeFormat.format(it) } ?: "N/A"
        } else {
            // No hay apertura - Mostrar botón de apertura
            binding.cardApertura.visibility = View.VISIBLE
            binding.cardCierre.visibility = View.GONE
        }
    }

    private fun confirmarApertura() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val horaActual = timeFormat.format(Date())

        AlertDialog.Builder(this)
            .setTitle("Confirmar Apertura de Caja")
            .setMessage(
                "¿Desea realizar la apertura de caja?\n\n" +
                        "Operador: $nombreOperador\n" +
                        "Hora: $horaActual\n" +
                        "Dispositivo: $idDispositivo"
            )
            .setPositiveButton("Confirmar") { _, _ ->
                ejecutarApertura()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarApertura() {
        binding.btnApertura.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = repository.registrarApertura(idOperador, nombreOperador, idDispositivo)) {
                is AperturaResult.Success -> {
                    Toast.makeText(
                        this@AperturaCierreActivity,
                        "✓ ${result.mensaje}",
                        Toast.LENGTH_LONG
                    ).show()

                    verificarEstadoApertura()
                }
                is AperturaResult.Error -> {
                    Toast.makeText(
                        this@AperturaCierreActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    binding.btnApertura.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun confirmarCierre() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val horaActual = timeFormat.format(Date())

        AlertDialog.Builder(this)
            .setTitle("Confirmar Cierre de Caja")
            .setMessage(
                "⚠️ ¿Desea realizar el cierre de caja?\n\n" +
                        "Esta acción generará un reporte completo y cerrará el turno.\n\n" +
                        "Operador: $nombreOperador\n" +
                        "Hora: $horaActual"
            )
            .setPositiveButton("Confirmar Cierre") { _, _ ->
                ejecutarCierre()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarCierre() {
        binding.btnCierre.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = repository.registrarCierre(idOperador, nombreOperador, idDispositivo)) {
                is CierreResult.Success -> {
                    Toast.makeText(
                        this@AperturaCierreActivity,
                        "✓ ${result.mensaje}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Imprimir ticket de cierre
                    imprimirTicketCierre(result)

                    // Actualizar UI
                    verificarEstadoApertura()
                }
                is CierreResult.Error -> {
                    Toast.makeText(
                        this@AperturaCierreActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    binding.btnCierre.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun imprimirTicketCierre(cierre: CierreResult.Success) {
        try {
            val ticketData = CierreTicketData(
                operador = nombreOperador,
                fechaApertura = cierre.fechaApertura,
                fechaCierre = cierre.fechaOperacion,
                montoTotal = cierre.montoTotal,
                cantidadVehiculos = cierre.cantidadVehiculos,
                vehiculosDentro = cierre.vehiculosDentro,
                vehiculosDentroDetalle = cierre.vehiculosDentroDetalle
            )

            CierreTicketPrinter.printCierreTicket(this, ticketData)

            Toast.makeText(this, "✓ Ticket de cierre impreso", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "⚠ Error al imprimir: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

/**
 * Data class para el ticket de cierre
 */
data class CierreTicketData(
    val operador: String,
    val fechaApertura: Date,
    val fechaCierre: Date,
    val montoTotal: Double,
    val cantidadVehiculos: Int,
    val vehiculosDentro: Int,
    val vehiculosDentroDetalle: List<com.cepa.parqueo.database.VehiculoDentro>
)