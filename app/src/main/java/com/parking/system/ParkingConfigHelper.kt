package com.parking.system

import android.content.Context
import android.util.Log
import com.parking.system.database.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

/**
 * Helper para gestionar configuraciones del sistema de parqueo
 * Sincroniza entre SharedPreferences (local) y SQL Server (remoto)
 */
object ParkingConfigHelper {

    private const val TAG = "ParkingConfigHelper"
    private const val PREFS_NAME = "ParkingConfig"
    private const val KEY_TIEMPO_GRACIA = "tiempo_gracia_minutos"
    private const val DEFAULT_TIEMPO_GRACIA = 15

    /**
     * Obtiene el tiempo de gracia configurado (en minutos)
     * Intenta primero desde BD, si falla usa SharedPreferences
     * Por defecto: 15 minutos
     */
    suspend fun obtenerTiempoGracia(context: Context, idDispositivo: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                // Intentar obtener de BD
                val dbHelper = DatabaseHelper(context)
                val connection = dbHelper.getConnection()

                if (connection != null) {
                    val tiempoGracia = obtenerTiempoGraciaDesdeBD(connection, idDispositivo)
                    dbHelper.closeConnection(connection)

                    if (tiempoGracia > 0) {
                        // Actualizar SharedPreferences con el valor de BD
                        guardarEnSharedPreferences(context, tiempoGracia)
                        Log.d(TAG, "✓ Tiempo de gracia obtenido de BD: $tiempoGracia min")
                        return@withContext tiempoGracia
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener tiempo de gracia de BD", e)
            }

            // Fallback: Usar SharedPreferences
            val tiempoLocal = obtenerDeSharedPreferences(context)
            Log.d(TAG, "✓ Tiempo de gracia obtenido localmente: $tiempoLocal min")
            tiempoLocal
        }
    }

    /**
     * Obtiene el tiempo de gracia desde SharedPreferences (sin BD)
     * Útil para operaciones síncronas rápidas
     */
    fun obtenerTiempoGraciaLocal(context: Context): Int {
        return obtenerDeSharedPreferences(context)
    }

    /**
     * Guarda el tiempo de gracia y sincroniza con BD
     */
    suspend fun guardarTiempoGracia(
        context: Context,
        idDispositivo: String,
        minutos: Int,
        usuarioModificacion: String? = null
    ): ResultadoGuardado {
        if (minutos < 1 || minutos > 120) {
            return ResultadoGuardado(false, "El tiempo debe estar entre 1 y 120 minutos")
        }

        return withContext(Dispatchers.IO) {
            // 1. Guardar localmente primero (fallback)
            guardarEnSharedPreferences(context, minutos)
            Log.d(TAG, "✓ Tiempo de gracia guardado localmente: $minutos min")

            // 2. Intentar guardar en BD
            try {
                val dbHelper = DatabaseHelper(context)
                val connection = dbHelper.getConnection()

                if (connection != null) {
                    val exito = guardarTiempoGraciaEnBD(
                        connection,
                        idDispositivo,
                        minutos,
                        usuarioModificacion
                    )
                    dbHelper.closeConnection(connection)

                    if (exito) {
                        Log.d(TAG, "✓ Tiempo de gracia sincronizado con BD: $minutos min")
                        return@withContext ResultadoGuardado(
                            true,
                            "Tiempo de gracia actualizado: $minutos minutos\n(Sincronizado con servidor)"
                        )
                    }
                }

                // Si llegamos aquí, no se pudo guardar en BD pero sí localmente
                return@withContext ResultadoGuardado(
                    true,
                    "Tiempo de gracia actualizado: $minutos minutos\n(Guardado localmente, sin conexión a servidor)"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error al guardar en BD", e)
                return@withContext ResultadoGuardado(
                    true,
                    "Tiempo de gracia actualizado: $minutos minutos\n(Guardado localmente, error en servidor)"
                )
            }
        }
    }

    /**
     * Restaura el tiempo de gracia al valor por defecto (15 minutos)
     */
    suspend fun restaurarTiempoGraciaDefault(
        context: Context,
        idDispositivo: String,
        usuarioModificacion: String? = null
    ): ResultadoGuardado {
        return guardarTiempoGracia(context, idDispositivo, DEFAULT_TIEMPO_GRACIA, usuarioModificacion)
    }

    // ===== MÉTODOS PRIVADOS =====

    private fun obtenerDeSharedPreferences(context: Context): Int {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getInt(KEY_TIEMPO_GRACIA, DEFAULT_TIEMPO_GRACIA)
    }

    private fun guardarEnSharedPreferences(context: Context, minutos: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt(KEY_TIEMPO_GRACIA, minutos)
            apply()
        }
    }

    private fun obtenerTiempoGraciaDesdeBD(connection: Connection, idDispositivo: String): Int {
        try {
            val sql = "{CALL dbo.IOT_sp_ObtenerTiempoGracia(?)}"
            val callableStatement = connection.prepareCall(sql)
            callableStatement.setString(1, idDispositivo)

            val resultSet = callableStatement.executeQuery()

            if (resultSet.next()) {
                val tiempoGracia = resultSet.getInt("TiempoGraciaMinutos")
                resultSet.close()
                callableStatement.close()
                return tiempoGracia
            }

            resultSet.close()
            callableStatement.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error en obtenerTiempoGraciaDesdeBD", e)
        }

        return 0
    }

    private fun guardarTiempoGraciaEnBD(
        connection: Connection,
        idDispositivo: String,
        minutos: Int,
        usuarioModificacion: String?
    ): Boolean {
        try {
            val sql = "{CALL dbo.IOT_sp_GuardarTiempoGracia(?, ?, ?)}"
            val callableStatement = connection.prepareCall(sql)
            callableStatement.setString(1, idDispositivo)
            callableStatement.setInt(2, minutos)
            callableStatement.setString(3, usuarioModificacion)

            val resultSet = callableStatement.executeQuery()

            if (resultSet.next()) {
                val exitoso = resultSet.getInt("Exitoso")
                val mensaje = resultSet.getString("Mensaje")

                Log.d(TAG, "BD Response: $mensaje")

                resultSet.close()
                callableStatement.close()

                return exitoso == 1
            }

            resultSet.close()
            callableStatement.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error en guardarTiempoGraciaEnBD", e)
        }

        return false
    }
}

/**
 * Resultado de operación de guardado
 */
data class ResultadoGuardado(
    val exitoso: Boolean,
    val mensaje: String
)

/**
 * Resultado de salida y reingreso por exceso de gracia
 */
data class ResultadoReingresoPorGracia(
    val exitoso: Boolean,
    val mensaje: String,
    val idRegistroAnterior: Int,
    val idNuevoRegistro: Int,
    val codigoBarrasAnterior: String,
    val nuevoCodigoBarras: String
)