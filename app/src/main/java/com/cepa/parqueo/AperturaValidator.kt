package com.cepa.parqueo

import android.content.Context
import com.cepa.parqueo.database.AperturaCierreRepository
import com.cepa.parqueo.database.DispositivoManager
import com.cepa.parqueo.database.EstadoAperturaResult

/**
 * Helper para validar si hay apertura activa antes de permitir operaciones
 */
object AperturaValidator {

    /**
     * Verifica si hay una apertura activa
     * @return true si hay apertura, false si no hay
     */
    suspend fun hayAperturaActiva(context: Context): Boolean {
        val repository = AperturaCierreRepository(context)
        val dispositivoManager = DispositivoManager(context)
        val idDispositivo = dispositivoManager.obtenerIdDispositivo()

        return when (val result = repository.obtenerEstadoApertura(idDispositivo)) {
            is EstadoAperturaResult.Success -> result.hayApertura
            is EstadoAperturaResult.Error -> false
        }
    }

    /**
     * Mensaje estándar cuando no hay apertura
     */
    const val MENSAJE_SIN_APERTURA =
        "⚠️ NO HAY APERTURA DE CAJA\n\n" +
                "Debe realizar la APERTURA DE CAJA antes de registrar ingresos o salidas de vehículos.\n\n" +
                "Vaya a: Inicio → Apertura/Cierre de Caja"
}