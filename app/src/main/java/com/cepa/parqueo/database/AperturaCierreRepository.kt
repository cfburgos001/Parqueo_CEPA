package com.cepa.parqueo.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.sql.Connection
import java.util.Date

/**
 * Repositorio para operaciones de Apertura y Cierre de Caja
 */
class AperturaCierreRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val TAG = "AperturaCierreRepo"

    /**
     * Registra la apertura de caja
     */
    suspend fun registrarApertura(
        idOperador: Int,
        nombreOperador: String,
        idDispositivo: String
    ): AperturaResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext AperturaResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_RegistrarApertura(?, ?, ?)}"
                val callableStatement = connection.prepareCall(sql)

                callableStatement.setInt(1, idOperador)
                callableStatement.setString(2, nombreOperador)
                callableStatement.setString(3, idDispositivo)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val exitoso = resultSet.getInt("Exitoso")
                    val mensaje = resultSet.getString("Mensaje")

                    if (exitoso == 1) {
                        val id = resultSet.getInt("Id")
                        val fechaOperacion = resultSet.getTimestamp("FechaOperacion")

                        resultSet.close()
                        callableStatement.close()

                        Log.d(TAG, "✓ Apertura registrada - ID: $id")
                        return@withContext AperturaResult.Success(
                            mensaje = mensaje,
                            id = id,
                            fechaOperacion = fechaOperacion
                        )
                    } else {
                        resultSet.close()
                        callableStatement.close()

                        Log.d(TAG, "✗ Error en apertura: $mensaje")
                        return@withContext AperturaResult.Error(mensaje)
                    }
                } else {
                    resultSet.close()
                    callableStatement.close()
                    return@withContext AperturaResult.Error("No se obtuvo respuesta del procedimiento")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar apertura", e)
                return@withContext AperturaResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Registra el cierre de caja
     */
    suspend fun registrarCierre(
        idOperador: Int,
        nombreOperador: String,
        idDispositivo: String
    ): CierreResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext CierreResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_RegistrarCierre(?, ?, ?)}"
                val callableStatement = connection.prepareCall(sql)

                callableStatement.setInt(1, idOperador)
                callableStatement.setString(2, nombreOperador)
                callableStatement.setString(3, idDispositivo)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val exitoso = resultSet.getInt("Exitoso")
                    val mensaje = resultSet.getString("Mensaje")

                    if (exitoso == 1) {
                        val id = resultSet.getInt("Id")
                        val fechaOperacion = resultSet.getTimestamp("FechaOperacion")
                        val fechaApertura = resultSet.getTimestamp("FechaApertura")
                        val montoTotal = resultSet.getBigDecimal("MontoTotal")?.toDouble() ?: 0.0
                        val cantidadVehiculos = resultSet.getInt("CantidadVehiculos")
                        val vehiculosDentro = resultSet.getInt("VehiculosDentro")
                        val vehiculosDetalleJson = resultSet.getString("VehiculosDetalle") ?: "[]"

                        // Parsear JSON de vehículos dentro
                        val vehiculosDentroLista = parseVehiculosDentro(vehiculosDetalleJson)

                        resultSet.close()
                        callableStatement.close()

                        Log.d(TAG, "✓ Cierre registrado - ID: $id, Monto: $montoTotal")
                        return@withContext CierreResult.Success(
                            mensaje = mensaje,
                            id = id,
                            fechaOperacion = fechaOperacion,
                            fechaApertura = fechaApertura,
                            montoTotal = montoTotal,
                            cantidadVehiculos = cantidadVehiculos,
                            vehiculosDentro = vehiculosDentro,
                            vehiculosDentroDetalle = vehiculosDentroLista
                        )
                    } else {
                        resultSet.close()
                        callableStatement.close()

                        Log.d(TAG, "✗ Error en cierre: $mensaje")
                        return@withContext CierreResult.Error(mensaje)
                    }
                } else {
                    resultSet.close()
                    callableStatement.close()
                    return@withContext CierreResult.Error("No se obtuvo respuesta del procedimiento")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al registrar cierre", e)
                return@withContext CierreResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Genera un Reporte X (sin cerrar la caja)
     */
    suspend fun generarReporteX(
        idOperador: Int,
        nombreOperador: String,
        idDispositivo: String
    ): CierreResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext CierreResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_GenerarReporteX(?, ?, ?)}"
                val callableStatement = connection.prepareCall(sql)

                callableStatement.setInt(1, idOperador)
                callableStatement.setString(2, nombreOperador)
                callableStatement.setString(3, idDispositivo)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val exitoso = resultSet.getInt("Exitoso")
                    val mensaje = resultSet.getString("Mensaje")

                    if (exitoso == 1) {
                        val id = resultSet.getInt("Id")
                        val fechaOperacion = resultSet.getTimestamp("FechaOperacion")
                        val fechaApertura = resultSet.getTimestamp("FechaApertura")
                        val montoTotal = resultSet.getBigDecimal("MontoTotal")?.toDouble() ?: 0.0
                        val cantidadVehiculos = resultSet.getInt("CantidadVehiculos")
                        val vehiculosDentro = resultSet.getInt("VehiculosDentro")
                        val vehiculosDetalleJson = resultSet.getString("VehiculosDetalle") ?: "[]"

                        val vehiculosDentroLista = parseVehiculosDentro(vehiculosDetalleJson)

                        resultSet.close()
                        callableStatement.close()

                        Log.d(TAG, "✓ Reporte X generado - ID: $id, Monto: $montoTotal")
                        return@withContext CierreResult.Success(
                            mensaje = mensaje,
                            id = id,
                            fechaOperacion = fechaOperacion,
                            fechaApertura = fechaApertura,
                            montoTotal = montoTotal,
                            cantidadVehiculos = cantidadVehiculos,
                            vehiculosDentro = vehiculosDentro,
                            vehiculosDentroDetalle = vehiculosDentroLista
                        )
                    } else {
                        resultSet.close()
                        callableStatement.close()

                        Log.d(TAG, "✗ Error al generar Reporte X: $mensaje")
                        return@withContext CierreResult.Error(mensaje)
                    }
                } else {
                    resultSet.close()
                    callableStatement.close()
                    return@withContext CierreResult.Error("No se obtuvo respuesta del procedimiento")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al generar Reporte X", e)
                return@withContext CierreResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Obtiene el estado de apertura actual
     */
    suspend fun obtenerEstadoApertura(idDispositivo: String): EstadoAperturaResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext EstadoAperturaResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_ObtenerEstadoApertura(?)}"
                val callableStatement = connection.prepareCall(sql)

                callableStatement.setString(1, idDispositivo)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val hayApertura = resultSet.getInt("HayApertura") == 1

                    if (hayApertura) {
                        val fechaApertura = resultSet.getTimestamp("FechaApertura")
                        val operadorApertura = resultSet.getString("OperadorApertura")

                        resultSet.close()
                        callableStatement.close()

                        return@withContext EstadoAperturaResult.Success(
                            hayApertura = true,
                            fechaApertura = fechaApertura,
                            operadorApertura = operadorApertura
                        )
                    } else {
                        resultSet.close()
                        callableStatement.close()

                        return@withContext EstadoAperturaResult.Success(
                            hayApertura = false,
                            fechaApertura = null,
                            operadorApertura = null
                        )
                    }
                } else {
                    resultSet.close()
                    callableStatement.close()
                    return@withContext EstadoAperturaResult.Error("No se obtuvo respuesta del procedimiento")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener estado de apertura", e)
                return@withContext EstadoAperturaResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Parsea el JSON de vehículos dentro
     */
    private fun parseVehiculosDentro(json: String): List<VehiculoDentro> {
        return try {
            val jsonArray = JSONArray(json)
            val lista = mutableListOf<VehiculoDentro>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                lista.add(
                    VehiculoDentro(
                        placa = obj.getString("Placa"),
                        fechaEntrada = obj.getString("FechaEntrada"),
                        pagado = obj.getInt("Pagado") == 1,
                        monto = obj.getDouble("Monto"),
                        tipoVehiculo = obj.optString("TipoVehiculo", "A")
                    )
                )
            }

            lista
        } catch (e: Exception) {
            Log.e(TAG, "Error al parsear vehículos dentro", e)
            emptyList()
        }
    }
}

// ===== DATA CLASSES =====

data class VehiculoDentro(
    val placa: String,
    val fechaEntrada: String,
    val pagado: Boolean,
    val monto: Double,
    val tipoVehiculo: String
)

// ===== SEALED CLASSES =====

sealed class AperturaResult {
    data class Success(
        val mensaje: String,
        val id: Int,
        val fechaOperacion: Date
    ) : AperturaResult()

    data class Error(val message: String) : AperturaResult()
}

sealed class CierreResult {
    data class Success(
        val mensaje: String,
        val id: Int,
        val fechaOperacion: Date,
        val fechaApertura: Date,
        val montoTotal: Double,
        val cantidadVehiculos: Int,
        val vehiculosDentro: Int,
        val vehiculosDentroDetalle: List<VehiculoDentro>
    ) : CierreResult()

    data class Error(val message: String) : CierreResult()
}

sealed class EstadoAperturaResult {
    data class Success(
        val hayApertura: Boolean,
        val fechaApertura: Date?,
        val operadorApertura: String?
    ) : EstadoAperturaResult()

    data class Error(val message: String) : EstadoAperturaResult()
}