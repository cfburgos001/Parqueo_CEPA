package com.cepa.parqueo

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.VehiculoDB
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.database.VehiculoResult
import com.cepa.parqueo.databinding.ActivityReimprimirFacturaBinding
import com.cepa.parqueo.printer.PagoTicketPrinter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Búsqueda por placa para reimprimir la factura (comprobante de pago) de
 * una visita que YA fue pagada. Pensado para cuando el cliente dañó su
 * factura física y pide que se la vuelvan a imprimir.
 *
 * Reutiliza el mismo formato que la impresión al momento de pagar
 * (PagoTicketPrinter + PagoTicketData).
 *
 * IMPORTANTE — dos datos no se guardan en la BD al momento del pago original
 * y por lo tanto se reconstruyen de forma aproximada en la reimpresión:
 *  - "Operador": la BD no registra quién procesó el pago (solo qué
 *    dispositivo), así que se imprime el operador de la sesión actual,
 *    con la aclaración "(reimpresión)".
 *  - "Tiempo cobrable": no se guarda por separado del tiempo total: se
 *    reutiliza el mismo valor. El monto cobrado SÍ es el real (columna
 *    Monto), esto solo afecta el detalle informativo del tiempo.
 */
class ReimprimirFacturaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReimprimirFacturaBinding
    private lateinit var vehiculoRepository: VehiculoRepository

    private var vehiculoEncontrado: VehiculoDB? = null
    private var nombreOperador: String = ""
    private var idDispositivo: String = ""

    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale("es", "SV"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReimprimirFacturaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)
        cargarDatosSesion()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnBuscar.setOnClickListener { buscarPorPlaca() }
        binding.btnReimprimir.setOnClickListener { reimprimir() }
    }

    private fun cargarDatosSesion() {
        val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
        nombreOperador = sharedPref.getString("nombre_completo", "Operador") ?: "Operador"

        val devicePref = getSharedPreferences("DeviceConfig", MODE_PRIVATE)
        idDispositivo = devicePref.getString("device_id", "POS-001") ?: "POS-001"
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
                        this@ReimprimirFacturaActivity,
                        "No se encontró ningún registro con la placa \"$placa\"",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is VehiculoResult.Error -> {
                    Toast.makeText(
                        this@ReimprimirFacturaActivity,
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
        binding.tvMontoResultado.text = "$${String.format("%.2f", vehiculo.monto)}"
        binding.tvFechaPagoResultado.text = if (vehiculo.fechaPago != null) {
            "Pagado: ${formatoFecha.format(vehiculo.fechaPago)}"
        } else {
            "Sin fecha de pago"
        }

        if (vehiculo.estaPagado()) {
            binding.tvMetodoResultado.text = "Método: ${metodoPagoTexto(vehiculo.operationType)}"
            binding.tvEstadoResultado.text = "✓ Pagado — se puede reimprimir"
            binding.tvEstadoResultado.setTextColor(0xFF2E7D32.toInt())
            binding.btnReimprimir.visibility = View.VISIBLE
        } else {
            binding.tvMetodoResultado.text = "Este vehículo aún no tiene un pago registrado."
            binding.tvEstadoResultado.text = "✗ No pagado — no se puede reimprimir factura"
            binding.tvEstadoResultado.setTextColor(0xFFD32F2F.toInt())
            binding.btnReimprimir.visibility = View.GONE
        }

        binding.cardResultado.visibility = View.VISIBLE
    }

    private fun metodoPagoTexto(operationType: Int): String = when (operationType) {
        2 -> "Tarjeta"
        3 -> "Cortesía"
        else -> "Efectivo"
    }

    private fun reimprimir() {
        val vehiculo = vehiculoEncontrado ?: return
        if (!vehiculo.estaPagado() || vehiculo.fechaPago == null) return

        binding.btnReimprimir.isEnabled = false

        val tiempoTotalMinutos = ((vehiculo.fechaPago.time - vehiculo.fechaEntrada.time) / 60000L)
            .toInt()
            .coerceAtLeast(0)

        val ticketData = PagoTicketData(
            placa = vehiculo.placa,
            fechaEntrada = vehiculo.fechaEntrada,
            fechaPago = vehiculo.fechaPago,
            tiempoTotalMinutos = tiempoTotalMinutos,
            // No se guarda por separado en BD; se reutiliza el total (ver nota de la clase).
            tiempoCobrableMinutos = tiempoTotalMinutos,
            monto = vehiculo.monto,
            metodoPago = metodoPagoTexto(vehiculo.operationType),
            operador = "$nombreOperador (reimpresión)",
            idDispositivo = idDispositivo,
            estadoCobro = "REIMPRESION"
        )

        try {
            PagoTicketPrinter.printPagoTicket(this, ticketData)
            Toast.makeText(this, "✓ Factura reimpresa", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al imprimir: ${e.message}", Toast.LENGTH_LONG).show()
        }

        binding.btnReimprimir.isEnabled = true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}