package com.cepa.parqueo

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.DatabaseResult
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.database.ListaTarifasResult
import com.cepa.parqueo.database.RegistroEntradaResult
import com.cepa.parqueo.database.TarifaDetalle
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.databinding.ActivityIngresoVehiculoBinding
import com.cepa.parqueo.hardware.PlumaController
import kotlinx.coroutines.launch
import java.util.Date

/**
 * VERSIÓN 2: Con selector de tipo de vehículo (Auto/Moto/Camión)
 */
class IngresoVehiculoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIngresoVehiculoBinding
    private lateinit var vehiculoRepository: VehiculoRepository
    private lateinit var dispositivoManager: DispositivoManager

    private var idOperador: Int = 0
    private var nombreOperador: String = ""
    private var idDispositivo: String = ""

    private var ultimoTicketImpreso: ReceiptData? = null

    // ⭐ NUEVO: Lista de tarifas disponibles
    private var tarifasDisponibles: List<TarifaDetalle> = emptyList()
    private var strRateKeySeleccionado: String = "A"  // Default: Auto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIngresoVehiculoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)
        dispositivoManager = DispositivoManager(this)

        cargarDatosSesion()
        verificarTipoDispositivo()
        cargarTarifas()  // ⭐ NUEVO
        setupUI()
    }

    private fun verificarTipoDispositivo() {
        if (!dispositivoManager.puedeRegistrarEntrada()) {
            Toast.makeText(
                this,
                "⚠ Este dispositivo está configurado como SALIDA\nNo puede registrar entradas",
                Toast.LENGTH_LONG
            ).show()

            binding.btnRegistrarEntrada.isEnabled = false
            binding.etPlaca.isEnabled = false
            binding.spinnerTipoVehiculo.isEnabled = false
        }
    }

    private fun cargarDatosSesion() {
        val sharedPref = getSharedPreferences("ParkingSession", MODE_PRIVATE)
        idOperador = sharedPref.getInt("id_operador", 0)
        nombreOperador = sharedPref.getString("nombre_completo", "Desconocido") ?: "Desconocido"

        lifecycleScope.launch {
            idDispositivo = dispositivoManager.obtenerIdDispositivo()
        }
    }

    /**
     * ⭐ NUEVO: Carga las tarifas disponibles desde la BD
     */
    private fun cargarTarifas() {
        lifecycleScope.launch {
            when (val result = vehiculoRepository.listarTarifas()) {
                is ListaTarifasResult.Success -> {
                    tarifasDisponibles = result.tarifas

                    // Crear lista de opciones para el Spinner
                    val opcionesTarifas = tarifasDisponibles.map { tarifa ->
                        "${tarifa.tipoTarifa} - $${String.format("%.2f", tarifa.precioPorHora)}/h"
                    }

                    // Configurar Spinner
                    val adapter = ArrayAdapter(
                        this@IngresoVehiculoActivity,
                        android.R.layout.simple_spinner_item,
                        opcionesTarifas
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerTipoVehiculo.adapter = adapter

                    // Listener para cambio de selección
                    binding.spinnerTipoVehiculo.setSelection(0)  // Auto por defecto
                    strRateKeySeleccionado = if (tarifasDisponibles.isNotEmpty()) {
                        tarifasDisponibles[0].strRateKey
                    } else {
                        "A"
                    }

                    // Actualizar texto de tarifa seleccionada
                    actualizarInfoTarifa(0)
                }
                is ListaTarifasResult.Error -> {
                    Toast.makeText(
                        this@IngresoVehiculoActivity,
                        "⚠ Error al cargar tarifas: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Configurar tarifas por defecto
                    configurarTarifasPorDefecto()
                }
            }
        }
    }

    /**
     * Configura tarifas por defecto si no se pueden cargar de BD
     */
    private fun configurarTarifasPorDefecto() {
        val opcionesDefault = listOf(
            "Vehículo Normal - $1.25/h",
            "Motocicleta - $1.00/h",
            "Camión - $2.00/h"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            opcionesDefault
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTipoVehiculo.adapter = adapter
        binding.spinnerTipoVehiculo.setSelection(0)
        strRateKeySeleccionado = "A"
    }

    /**
     * ⭐ NUEVO: Actualiza la información de la tarifa seleccionada
     */
    private fun actualizarInfoTarifa(position: Int) {
        if (position < tarifasDisponibles.size) {
            val tarifa = tarifasDisponibles[position]
            strRateKeySeleccionado = tarifa.strRateKey

            val infoTexto = buildString {
                append("Tarifa: ${tarifa.tipoTarifa}\n")
                append("Precio: $${String.format("%.2f", tarifa.precioPorHora)}/hora\n")
                if (tarifa.cobroIndefinido) {
                    append("Modo: Sin Máximo (cobro indefinido)")
                } else {
                    append("Modo: Escalonado (hasta $${String.format("%.2f", tarifa.precioMax)}/día)")
                }
            }

            binding.tvInfoTarifa.text = infoTexto
            binding.tvInfoTarifa.visibility = View.VISIBLE
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Registro de Entrada"

        binding.etPlaca.addTextChangedListener {
            binding.tilPlaca.error = null
        }

        binding.etPlaca.addTextChangedListener { text ->
            val upperCase = text.toString().uppercase()
            if (text.toString() != upperCase) {
                binding.etPlaca.setText(upperCase)
                binding.etPlaca.setSelection(upperCase.length)
            }
        }

        // ⭐ NUEVO: Listener para cambio de tipo de vehículo
        binding.spinnerTipoVehiculo.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                actualizarInfoTarifa(position)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // No hacer nada
            }
        }

        binding.btnRegistrarEntrada.setOnClickListener {
            registrarEntrada()
        }

        binding.btnReimprimir.setOnClickListener {
            reimprimirUltimoTicket()
        }

        binding.btnReimprimir.isEnabled = false
    }

    private fun registrarEntrada() {
        val placa = binding.etPlaca.text.toString().trim()

        if (placa.isEmpty()) {
            binding.tilPlaca.error = "Ingrese la placa del vehículo"
            return
        }

        if (placa.length < 4) {
            binding.tilPlaca.error = "Placa inválida"
            return
        }

        binding.btnRegistrarEntrada.isEnabled = false

        lifecycleScope.launch {
            val result = vehiculoRepository.registrarEntrada(
                placa = placa,
                usuario = nombreOperador,
                idOperador = idOperador,
                idDispositivo = idDispositivo,
                strRateKey = strRateKeySeleccionado  // ⭐ NUEVO: Enviar tipo de vehículo
            )

            when (result) {
                is RegistroEntradaResult.Success -> {
                    val tipoVehiculoTexto = when (strRateKeySeleccionado) {
                        "M" -> "Moto"
                        "C" -> "Camión"
                        else -> "Vehículo"
                    }

                    Toast.makeText(
                        this@IngresoVehiculoActivity,
                        "✓ Entrada registrada exitosamente\n" +
                                "Tipo: $tipoVehiculoTexto\n" +
                                "Código: ${result.codigoBarras}",
                        Toast.LENGTH_SHORT
                    ).show()

                    val receiptData = ReceiptData(
                        uniqueId = result.codigoBarras,
                        plate = placa,
                        entryTime = Date(),
                        vehicleType = tipoVehiculoTexto  // ⭐ NUEVO
                    )

                    ultimoTicketImpreso = receiptData
                    printReceipt(receiptData)
                    levantarPluma()
                    binding.etPlaca.text?.clear()
                    binding.spinnerTipoVehiculo.setSelection(0)  // Reset a Auto
                    binding.btnReimprimir.isEnabled = true
                }
                is RegistroEntradaResult.Error -> {
                    Toast.makeText(
                        this@IngresoVehiculoActivity,
                        "⚠ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            binding.btnRegistrarEntrada.isEnabled = true
        }
    }

    private fun reimprimirUltimoTicket() {
        if (ultimoTicketImpreso == null) {
            Toast.makeText(
                this,
                "No hay ticket para reimprimir",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.btnReimprimir.isEnabled = false

        lifecycleScope.launch {
            val result = vehiculoRepository.incrementarBitCopy(ultimoTicketImpreso!!.uniqueId)

            when (result) {
                is DatabaseResult.Success -> {
                    printReceipt(ultimoTicketImpreso!!)

                    Toast.makeText(
                        this@IngresoVehiculoActivity,
                        "✓ Ticket reimpreso\n${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is DatabaseResult.Error -> {
                    printReceipt(ultimoTicketImpreso!!)

                    Toast.makeText(
                        this@IngresoVehiculoActivity,
                        "⚠ Ticket reimpreso\n(No se actualizó contador: ${result.message})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            binding.btnReimprimir.isEnabled = true
        }
    }

    private fun printReceipt(data: ReceiptData) {
        try {
            com.cepa.parqueo.printer.PrinterManager.printReceipt(this, data)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al imprimir: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun levantarPluma() {
        lifecycleScope.launch {
            Toast.makeText(
                this@IngresoVehiculoActivity,
                "🚧 LEVANTANDO LA PLUMA...",
                Toast.LENGTH_LONG
            ).show()

            val exito = PlumaController.levantarPluma(duracionSegundos = 5)

            if (exito) {
                Toast.makeText(
                    this@IngresoVehiculoActivity,
                    "✓ Pluma levantada - Puede pasar",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@IngresoVehiculoActivity,
                    "⚠ Error al controlar la pluma",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}