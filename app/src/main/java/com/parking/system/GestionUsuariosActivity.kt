package com.parking.system

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parking.system.database.*
import com.parking.system.databinding.ActivityGestionUsuariosBinding
import kotlinx.coroutines.launch

class GestionUsuariosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestionUsuariosBinding
    private lateinit var operadorRepository: OperadorRepository
    private lateinit var adapter: OperadoresAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestionUsuariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        operadorRepository = OperadorRepository(this)

        setupUI()
        cargarOperadores()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Gestión de Usuarios"

        // RecyclerView
        adapter = OperadoresAdapter()
        binding.rvOperadores.layoutManager = LinearLayoutManager(this)
        binding.rvOperadores.adapter = adapter

        // Botón nuevo operador
        binding.fabNuevoOperador.setOnClickListener {
            mostrarDialogoNuevoOperador()
        }
    }

    private fun cargarOperadores() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = operadorRepository.listarOperadores()) {
                is ListaOperadoresResult.Success -> {
                    adapter.setOperadores(result.operadores)
                    binding.progressBar.visibility = View.GONE

                    if (result.operadores.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                    }
                }
                is ListaOperadoresResult.Error -> {
                    Toast.makeText(
                        this@GestionUsuariosActivity,
                        "Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun mostrarDialogoNuevoOperador() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_nuevo_operador, null)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Nuevo Operador")
            .setView(dialogView)
            .setPositiveButton("Crear", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val btnCrear = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnCrear.setOnClickListener {
                crearOperador(dialogView, dialog)
            }
        }

        dialog.show()
    }

    private fun crearOperador(dialogView: View, dialog: AlertDialog) {
        val etUsername = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)
        val etNombre = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombre)
        val etApellido = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApellido)
        val spinnerTipo = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerTipo)

        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val nombre = etNombre.text.toString().trim()
        val apellido = etApellido.text.toString().trim()
        val tipoUsuario = spinnerTipo.selectedItem.toString()

        if (username.isEmpty() || password.isEmpty() || nombre.isEmpty() || apellido.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = operadorRepository.crearOperador(
                username, password, nombre, apellido, tipoUsuario
            )

            when (result) {
                is DatabaseResult.Success -> {
                    Toast.makeText(
                        this@GestionUsuariosActivity,
                        result.message,
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    cargarOperadores()
                }
                is DatabaseResult.Error -> {
                    Toast.makeText(
                        this@GestionUsuariosActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// Adapter para RecyclerView
class OperadoresAdapter : RecyclerView.Adapter<OperadoresAdapter.OperadorViewHolder>() {

    private var operadores = listOf<Operador>()

    fun setOperadores(nuevosOperadores: List<Operador>) {
        operadores = nuevosOperadores
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): OperadorViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_operador, parent, false)
        return OperadorViewHolder(view)
    }

    override fun onBindViewHolder(holder: OperadorViewHolder, position: Int) {
        holder.bind(operadores[position])
    }

    override fun getItemCount() = operadores.size

    class OperadorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNombre = itemView.findViewById<android.widget.TextView>(R.id.tvNombre)
        private val tvUsername = itemView.findViewById<android.widget.TextView>(R.id.tvUsername)
        private val tvTipo = itemView.findViewById<android.widget.TextView>(R.id.tvTipo)
        private val tvId = itemView.findViewById<android.widget.TextView>(R.id.tvId)

        fun bind(operador: Operador) {
            tvNombre.text = operador.getNombreCompleto()
            tvUsername.text = "Usuario: ${operador.username}"
            tvTipo.text = operador.tipoUsuario
            tvId.text = "ID: ${operador.id}"
        }
    }
}