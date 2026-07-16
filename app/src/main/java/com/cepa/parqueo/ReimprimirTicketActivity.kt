package com.cepa.parqueo

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.DatabaseResult
import com.cepa.parqueo.database.VehiculoDB
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.database.VehiculoResult
import com.cepa.parqueo.databinding.ActivityReimprimirTicketBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Búsqueda por placa (sin importar el estado del vehículo) para reimprimir
 * el ticket de entrada original. Pensado para cuando el QR físico del
 * ticket está dañado o ilegible pero la placa se conoce.
 *
 * Reutiliza el mismo formato de impresión que la entrada normal
 * (PrinterManager.printReceipt + ReceiptData), así que el ticket
 * reimpreso sale idéntico al original.
 */
class ReimprimirTicketActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReimprimirTicketBinding
    private lateinit var vehiculoRepository: VehiculoRepository

    private var vehiculoEncontrado: VehiculoDB? = null

    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale("es", "SV"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReimprimirTicketBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnBuscar.setOnClickListener { buscarPorPlaca() }
        binding.btnReimprimir.setOnClickListener { reimprimir() }
    }

    private fun buscarPorPlaca() {
        val placa = binding.etPlaca.text.toString().trim()

        if (placa.isEmpty()) {
            binding.tilPlaca.error = "Ingrese la placa del vehículo"
            return
        }
        binding.tilPlaca.error = null

        binding.progressBar.visibility = View.VISIBLE
        binding.btnBuscar.isEnabled = false
        binding.cardResultado.visibility = View.GONE
        binding.btnReimprimir.visibility = View.GONE
        vehiculoEncontrado = null

        lifecycleScope.launch {
            when (val result = vehiculoRepository.buscarVehiculoParaReimpresion(placa)) {
                is VehiculoResult.Found -> {
                    vehiculoEncontrado = result.vehiculo
                    mostrarResultado(result.vehiculo)
                }
                is VehiculoResult.NotFound -> {
                    Toast.makeText(
                        this@ReimprimirTicketActivity,
                        "No se encontró ningún registro con la placa \"$placa\"",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is VehiculoResult.Error -> {
                    Toast.makeText(
                        this@ReimprimirTicketActivity,
                        "⚠ ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            binding.progressBar.visibility = View.GONE
            binding.btnBuscar.isEnabled = true
        }
    }

    private fun mostrarResultado(vehiculo: VehiculoDB) {
        binding.tvPlacaResultado.text = vehiculo.placa
        binding.tvTipoResultado.text = "Tipo: ${tipoVehiculoTexto(vehiculo.strRateKey)}"
        binding.tvCodigoResultado.text = "Código: ${vehiculo.codigoBarras}"
        binding.tvFechaResultado.text = "Entrada: ${formatoFecha.format(vehiculo.fechaEntrada)}"
        binding.tvEstadoResultado.text = when (vehiculo.estado) {
            "DENTRO" -> "● Dentro del parqueo"
            "SALIO" -> "● Ya salió del parqueo"
            else -> "● ${vehiculo.estado}"
        }

        binding.cardResultado.visibility = View.VISIBLE
        binding.btnReimprimir.visibility = View.VISIBLE
    }

    private fun reimprimir() {
        val vehiculo = vehiculoEncontrado ?: return

        binding.btnReimprimir.isEnabled = false

        lifecycleScope.launch {
            // Se usa el mismo contador de copias que la reimpresión normal,
            // solo para llevar registro de cuántas veces se ha reimpreso.
            val result = vehiculoRepository.incrementarBitCopy(vehiculo.codigoBarras)

            val receiptData = ReceiptData(
                uniqueId = vehiculo.codigoBarras,
                plate = vehiculo.placa,
                entryTime = vehiculo.fechaEntrada,
                vehicleType = tipoVehiculoTexto(vehiculo.strRateKey)
            )

            try {
                com.cepa.parqueo.printer.PrinterManager.printReceipt(this@ReimprimirTicketActivity, receiptData)

                val mensaje = when (result) {
                    is DatabaseResult.Success -> "✓ Ticket reimpreso\n${result.message}"
                    is DatabaseResult.Error -> "✓ Ticket reimpreso\n(No se actualizó contador: ${result.message})"
                }
                Toast.makeText(this@ReimprimirTicketActivity, mensaje, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ReimprimirTicketActivity,
                    "Error al imprimir: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

            binding.btnReimprimir.isEnabled = true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}