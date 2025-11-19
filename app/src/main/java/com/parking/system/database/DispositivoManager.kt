package com.parking.system.database

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

/**
 * Manager para gestión de dispositivos (Terminales POS)
 * Actualizado para usar IOT_Dispositivos
 */
class DispositivoManager(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val TAG = "DispositivoManager"

    /**
     * Obtiene o registra el ID del dispositivo actual
     */
    suspend fun obtenerIdDispositivo(): String {
        val sharedPref = context.getSharedPreferences("DeviceConfig", Context.MODE_PRIVATE)
        var idDispositivo = sharedPref.getString("id_dispositivo", null)

        if (idDispositivo == null) {
            // Generar ID basado en Android ID
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            idDispositivo = "POS-${androidId.takeLast(8).uppercase()}"

            // Guardar
            sharedPref.edit().putString("id_dispositivo", idDispositivo).apply()

            Log.d(TAG, "Nuevo ID de dispositivo generado: $idDispositivo")
        }

        return idDispositivo
    }

    /**
     * Configura el tipo de dispositivo
     */
    fun configurarTipoDispositivo(tipo: String) {
        val sharedPref = context.getSharedPreferences("DeviceConfig", Context.MODE_PRIVATE)
        sharedPref.edit().putString("tipo_dispositivo", tipo).apply()
        Log.d(TAG, "Tipo de dispositivo configurado: $tipo")
    }

    /**
     * Obtiene el tipo de dispositivo
     */
    fun obtenerTipoDispositivo(): String {
        val sharedPref = context.getSharedPreferences("DeviceConfig", Context.MODE_PRIVATE)
        return sharedPref.getString("tipo_dispositivo", "MIXTO") ?: "MIXTO"
    }

    /**
     * Configura el ID numérico del dispositivo
     */
    fun configurarIdNumerico(idNumerico: Int) {
        val sharedPref = context.getSharedPreferences("DeviceConfig", Context.MODE_PRIVATE)
        sharedPref.edit().putInt("id_numerico", idNumerico).apply()
        Log.d(TAG, "ID numérico configurado: $idNumerico")
    }

    /**
     * Obtiene el ID numérico del dispositivo
     */
    fun obtenerIdNumerico(): Int {
        val sharedPref = context.getSharedPreferences("DeviceConfig", Context.MODE_PRIVATE)
        return sharedPref.getInt("id_numerico", 0)
    }

    /**
     * Obtiene el ID del dispositivo para entrada (IdEntryDevice)
     * Según el tipo de dispositivo configurado
     */
    fun obtenerIdEntryDevice(): Int {
        val tipo = obtenerTipoDispositivo()
        return when (tipo) {
            "ENTRADA", "MIXTO" -> 1  // Dispositivos de entrada o mixtos usan ID 1
            else -> 0  // Dispositivos de solo salida no registran entrada
        }
    }

    /**
     * Obtiene el ID del dispositivo para salida (IdExitDevice)
     * Según el tipo de dispositivo configurado
     */
    fun obtenerIdExitDevice(): Int {
        val tipo = obtenerTipoDispositivo()
        return when (tipo) {
            "SALIDA", "MIXTO" -> 2  // Dispositivos de salida o mixtos usan ID 2
            else -> 0  // Dispositivos de solo entrada no registran salida
        }
    }

    /**
     * Verifica si el dispositivo puede registrar entradas
     */
    fun puedeRegistrarEntrada(): Boolean {
        val tipo = obtenerTipoDispositivo()
        return tipo == "ENTRADA" || tipo == "MIXTO"
    }

    /**
     * Verifica si el dispositivo puede registrar salidas
     */
    fun puedeRegistrarSalida(): Boolean {
        val tipo = obtenerTipoDispositivo()
        return tipo == "SALIDA" || tipo == "MIXTO"
    }

    /**
     * Registra el dispositivo usando dbo.IOT_sp_RegistrarDispositivo
     * VERSIÓN ACTUALIZADA con idNumerico
     */
    suspend fun registrarDispositivoEnBD(
        idDispositivo: String,
        nombreDispositivo: String,
        tipoDispositivo: String,
        idNumerico: Int
    ) {
        withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    throw Exception("No se pudo conectar a la base de datos")
                }

                val macAddress = obtenerMacAddress()

                val sql = "{CALL dbo.IOT_sp_RegistrarDispositivo(?, ?, ?, ?, ?)}"
                val callableStatement = connection.prepareCall(sql)
                callableStatement.setString(1, idDispositivo)
                callableStatement.setString(2, nombreDispositivo)
                callableStatement.setString(3, tipoDispositivo)
                callableStatement.setString(4, macAddress)
                callableStatement.setInt(5, idNumerico)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val id = resultSet.getInt("Id")
                    val mensaje = resultSet.getString("Mensaje")

                    Log.d(TAG, "✓ Dispositivo registrado: $idDispositivo (ID Numérico: $idNumerico) - $mensaje")

                    resultSet.close()
                    callableStatement.close()
                } else {
                    resultSet.close()
                    callableStatement.close()
                    throw Exception("No se obtuvo respuesta del procedimiento")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar dispositivo en BD", e)
                throw e
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    private fun obtenerMacAddress(): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            androidId ?: "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}