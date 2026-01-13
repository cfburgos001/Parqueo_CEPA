package com.cepa.parqueo.database

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

/**
 * Manager para gestión de dispositivos (Terminales POS)
 * ACTUALIZADO: Incluye configuración para habilitar/deshabilitar pagos en POS
 */
class DispositivoManager(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val TAG = "DispositivoManager"

    companion object {
        private const val PREFS_DEVICE = "DeviceConfig"
        private const val KEY_COBROS_HABILITADOS = "cobros_habilitados_pos"
    }

    /**
     * Obtiene o registra el ID del dispositivo actual
     */
    suspend fun obtenerIdDispositivo(): String {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        var idDispositivo = sharedPref.getString("id_dispositivo", null)

        if (idDispositivo == null) {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            idDispositivo = "POS-${androidId.takeLast(8).uppercase()}"
            sharedPref.edit().putString("id_dispositivo", idDispositivo).apply()

            Log.d(TAG, "Nuevo ID de dispositivo generado: $idDispositivo")
        }

        return idDispositivo
    }

    /**
     *  Habilita o deshabilita los cobros en este POS
     * Esta configuración es por dispositivo, NO por usuario
     */
    fun configurarCobrosHabilitados(habilitado: Boolean) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean(KEY_COBROS_HABILITADOS, habilitado).apply()
        Log.d(TAG, "Cobros en POS ${if (habilitado) "HABILITADOS" else "DESHABILITADOS"}")
    }

    /**
     *  Obtiene si los cobros están habilitados en este POS
     * Por defecto: TRUE (habilitado)
     */
    fun estanCobrosHabilitados(): Boolean {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(KEY_COBROS_HABILITADOS, true) // Default: habilitado
    }

    /**
     * Configura el tipo de dispositivo
     */
    fun configurarTipoDispositivo(tipo: String) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putString("tipo_dispositivo", tipo).apply()
        Log.d(TAG, "Tipo de dispositivo configurado: $tipo")
    }

    /**
     * Obtiene el tipo de dispositivo
     */
    fun obtenerTipoDispositivo(): String {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getString("tipo_dispositivo", "MIXTO") ?: "MIXTO"
    }

    /**
     * Configura el ID numérico del dispositivo
     */
    fun configurarIdNumerico(idNumerico: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putInt("id_numerico", idNumerico).apply()
        Log.d(TAG, "ID numérico configurado: $idNumerico")
    }

    /**
     * Obtiene el ID numérico del dispositivo
     */
    fun obtenerIdNumerico(): Int {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getInt("id_numerico", 0)
    }

    /**
     * Configura el ID de entrada (IdEntryDevice)
     */
    fun configurarIdEntryDevice(idEntryDevice: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putInt("id_entry_device", idEntryDevice).apply()
        Log.d(TAG, "ID Entry Device configurado: $idEntryDevice")
    }

    /**
     * Obtiene el ID de entrada (IdEntryDevice)
     */
    fun obtenerIdEntryDevice(): Int {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getInt("id_entry_device", 1) // Default 1
    }

    /**
     * Configura el ID de salida (IdExitDevice)
     */
    fun configurarIdExitDevice(idExitDevice: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putInt("id_exit_device", idExitDevice).apply()
        Log.d(TAG, "ID Exit Device configurado: $idExitDevice")
    }

    /**
     * Obtiene el ID de salida (IdExitDevice)
     */
    fun obtenerIdExitDevice(): Int {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getInt("id_exit_device", 2) // Default 2
    }

    /**
     * Obtiene el ID del dispositivo para entrada según tipo configurado
     */
    fun obtenerIdEntryDeviceParaRegistro(): Int {
        val tipo = obtenerTipoDispositivo()
        return when (tipo) {
            "ENTRADA", "MIXTO" -> obtenerIdEntryDevice()
            else -> 0
        }
    }

    /**
     * Obtiene el ID del dispositivo para salida según tipo configurado
     */
    fun obtenerIdExitDeviceParaRegistro(): Int {
        val tipo = obtenerTipoDispositivo()
        return when (tipo) {
            "SALIDA", "MIXTO" -> obtenerIdExitDevice()
            else -> 0
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
     * y actualiza IdEntryDevice e IdExitDevice
     */
    suspend fun registrarDispositivoEnBD(
        idDispositivo: String,
        nombreDispositivo: String,
        tipoDispositivo: String,
        idNumerico: Int,
        idEntryDevice: Int = 1,
        idExitDevice: Int = 2
    ) {
        withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    throw Exception("No se pudo conectar a la base de datos")
                }

                val macAddress = obtenerMacAddress()

                // 1. Registrar dispositivo
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

                    Log.d(TAG, "✓ Dispositivo registrado: $idDispositivo (ID: $idNumerico) - $mensaje")

                    resultSet.close()
                    callableStatement.close()
                } else {
                    resultSet.close()
                    callableStatement.close()
                    throw Exception("No se obtuvo respuesta del procedimiento")
                }

                // 2. Actualizar IdEntryDevice e IdExitDevice
                val sqlUpdate = """
                    UPDATE IOT_Dispositivos
                    SET IdEntryDevice = ?, IdExitDevice = ?
                    WHERE IdDispositivo = ?
                """
                val stmtUpdate = connection.prepareStatement(sqlUpdate)
                stmtUpdate.setInt(1, idEntryDevice)
                stmtUpdate.setInt(2, idExitDevice)
                stmtUpdate.setString(3, idDispositivo)
                stmtUpdate.executeUpdate()
                stmtUpdate.close()

                Log.d(TAG, "✓ IDs actualizados - Entry: $idEntryDevice, Exit: $idExitDevice")

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