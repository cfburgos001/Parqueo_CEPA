package com.parking.system

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.zxing.integration.android.IntentIntegrator
import com.parking.system.database.DispositivoManager
import com.parking.system.database.VehiculoDB
import com.parking.system.database.VehiculoRepository
import com.parking.system.database.VehiculoResult
import com.parking.system.databinding.ActivitySalidaVehiculoBinding
import kotlinx.coroutines.launch
import java.util.Date

class SalidaVehiculoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalidaVehiculoBinding
    private lateinit var vehiculoRepository: VehiculoRepository
    private lateinit var dispositivoManager: DispositivoManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalidaVehiculoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vehiculoRepository = VehiculoRepository(this)
        dispositivoManager = DispositivoManager(this)

        verificarTipoDispositivo()
        setupUI()
    }

    private fun verificarTipoDispositivo() {
        if (!dispositivoManager.puedeRegistrarSalida()) {
            Toast.makeText(
                this,
                "Este dispositivo esta configurado como ENTRADA. No puede registrar salidas",
                Toast.LENGTH_LONG
            ).show()

            binding.btnRegistrarPorPlaca.isEnabled = false
            binding.btnEscanearQR.isEnabled = false
            binding.etPlaca.isEnabled = false
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Registro de Salida"

        binding.btnRegistrarPorPlaca.setOnClickListener {
            procesarBusqueda()
        }

        binding.btnEscanearQR.setOnClickListener {
            iniciarEscaner()
        }

        binding.etPlaca.addTextChangedListener { text ->
            val upperCase = text.toString().uppercase()
            if (text.toString() != upperCase) {
                binding.etPlaca.setText(upperCase)
                binding.etPlaca.setSelection(upperCase.length)
            }
        }

        binding.etPlaca.addTextChangedListener {
            binding.tilPlaca.error = null
        }
    }

    private fun procesarBusqueda() {
        val placa = binding.etPlaca.text.toString().trim()

        if (placa.isEmpty()) {
            binding.tilPlaca.error = "Ingrese la placa del vehiculo"
            return
        }

        if (placa.length < 4) {
            binding.tilPlaca.error = "Placa invalida"
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
                        "Vehiculo no encontrado o ya salio del parqueo",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnRegistrarPorPlaca.isEnabled = true
                }
                is VehiculoResult.Error -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "Error: ${result.message}",
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
                        "Ticket no encontrado o vehiculo ya salio",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is VehiculoResult.Error -> {
                    Toast.makeText(
                        this@SalidaVehiculoActivity,
                        "Error: ${result.message}",
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
        android.util.Log.d("SalidaVehiculo", "========================")

        // ⭐ SOLO VERIFICAR bitPaid - No calcular nada
        if (vehiculo.bitPaid != 1) {
            // NO HA PAGADO - Mostrar alerta
            mostrarAlertaNoPagado(vehiculo)
            binding.btnRegistrarPorPlaca.isEnabled = true
            return
        }

        // YA PAGÓ (bitPaid == 1) - Proceder a confirmación
        abrirConfirmacionSalida(vehiculo)
    }

    private fun mostrarAlertaNoPagado(vehiculo: VehiculoDB) {
        val mensaje = buildString {
            append("⚠️ PAGO PENDIENTE\n\n")
            append("Este vehículo NO ha pagado el ticket.\n\n")
            append("📋 Información:\n")
            append("Placa: ${vehiculo.placa}\n")
            append("Monto en BD: $${String.format("%.2f", vehiculo.monto)}\n")
            append("Estado bitPaid: ${vehiculo.bitPaid}\n\n")
            append("❌ El cliente debe ir a PayStation para pagar antes de salir.")
        }

        AlertDialog.Builder(this)
            .setTitle("Pago Pendiente")
            .setMessage(mensaje)
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
                binding.etPlaca.text?.clear()
            }
            .setCancelable(false)
            .show()
    }

    private fun abrirConfirmacionSalida(vehiculo: VehiculoDB) {
        val ahora = Date()
        val tiempoMinutos = ((ahora.time - vehiculo.fechaEntrada.time) / 60000).toInt()

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
        binding.btnRegistrarPorPlaca.isEnabled = true
    }

    private fun iniciarEscaner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(
            IntentIntegrator.QR_CODE,
            IntentIntegrator.CODE_128
        )
        integrator.setPrompt("Escanee el codigo del ticket")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(true)
        integrator.setOrientationLocked(false)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CONFIRMACION) {
            binding.etPlaca.text?.clear()
            binding.btnRegistrarPorPlaca.isEnabled = true
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
                    "Codigo escaneado: $codigoEscaneado",
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
        private const val REQUEST_CODE_CONFIRMACION = 100
    }
}