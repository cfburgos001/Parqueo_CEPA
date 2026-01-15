package com.cepa.parqueo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.database.VehiculoDB
import com.cepa.parqueo.database.VehiculoRepository
import com.cepa.parqueo.database.VehiculoResult
import com.cepa.parqueo.databinding.ActivitySalidaVehiculoBinding
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Activity para registrar salida de vehículos
 * VERSIÓN 2: Con validación de apertura de caja
 */
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

        // ⭐ VALIDAR APERTURA AL INICIAR
        validarAperturaActiva()
    }

    /**
     * ⭐ NUEVO: Validar que haya apertura antes de permitir operaciones
     */
    private fun validarAperturaActiva() {
        binding.btnRegistrarPorPlaca.isEnabled = false
        binding.btnEscanearQR.isEnabled = false
        binding.etPlaca.isEnabled = false

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
            }
        }
    }

    /**
     * ⭐ NUEVO: Mostrar diálogo cuando no hay apertura
     */
    private fun mostrarDialogoSinApertura() {
        AlertDialog.Builder(this)
            .setTitle("Apertura Requerida")
            .setMessage(AperturaValidator.MENSAJE_SIN_APERTURA)
            .setCancelable(false)
            .setPositiveButton("Ir a Apertura/Cierre") { _, _ ->
                // Cerrar esta activity y volver al home
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
        android.util.Log.d("SalidaVehiculo", "========================")

        val ahora = Date()
        val tiempoMinutos = ((ahora.time - vehiculo.fechaEntrada.time) / 60000).toInt()

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
        if (requestCode == REQUEST_CODE_PAGO || requestCode == REQUEST_CODE_CONFIRMACION) {
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
    }
}