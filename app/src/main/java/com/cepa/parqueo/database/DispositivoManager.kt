package com.cepa.parqueo.database

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

class DispositivoManager(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val TAG = "DispositivoManager"

    companion object {
        private const val PREFS_DEVICE           = "DeviceConfig"
        private const val KEY_COBROS_HABILITADOS  = "cobros_habilitados_pos"
        private const val KEY_ID_BARRERA          = "id_barrera_asignada"
    }

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

    fun configurarCobrosHabilitados(habilitado: Boolean) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean(KEY_COBROS_HABILITADOS, habilitado).apply()
        Log.d(TAG, "Cobros en POS ${if (habilitado) "HABILITADOS" else "DESHABILITADOS"}")
    }

    fun estanCobrosHabilitados(): Boolean {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(KEY_COBROS_HABILITADOS, true)
    }

    fun configurarTipoDispositivo(tipo: String) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putString("tipo_dispositivo", tipo).apply()
        Log.d(TAG, "Tipo de dispositivo configurado: $tipo")
    }

    fun obtenerTipoDispositivo(): String {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getString("tipo_dispositivo", "MIXTO") ?: "MIXTO"
    }

    fun configurarIdNumerico(idNumerico: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putInt("id_numerico", idNumerico).apply()
        Log.d(TAG, "ID numérico configurado: $idNumerico")
    }

    fun obtenerIdNumerico(): Int {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getInt("id_numerico", 0)
    }

    fun configurarIdEntryDevice(idEntryDevice: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putInt("id_entry_device", idEntryDevice).apply()
        Log.d(TAG, "ID Entry Device configurado: $idEntryDevice")
    }

    fun obtenerIdEntryDevice(): Int {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getInt("id_entry_device", 1)
    }

    fun configurarIdExitDevice(idExitDevice: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putInt("id_exit_device", idExitDevice).apply()
        Log.d(TAG, "ID Exit Device configurado: $idExitDevice")
    }

    fun obtenerIdExitDevice(): Int {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getInt("id_exit_device", 2)
    }

    // =============================================
    // BARRERA ASIGNADA
    // =============================================

    fun configurarIdBarrera(idBarrera: Int) {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        sharedPref.edit().putInt(KEY_ID_BARRERA, idBarrera).apply()
        Log.d(TAG, "Barrera asignada configurada: ID $idBarrera")
    }

    fun obtenerIdBarrera(): Int {
        val sharedPref = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
        return sharedPref.getInt(KEY_ID_BARRERA, 1)
    }

    /** Persiste IdBarreraAsignada en IOT_Dispositivos para que sea visible desde el web */
    suspend fun persistirBarreraEnBD(idBarrera: Int) {
        withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection() ?: return@withContext

                val idDispositivo = obtenerIdDispositivo()
                val sql = "UPDATE IOT_Dispositivos SET IdBarreraAsignada = ? WHERE IdDispositivo = ?"
                val stmt = connection.prepareStatement(sql)
                stmt.setInt(1, idBarrera)
                stmt.setString(2, idDispositivo)
                stmt.executeUpdate()
                stmt.close()

                Log.d(TAG, "IdBarreraAsignada=$idBarrera persistido en BD para $idDispositivo")
            } catch (e: Exception) {
                Log.e(TAG, "Error al persistir barrera en BD", e)
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    // =============================================
    // HELPERS
    // =============================================

    fun obtenerIdEntryDeviceParaRegistro(): Int {
        val tipo = obtenerTipoDispositivo()
        return when (tipo) {
            "ENTRADA", "MIXTO" -> obtenerIdEntryDevice()
            else -> 0
        }
    }

    fun obtenerIdExitDeviceParaRegistro(): Int {
        val tipo = obtenerTipoDispositivo()
        return when (tipo) {
            "SALIDA", "MIXTO" -> obtenerIdExitDevice()
            else -> 0
        }
    }

    fun puedeRegistrarEntrada(): Boolean {
        val tipo = obtenerTipoDispositivo()
        return tipo == "ENTRADA" || tipo == "MIXTO"
    }

    fun puedeRegistrarSalida(): Boolean {
        val tipo = obtenerTipoDispositivo()
        return tipo == "SALIDA" || tipo == "MIXTO"
    }

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
                    ?: throw Exception("No se pudo conectar a la base de datos")

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
                    val mensaje = resultSet.getString("Mensaje")
                    Log.d(TAG, "Dispositivo registrado: $idDispositivo - $mensaje")
                }
                resultSet.close()
                callableStatement.close()

                // Actualiza Entry, Exit e IdBarreraAsignada en un solo UPDATE
                val sqlUpdate = """
                    UPDATE IOT_Dispositivos
                    SET IdEntryDevice     = ?,
                        IdExitDevice      = ?,
                        IdBarreraAsignada = ?
                    WHERE IdDispositivo = ?
                """.trimIndent()
                val stmtUpdate = connection.prepareStatement(sqlUpdate)
                stmtUpdate.setInt(1, idEntryDevice)
                stmtUpdate.setInt(2, idExitDevice)
                stmtUpdate.setInt(3, obtenerIdBarrera())
                stmtUpdate.setString(4, idDispositivo)
                stmtUpdate.executeUpdate()
                stmtUpdate.close()

                Log.d(TAG, "IDs actualizados - Entry: $idEntryDevice, Exit: $idExitDevice, Barrera: ${obtenerIdBarrera()}")

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
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}
