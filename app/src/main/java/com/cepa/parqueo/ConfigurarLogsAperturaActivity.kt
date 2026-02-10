package com.cepa.parqueo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cepa.parqueo.database.AperturaManualRepository
import com.cepa.parqueo.database.DatabaseResult
import com.cepa.parqueo.database.TipoLogMantenimiento
import com.cepa.parqueo.database.TiposLogMantenimientoResult
import kotlinx.coroutines.launch

/**
 * Activity para configurar qué tipos de logs se muestran
 * en los botones de Apertura Manual (Entrada y Salida).
 *
 * La config se guarda en IOT_ConfigAperturaManual (tabla separada).
 * NO toca IOT_TiposLogs.
 * Es GLOBAL: todos los dispositivos leen la misma config.
 * Es DINÁMICA: se puede usar para otros contextos a futuro.
 */
class ConfigurarLogsAperturaActivity : AppCompatActivity() {

    private lateinit var repository: AperturaManualRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvSinDatos: TextView
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configurar_logs_apertura)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurar Logs de Apertura"

        repository = AperturaManualRepository(this)

        recyclerView = findViewById(R.id.rvTiposLog)
        tvSinDatos = findViewById(R.id.tvSinDatos)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)

        cargarTiposLog()
    }

    private fun cargarTiposLog() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvSinDatos.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = repository.listarTodosLosTiposLog()) {
                is TiposLogMantenimientoResult.Success -> {
                    progressBar.visibility = View.GONE

                    if (result.tiposLog.isEmpty()) {
                        tvSinDatos.visibility = View.VISIBLE
                        tvSinDatos.text = "No se encontraron tipos de log activos"
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        recyclerView.adapter = TipoLogAdapter(
                            result.tiposLog.toMutableList()
                        )
                    }
                }
                is TiposLogMantenimientoResult.Error -> {
                    progressBar.visibility = View.GONE
                    tvSinDatos.visibility = View.VISIBLE
                    tvSinDatos.text = "Error al cargar: ${result.message}"
                }
            }
        }
    }

    /**
     * Cuando se marca un checkbox: agregar a IOT_ConfigAperturaManual
     * Cuando se desmarca: quitar de IOT_ConfigAperturaManual
     */
    private fun toggleLogContexto(idTipoLog: Int, contexto: String, agregar: Boolean) {
        lifecycleScope.launch {
            val result = if (agregar) {
                repository.agregarLogAContexto(idTipoLog, contexto)
            } else {
                repository.quitarLogDeContexto(idTipoLog, contexto)
            }

            when (result) {
                is DatabaseResult.Success -> {
                    val accion = if (agregar) "agregado a" else "removido de"
                    Toast.makeText(
                        this@ConfigurarLogsAperturaActivity,
                        "✓ Log $accion $contexto",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is DatabaseResult.Error -> {
                    Toast.makeText(
                        this@ConfigurarLogsAperturaActivity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    // Recargar para revertir el checkbox
                    cargarTiposLog()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // =============================================
    // ADAPTER
    // =============================================
    inner class TipoLogAdapter(
        private val tiposLog: MutableList<TipoLogMantenimiento>
    ) : RecyclerView.Adapter<TipoLogAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvId: TextView = itemView.findViewById(R.id.tvIdLog)
            val tvNombre: TextView = itemView.findViewById(R.id.tvNombreLog)
            val tvDescripcion: TextView = itemView.findViewById(R.id.tvDescripcionLog)
            val cbEntrada: CheckBox = itemView.findViewById(R.id.cbMostrarEntrada)
            val cbSalida: CheckBox = itemView.findViewById(R.id.cbMostrarSalida)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tipo_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tipoLog = tiposLog[position]

            holder.tvId.text = "ID: ${tipoLog.id}"
            holder.tvNombre.text = tipoLog.nombre
            holder.tvDescripcion.text = tipoLog.descripcion

            // Quitar listeners para evitar disparo al setear valores
            holder.cbEntrada.setOnCheckedChangeListener(null)
            holder.cbSalida.setOnCheckedChangeListener(null)

            holder.cbEntrada.isChecked = tipoLog.asignadoEntrada
            holder.cbSalida.isChecked = tipoLog.asignadoSalida

            // Listener: cuando cambia el checkbox de ENTRADA
            holder.cbEntrada.setOnCheckedChangeListener { _, isChecked ->
                toggleLogContexto(tipoLog.id, "ENTRADA", isChecked)
                // Actualizar el modelo local
                tiposLog[position] = tipoLog.copy(asignadoEntrada = isChecked)
            }

            // Listener: cuando cambia el checkbox de SALIDA
            holder.cbSalida.setOnCheckedChangeListener { _, isChecked ->
                toggleLogContexto(tipoLog.id, "SALIDA", isChecked)
                tiposLog[position] = tipoLog.copy(asignadoSalida = isChecked)
            }
        }

        override fun getItemCount(): Int = tiposLog.size
    }
}