package com.cepa.parqueo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.ListaTarifasResult
import com.cepa.parqueo.database.TicketExtraviadoResult
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.databinding.ActivityTicketPerdidoBinding
import com.cepa.parqueo.printer.PrinterManager
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Genera e imprime un Ticket Extraviado (normal o pesado) cuando un cliente
 * perdió su ticket original. No pide placa — el SP correspondiente genera
 * su propio código, que queda como Placa Y CodigoBarras a la vez (igual
 * que las entradas normales desde el fix de RegistrarEntradaApp), así que
 * se puede escanear en Salida sin más cambios.
 *
 * El precio mostrado se trae en vivo de listarTarifas() (rate keys 'E' y
 * 'EP') en vez de estar fijo en el botón — si cambias el precio en
 * IOT_Tarifas, esta pantalla lo refleja solo.
 */
class TicketPerdidoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTicketPerdidoBinding
    private lateinit var vehiculoRepository: VehiculoRepository

    private var idDispositivo: String = "POS-001"
    private var idOperador: Int = 0

    private var precioNormal: Double? = null
    private var precioPesado: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTicketPerdidoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)
        cargarDatosSesion()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnTicketNormal.setOnClickListener { confirmarYGenerar(esPesado = false) }
        binding.btnTicketPesado.setOnClickListener { confirmarYGenerar(esPesado = true) }

        cargarPrecios()
    }

    private fun cargarDatosSesion() {
        val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
        idOperador = sharedPref.getInt("id_operador", 0)

        val devicePref = getSharedPreferences("DeviceConfig", MODE_PRIVATE)
        idDispositivo = devicePref.getString("device_id", "POS-001") ?: "POS-001"
    }

    private fun cargarPrecios() {
        lifecycleScope.launch {
            when (val result = vehiculoRepository.listarTarifas()) {
                is ListaTarifasResult.Success -> {
                    precioNormal = result.tarifas.firstOrNull { it.strRateKey == "E" }?.precio1Hora
                    precioPesado = result.tarifas.firstOrNull { it.strRateKey == "EP" }?.precio1Hora

                    binding.tvPrecioNormal.text = precioNormal?.let { "$${String.format("%.2f", it)}" } ?: "—"
                    binding.tvPrecioPesado.text = precioPesado?.let { "$${String.format("%.2f", it)}" } ?: "—"
                }
                is ListaTarifasResult.Error -> {
                    // No bloquea la pantalla — el SP igual aplica el precio correcto
                    // aunque aquí no se pudo mostrar de antemano.
                    binding.tvPrecioNormal.text = "—"
                    binding.tvPrecioPesado.text = "—"
                }
            }
        }
    }

    private fun confirmarYGenerar(esPesado: Boolean) {
        val precio = if (esPesado) precioPesado else precioNormal
        val tipoTexto = if (esPesado) "Ticket Extraviado Pesado" else "Ticket Extraviado"
        val precioTexto = precio?.let { "$${String.format("%.2f", it)}" } ?: "el monto configurado"

        AlertDialog.Builder(this)
            .setTitle(tipoTexto)
            .setMessage("¿Confirma generar un $tipoTexto por $precioTexto? Se cobrará al momento de pagar en caja.")
            .setPositiveButton("Confirmar") { _, _ -> generar(esPesado) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun generar(esPesado: Boolean) {
        binding.btnTicketNormal.isEnabled = false
        binding.btnTicketPesado.isEnabled = false

        lifecycleScope.launch {
            val result = if (esPesado) {
                vehiculoRepository.crearTicketExtraviadoPesado(idDispositivo, idOperador)
            } else {
                vehiculoRepository.crearTicketExtraviado(idDispositivo, idOperador)
            }

            when (result) {
                is TicketExtraviadoResult.Success -> {
                    Toast.makeText(
                        this@TicketPerdidoActivity,
                        "✓ Ticket generado: ${result.codigo}",
                        Toast.LENGTH_SHORT
                    ).show()

                    val data = TicketExtraviadoData(
                        codigo = result.codigo,
                        tipo = if (esPesado) "TICKET EXTRAVIADO PESADO" else "TICKET EXTRAVIADO",
                        monto = result.monto,
                        fecha = Date()
                    )

                    try {
                        PrinterManager.printTicketExtraviado(this@TicketPerdidoActivity, data)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@TicketPerdidoActivity,
                            "Se generó el ticket pero falló la impresión: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                is TicketExtraviadoResult.Error -> {
                    Toast.makeText(this@TicketPerdidoActivity, "⚠ ${result.message}", Toast.LENGTH_LONG).show()
                }
            }

            binding.btnTicketNormal.isEnabled = true
            binding.btnTicketPesado.isEnabled = true
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}