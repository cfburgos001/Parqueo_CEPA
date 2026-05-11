package com.cepa.parqueo.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

class AperturaManualRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val TAG = "AperturaManualRepo"

    // =============================================
    // PARA LA APP (botones de entrada/salida)
    // =============================================

    suspend fun obtenerLogsParaApertura(contexto: String): TiposLogAperturaResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()
                    ?: return@withContext TiposLogAperturaResult.Error("No se pudo conectar a la base de datos")

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

                Log.d(TAG, "${tiposLog.size} logs configurados para $contexto")
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
     * Ejecuta la apertura manual sobre la barrera indicada.
     * @param idBarrera  ID de la fila en IOT_Barrera a abrir (default 1).
     */
    suspend fun ejecutarAperturaManual(
        idTipoLog: Int,
        idOperador: Int,
        nombreOperador: String,
        idDispositivo: String,
        contexto: String,
        idBarrera: Int = 1
    ): AperturaManualResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()
                    ?: return@withContext AperturaManualResult.Error("No se pudo conectar a la base de datos")

                val sql = "{CALL dbo.IOT_sp_EjecutarAperturaManualConLog(?, ?, ?, ?, ?, ?)}"
                val callableStatement = connection.prepareCall(sql)
                callableStatement.setInt(1, idTipoLog)
                callableStatement.setInt(2, idOperador)
                callableStatement.setString(3, nombreOperador)
                callableStatement.setString(4, idDispositivo)
                callableStatement.setString(5, contexto)
                callableStatement.setInt(6, idBarrera)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val exitoso = resultSet.getInt("Exitoso")
                    val mensaje = resultSet.getString("Mensaje")
                    resultSet.close()
                    callableStatement.close()

                    if (exitoso == 1) {
                        Log.d(TAG, "Apertura manual exitosa (barrera $idBarrera): $mensaje")
                        AperturaManualResult.Success(mensaje)
                    } else {
                        Log.w(TAG, "Apertura manual fallida: $mensaje")
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
    // BARRERAS
    // =============================================

    /** Lista todas las filas de IOT_Barrera para mostrarlas en Configurar Barrera */
    suspend fun listarBarreras(): BarrerasResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()
                    ?: return@withContext BarrerasResult.Error("No se pudo conectar a la base de datos")

                val sql = "{CALL dbo.IOT_sp_ListarBarreras}"
                val callableStatement = connection.prepareCall(sql)
                val resultSet = callableStatement.executeQuery()

                val barreras = mutableListOf<BarreraItem>()
                while (resultSet.next()) {
                    barreras.add(
                        BarreraItem(
                            id = resultSet.getInt("ID"),
                            barreraSeteo = resultSet.getString("BarreraSeteo") ?: "",
                            estadoBarrera = resultSet.getBoolean("EstadoBarrera")
                        )
                    )
                }

                resultSet.close()
                callableStatement.close()

                Log.d(TAG, "${barreras.size} barreras listadas")
                BarrerasResult.Success(barreras)

            } catch (e: Exception) {
                Log.e(TAG, "Error al listar barreras", e)
                BarrerasResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    // =============================================
    // PARA MANTENIMIENTO (configuracion de logs)
    // =============================================

    suspend fun listarTodosLosTiposLog(): TiposLogMantenimientoResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()
                    ?: return@withContext TiposLogMantenimientoResult.Error("No se pudo conectar a la base de datos")

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

                Log.d(TAG, "${tiposLog.size} tipos de log listados")
                TiposLogMantenimientoResult.Success(tiposLog)

            } catch (e: Exception) {
                Log.e(TAG, "Error al listar tipos de log", e)
                TiposLogMantenimientoResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    suspend fun agregarLogAContexto(idTipoLog: Int, contexto: String, usuarioCreacion: String? = null): DatabaseResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()
                    ?: return@withContext DatabaseResult.Error("No se pudo conectar a la base de datos")

                val sql = "{CALL dbo.IOT_sp_AgregarLogAContexto(?, ?, ?)}"
                val cs = connection.prepareCall(sql)
                cs.setInt(1, idTipoLog)
                cs.setString(2, contexto)
                cs.setString(3, usuarioCreacion)

                val rs = cs.executeQuery()
                if (rs.next()) {
                    val exitoso = rs.getInt("Exitoso")
                    val mensaje = rs.getString("Mensaje")
                    rs.close(); cs.close()
                    if (exitoso == 1) DatabaseResult.Success(mensaje) else DatabaseResult.Error(mensaje)
                } else {
                    rs.close(); cs.close()
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

    suspend fun quitarLogDeContexto(idTipoLog: Int, contexto: String): DatabaseResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()
                    ?: return@withContext DatabaseResult.Error("No se pudo conectar a la base de datos")

                val sql = "{CALL dbo.IOT_sp_QuitarLogDeContexto(?, ?)}"
                val cs = connection.prepareCall(sql)
                cs.setInt(1, idTipoLog)
                cs.setString(2, contexto)

                val rs = cs.executeQuery()
                if (rs.next()) {
                    val exitoso = rs.getInt("Exitoso")
                    val mensaje = rs.getString("Mensaje")
                    rs.close(); cs.close()
                    if (exitoso == 1) DatabaseResult.Success(mensaje) else DatabaseResult.Error(mensaje)
                } else {
                    rs.close(); cs.close()
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

data class TipoLogApertura(val id: Int, val nombre: String, val descripcion: String)

data class TipoLogMantenimiento(
    val id: Int,
    val nombre: String,
    val descripcion: String,
    val asignadoEntrada: Boolean,
    val asignadoSalida: Boolean
)

data class BarreraItem(
    val id: Int,
    val barreraSeteo: String,
    val estadoBarrera: Boolean
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

sealed class BarrerasResult {
    data class Success(val barreras: List<BarreraItem>) : BarrerasResult()
    data class Error(val message: String) : BarrerasResult()
}
