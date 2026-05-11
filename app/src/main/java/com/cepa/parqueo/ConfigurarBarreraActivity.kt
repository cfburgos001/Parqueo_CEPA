package com.cepa.parqueo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cepa.parqueo.database.AperturaManualRepository
import com.cepa.parqueo.database.AperturaManualRepository.BarreraItem
import com.cepa.parqueo.database.AperturaManualRepository.BarrerasResult
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.databinding.ActivityConfigurarBarreraBinding
import kotlinx.coroutines.launch

class ConfigurarBarreraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigurarBarreraBinding
    private lateinit var repository: AperturaManualRepository
    private lateinit var dispositivoManager: DispositivoManager
    private lateinit var adapter: BarreraAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigurarBarreraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AperturaManualRepository(this)
        dispositivoManager = DispositivoManager(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurar Barrera"

        setupRecyclerView()
        loadCurrentBarrera()
        loadBarreras()
    }

    private fun setupRecyclerView() {
        adapter = BarreraAdapter(dispositivoManager.obtenerIdBarrera()) { barreraId ->
            seleccionarBarrera(barreraId)
        }
        binding.rvBarreras.layoutManager = LinearLayoutManager(this)
        binding.rvBarreras.adapter = adapter
    }

    private fun loadCurrentBarrera() {
        val idBarreraActual = dispositivoManager.obtenerIdBarrera()
        binding.tvBarreraActual.text = "Barrera asignada: ID $idBarreraActual"
    }

    private fun loadBarreras() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvSinDatos.visibility = View.GONE
        binding.rvBarreras.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = repository.listarBarreras()) {
                is BarrerasResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (result.barreras.isEmpty()) {
                        binding.tvSinDatos.visibility = View.VISIBLE
                    } else {
                        adapter.actualizarLista(result.barreras)
                        binding.rvBarreras.visibility = View.VISIBLE
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

    private fun seleccionarBarrera(idBarrera: Int) {
        dispositivoManager.configurarIdBarrera(idBarrera)
        binding.tvBarreraActual.text = "Barrera asignada: ID $idBarrera"

        lifecycleScope.launch {
            try {
                dispositivoManager.persistirBarreraEnBD(idBarrera)
                Toast.makeText(
                    this@ConfigurarBarreraActivity,
                    "✓ Barrera $idBarrera asignada correctamente",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ConfigurarBarreraActivity,
                    "✓ Barrera guardada localmente (sin sincronizar con BD)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    inner class BarreraAdapter(
        private var idSeleccionado: Int,
        private val onSeleccion: (Int) -> Unit
    ) : RecyclerView.Adapter<BarreraAdapter.BarreraViewHolder>() {

        private val barreras = mutableListOf<BarreraItem>()

        fun actualizarLista(nuevaLista: List<BarreraItem>) {
            barreras.clear()
            barreras.addAll(nuevaLista)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarreraViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_barrera, parent, false)
            return BarreraViewHolder(view)
        }

        override fun onBindViewHolder(holder: BarreraViewHolder, position: Int) {
            holder.bind(barreras[position])
        }

        override fun getItemCount() = barreras.size

        inner class BarreraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val rbSeleccionar: RadioButton = itemView.findViewById(R.id.rbSeleccionarBarrera)
            private val tvId: TextView = itemView.findViewById(R.id.tvIdBarrera)
            private val tvNombre: TextView = itemView.findViewById(R.id.tvNombreBarrera)
            private val tvEstado: TextView = itemView.findViewById(R.id.tvEstadoBarrera)

            fun bind(barrera: BarreraItem) {
                tvId.text = "ID: ${barrera.id}"
                tvNombre.text = barrera.barreraSeteo
                rbSeleccionar.isChecked = barrera.id == idSeleccionado

                if (barrera.estadoBarrera) {
                    tvEstado.text = "● Activa"
                    tvEstado.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                } else {
                    tvEstado.text = "● Inactiva"
                    tvEstado.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                }

                val clickHandler = View.OnClickListener {
                    val prevSelected = idSeleccionado
                    idSeleccionado = barrera.id
                    val prevIndex = barreras.indexOfFirst { it.id == prevSelected }
                    if (prevIndex >= 0) notifyItemChanged(prevIndex)
                    notifyItemChanged(adapterPosition)
                    onSeleccion(barrera.id)
                }

                rbSeleccionar.setOnClickListener(clickHandler)
                itemView.setOnClickListener(clickHandler)
            }
        }
    }
}
