package com.cepa.parqueo

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.cepa.parqueo.database.AperturaManualRepository
import com.cepa.parqueo.database.AperturaManualResult
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.database.TipoLogApertura
import com.cepa.parqueo.database.TiposLogAperturaResult
import kotlinx.coroutines.launch

/**
 * Diálogo reutilizable para Apertura Manual de Barrera.
 * Se usa en IngresoVehiculoActivity (ENTRADA) y SalidaVehiculoActivity (SALIDA).
 *
 * Flujo:
 * 1. Consulta IOT_ConfigAperturaManual para el contexto
 * 2. Muestra lista de motivos al operador
 * 3. Al seleccionar: escribe 1 en ComandoBarrera + EstadoBarrera de la barrera correcta y registra log
 */
object AperturaManualDialog {

    /**
     * Muestra el diálogo de apertura manual.
     * @param contexto "ENTRADA" o "SALIDA" — determina qué barrera se abre.
     */
    fun mostrar(
        activity: Activity,
        lifecycleScope: LifecycleCoroutineScope,
        contexto: String,
        idOperador: Int,
        nombreOperador: String
    ) {
        val repository = AperturaManualRepository(activity)
        val dispositivoManager = DispositivoManager(activity)

        val idBarrera = if (contexto == "ENTRADA")
            dispositivoManager.obtenerIdBarreraEntrada()
        else
            dispositivoManager.obtenerIdBarreraSalida()

        val progressDialog = AlertDialog.Builder(activity)
            .setTitle("Cargando...")
            .setMessage("Obteniendo motivos de apertura...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            val idDispositivo = dispositivoManager.obtenerIdDispositivo()

            when (val result = repository.obtenerLogsParaApertura(contexto)) {
                is TiposLogAperturaResult.Success -> {
                    progressDialog.dismiss()

                    if (result.tiposLog.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "⚠ No hay motivos configurados para ${contexto.lowercase()}\n" +
                                    "Configúrelos en Mantenimiento → Logs de Apertura",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    mostrarListaMotivos(
                        activity, lifecycleScope, result.tiposLog,
                        contexto, idOperador, nombreOperador, idDispositivo, idBarrera
                    )
                }

                is TiposLogAperturaResult.Error -> {
                    progressDialog.dismiss()
                    Toast.makeText(activity, "✗ Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun mostrarListaMotivos(
        activity: Activity,
        lifecycleScope: LifecycleCoroutineScope,
        tiposLog: List<TipoLogApertura>,
        contexto: String,
        idOperador: Int,
        nombreOperador: String,
        idDispositivo: String,
        idBarrera: Int
    ) {
        val descripciones = tiposLog.map { it.descripcion }.toTypedArray()
        val tituloContexto = if (contexto == "ENTRADA") "Entrada" else "Salida"

        AlertDialog.Builder(activity)
            .setTitle("🔓 Apertura Manual - $tituloContexto\nSeleccione el motivo:")
            .setItems(descripciones) { _, which ->
                confirmarApertura(
                    activity, lifecycleScope, tiposLog[which],
                    contexto, idOperador, nombreOperador, idDispositivo, idBarrera
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarApertura(
        activity: Activity,
        lifecycleScope: LifecycleCoroutineScope,
        tipoLog: TipoLogApertura,
        contexto: String,
        idOperador: Int,
        nombreOperador: String,
        idDispositivo: String,
        idBarrera: Int
    ) {
        val tituloContexto = if (contexto == "ENTRADA") "Entrada" else "Salida"
        AlertDialog.Builder(activity)
            .setTitle("Confirmar Apertura")
            .setMessage(
                "¿Abrir barrera de $tituloContexto (ID $idBarrera) por:\n\n" +
                        "📋 ${tipoLog.descripcion}\n\n" +
                        "👤 Operador: $nombreOperador"
            )
            .setPositiveButton("ABRIR BARRERA") { _, _ ->
                ejecutarApertura(
                    activity, lifecycleScope, tipoLog,
                    contexto, idOperador, nombreOperador, idDispositivo, idBarrera
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarApertura(
        activity: Activity,
        lifecycleScope: LifecycleCoroutineScope,
        tipoLog: TipoLogApertura,
        contexto: String,
        idOperador: Int,
        nombreOperador: String,
        idDispositivo: String,
        idBarrera: Int
    ) {
        val repository = AperturaManualRepository(activity)

        lifecycleScope.launch {
            Toast.makeText(activity, "🚧 Abriendo barrera ID $idBarrera...", Toast.LENGTH_SHORT).show()

            when (val result = repository.ejecutarAperturaManual(
                idTipoLog = tipoLog.id,
                idOperador = idOperador,
                nombreOperador = nombreOperador,
                idDispositivo = idDispositivo,
                contexto = contexto,
                idBarrera = idBarrera
            )) {
                is AperturaManualResult.Success -> {
                    Toast.makeText(
                        activity,
                        "✓ Barrera ID $idBarrera abierta\n${tipoLog.descripcion}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is AperturaManualResult.Error -> {
                    Toast.makeText(
                        activity,
                        "✗ Error: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
