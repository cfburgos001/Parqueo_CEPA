package com.cepa.parqueo.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

/**
 * Repositorio para operaciones de Apertura Manual de Barrera
 * Usa tabla separada IOT_ConfigAperturaManual (no toca IOT_TiposLogs)
 * Configuración GLOBAL desde la BD
 */
class AperturaManualRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val TAG = "AperturaManualRepo"

    // =============================================
    // PARA LA APP (botones de entrada/salida)
    // =============================================

    /**
     * Obtiene los tipos de log configurados para un contexto
     * Solo retorna los que el admin habilitó en Mantenimiento
     */
    suspend fun obtenerLogsParaApertura(contexto: String): TiposLogAperturaResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext TiposLogAperturaResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_ObtenerLogsParaApertura(?)}"
                val callableStatement = connection.prepareCall(sql)
                callableStatement.setString(1, contexto)

                val resultSet = callableStatement.executeQuery()
                val tiposLog = mutableListOf<TipoLogApertura>()

                while (resultSet.next()) {
                    tiposLog.add(
                        TipoLogApertura(
                            id = resultSet.getInt("Id"),
                            nombre = resultSet.getString("Nombre"),
                            descripcion = resultSet.getString("Descripcion")
                        )
                    )
                }

                resultSet.close()
                callableStatement.close()

                Log.d(TAG, "✓ ${tiposLog.size} logs configurados para $contexto")
                TiposLogAperturaResult.Success(tiposLog)

            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener logs para apertura", e)
                TiposLogAperturaResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Ejecuta la apertura manual:
     * 1. ComandoBarrera = 1, EstadoBarrera = 1
     * 2. Registra en IOT_Logs con nombre del operador
     */
    suspend fun ejecutarAperturaManual(
        idTipoLog: Int,
        idOperador: Int,
        nombreOperador: String,
        idDispositivo: String,
        contexto: String
    ): AperturaManualResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext AperturaManualResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_EjecutarAperturaManualConLog(?, ?, ?, ?, ?)}"
                val callableStatement = connection.prepareCall(sql)

                callableStatement.setInt(1, idTipoLog)
                callableStatement.setInt(2, idOperador)
                callableStatement.setString(3, nombreOperador)
                callableStatement.setString(4, idDispositivo)
                callableStatement.setString(5, contexto)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val exitoso = resultSet.getInt("Exitoso")
                    val mensaje = resultSet.getString("Mensaje")

                    resultSet.close()
                    callableStatement.close()

                    if (exitoso == 1) {
                        Log.d(TAG, "✓ Apertura manual exitosa: $mensaje")
                        AperturaManualResult.Success(mensaje)
                    } else {
                        Log.w(TAG, "✗ Apertura manual fallida: $mensaje")
                        AperturaManualResult.Error(mensaje)
                    }
                } else {
                    resultSet.close()
                    callableStatement.close()
                    AperturaManualResult.Error("No se obtuvo respuesta del servidor")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al ejecutar apertura manual", e)
                AperturaManualResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    // =============================================
    // PARA MANTENIMIENTO (configuración)
    // =============================================

    /**
     * Lista TODOS los tipos de log activos con sus flags de asignación
     */
    suspend fun listarTodosLosTiposLog(): TiposLogMantenimientoResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext TiposLogMantenimientoResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_ListarTodosLosTiposLog}"
                val callableStatement = connection.prepareCall(sql)
                val resultSet = callableStatement.executeQuery()

                val tiposLog = mutableListOf<TipoLogMantenimiento>()

                while (resultSet.next()) {
                    tiposLog.add(
                        TipoLogMantenimiento(
                            id = resultSet.getInt("Id"),
                            nombre = resultSet.getString("Nombre"),
                            descripcion = resultSet.getString("Descripcion") ?: "",
                            asignadoEntrada = resultSet.getInt("AsignadoEntrada") == 1,
                            asignadoSalida = resultSet.getInt("AsignadoSalida") == 1
                        )
                    )
                }

                resultSet.close()
                callableStatement.close()

                Log.d(TAG, "✓ ${tiposLog.size} tipos de log listados")
                TiposLogMantenimientoResult.Success(tiposLog)

            } catch (e: Exception) {
                Log.e(TAG, "Error al listar tipos de log", e)
                TiposLogMantenimientoResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Agrega un tipo de log a un contexto (ENTRADA/SALIDA)
     */
    suspend fun agregarLogAContexto(
        idTipoLog: Int,
        contexto: String,
        usuarioCreacion: String? = null
    ): DatabaseResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext DatabaseResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_AgregarLogAContexto(?, ?, ?)}"
                val callableStatement = connection.prepareCall(sql)

                callableStatement.setInt(1, idTipoLog)
                callableStatement.setString(2, contexto)
                callableStatement.setString(3, usuarioCreacion)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val exitoso = resultSet.getInt("Exitoso")
                    val mensaje = resultSet.getString("Mensaje")

                    resultSet.close()
                    callableStatement.close()

                    if (exitoso == 1) {
                        Log.d(TAG, "✓ Log agregado: $mensaje")
                        DatabaseResult.Success(mensaje)
                    } else {
                        DatabaseResult.Error(mensaje)
                    }
                } else {
                    resultSet.close()
                    callableStatement.close()
                    DatabaseResult.Error("No se obtuvo respuesta")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al agregar log a contexto", e)
                DatabaseResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Quita un tipo de log de un contexto (ENTRADA/SALIDA)
     */
    suspend fun quitarLogDeContexto(
        idTipoLog: Int,
        contexto: String
    ): DatabaseResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext DatabaseResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_QuitarLogDeContexto(?, ?)}"
                val callableStatement = connection.prepareCall(sql)

                callableStatement.setInt(1, idTipoLog)
                callableStatement.setString(2, contexto)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val exitoso = resultSet.getInt("Exitoso")
                    val mensaje = resultSet.getString("Mensaje")

                    resultSet.close()
                    callableStatement.close()

                    if (exitoso == 1) {
                        Log.d(TAG, "✓ Log removido: $mensaje")
                        DatabaseResult.Success(mensaje)
                    } else {
                        DatabaseResult.Error(mensaje)
                    }
                } else {
                    resultSet.close()
                    callableStatement.close()
                    DatabaseResult.Error("No se obtuvo respuesta")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al quitar log de contexto", e)
                DatabaseResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }
}

// ===== DATA CLASSES =====

/** Tipo de log para mostrar en el diálogo de apertura manual */
data class TipoLogApertura(
    val id: Int,
    val nombre: String,
    val descripcion: String
)

/** Tipo de log para la pantalla de mantenimiento (con flags de asignación) */
data class TipoLogMantenimiento(
    val id: Int,
    val nombre: String,
    val descripcion: String,
    val asignadoEntrada: Boolean,
    val asignadoSalida: Boolean
)

// ===== SEALED CLASSES =====

sealed class TiposLogAperturaResult {
    data class Success(val tiposLog: List<TipoLogApertura>) : TiposLogAperturaResult()
    data class Error(val message: String) : TiposLogAperturaResult()
}

sealed class AperturaManualResult {
    data class Success(val mensaje: String) : AperturaManualResult()
    data class Error(val message: String) : AperturaManualResult()
}

sealed class TiposLogMantenimientoResult {
    data class Success(val tiposLog: List<TipoLogMantenimiento>) : TiposLogMantenimientoResult()
    data class Error(val message: String) : TiposLogMantenimientoResult()
}