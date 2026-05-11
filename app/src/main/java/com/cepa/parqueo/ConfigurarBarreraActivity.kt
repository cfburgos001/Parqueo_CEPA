package com.cepa.parqueo

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cepa.parqueo.database.AperturaManualRepository
import com.cepa.parqueo.database.BarreraItem
import com.cepa.parqueo.database.BarrerasResult
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.databinding.ActivityConfigurarBarreraBinding
import kotlinx.coroutines.launch

class ConfigurarBarreraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigurarBarreraBinding
    private lateinit var repository: AperturaManualRepository
    private lateinit var dispositivoManager: DispositivoManager

    private var barreras = listOf<BarreraItem>()
    private var idBarreraEntradaSeleccionada = 1
    private var idBarreraSalidaSeleccionada = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigurarBarreraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AperturaManualRepository(this)
        dispositivoManager = DispositivoManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurar Barrera"

        idBarreraEntradaSeleccionada = dispositivoManager.obtenerIdBarreraEntrada()
        idBarreraSalidaSeleccionada = dispositivoManager.obtenerIdBarreraSalida()

        loadBarreras()

        binding.btnGuardarBarreras.setOnClickListener {
            guardarBarreras()
        }
    }

    private fun loadBarreras() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
        binding.tvSinDatos.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = repository.listarBarreras()) {
                is BarrerasResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.barreras.isEmpty()) {
                        binding.tvSinDatos.visibility = View.VISIBLE
                    } else {
                        barreras = result.barreras
                        setupSpinners()
                        binding.contentLayout.visibility = View.VISIBLE
                    }
                }
                is BarrerasResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvSinDatos.visibility = View.VISIBLE
                    Toast.makeText(
                        this@ConfigurarBarreraActivity,
                        "Error al cargar barreras: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupSpinners() {
        val nombres = barreras.map { "[ID ${it.id}]  ${it.barreraSeteo}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nombres)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerBarreraEntrada.adapter = adapter
        val posEntrada = barreras.indexOfFirst { it.id == idBarreraEntradaSeleccionada }.coerceAtLeast(0)
        binding.spinnerBarreraEntrada.setSelection(posEntrada)
        binding.spinnerBarreraEntrada.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                idBarreraEntradaSeleccionada = barreras[pos].id
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.spinnerBarreraSalida.adapter = adapter
        val posSalida = barreras.indexOfFirst { it.id == idBarreraSalidaSeleccionada }.coerceAtLeast(0)
        binding.spinnerBarreraSalida.setSelection(posSalida)
        binding.spinnerBarreraSalida.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                idBarreraSalidaSeleccionada = barreras[pos].id
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun guardarBarreras() {
        dispositivoManager.configurarBarreraEntrada(idBarreraEntradaSeleccionada)
        dispositivoManager.configurarBarreraSalida(idBarreraSalidaSeleccionada)

        lifecycleScope.launch {
            try {
                dispositivoManager.persistirBarrerasEnBD(
                    idBarreraEntradaSeleccionada,
                    idBarreraSalidaSeleccionada
                )
            } catch (e: Exception) {
                // guardado local ya aplicado arriba
            }
            Toast.makeText(
                this@ConfigurarBarreraActivity,
                "✓ Guardado\nEntrada → Barrera ID $idBarreraEntradaSeleccionada\nSalida  → Barrera ID $idBarreraSalidaSeleccionada",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
