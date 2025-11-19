package com.parking.system.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

/**
 * Repositorio para operaciones con IOT_Operadores
 */
class OperadorRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val TAG = "OperadorRepository"

    /**
     * Valida credenciales usando dbo.IOT_sp_ValidarOperador
     */
    suspend fun validarOperador(username: String, password: String): OperadorResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext OperadorResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_ValidarOperador(?, ?)}"
                val callableStatement = connection.prepareCall(sql)
                callableStatement.setString(1, username)
                callableStatement.setString(2, password)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val activo = resultSet.getBoolean("Activo")

                    if (!activo) {
                        resultSet.close()
                        callableStatement.close()
                        return@withContext OperadorResult.Error("Usuario inactivo")
                    }

                    val operador = Operador(
                        id = resultSet.getInt("Id"),
                        username = resultSet.getString("Username"),
                        nombre = resultSet.getString("Nombre"),
                        apellido = resultSet.getString("Apellido"),
                        tipoUsuario = resultSet.getString("TipoUsuario")
                    )

                    resultSet.close()
                    callableStatement.close()

                    Log.d(TAG, "✓ Login exitoso: ${operador.username}")
                    OperadorResult.Success(operador)
                } else {
                    resultSet.close()
                    callableStatement.close()

                    Log.d(TAG, "✗ Credenciales inválidas")
                    OperadorResult.Error("Usuario o contraseña incorrectos")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al validar operador", e)
                OperadorResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Crea un nuevo operador usando dbo.IOT_sp_CrearOperador
     */
    suspend fun crearOperador(
        username: String,
        password: String,
        nombre: String,
        apellido: String,
        tipoUsuario: String
    ): DatabaseResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext DatabaseResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_CrearOperador(?, ?, ?, ?, ?)}"
                val callableStatement = connection.prepareCall(sql)
                callableStatement.setString(1, username)
                callableStatement.setString(2, password)
                callableStatement.setString(3, nombre)
                callableStatement.setString(4, apellido)
                callableStatement.setString(5, tipoUsuario)

                val resultSet = callableStatement.executeQuery()

                if (resultSet.next()) {
                    val id = resultSet.getInt("Id")
                    val mensaje = resultSet.getString("Mensaje")

                    resultSet.close()
                    callableStatement.close()

                    if (id > 0) {
                        Log.d(TAG, "✓ Operador creado: $username")
                        DatabaseResult.Success(mensaje)
                    } else {
                        Log.d(TAG, "✗ Error: $mensaje")
                        DatabaseResult.Error(mensaje)
                    }
                } else {
                    resultSet.close()
                    callableStatement.close()
                    DatabaseResult.Error("Error al crear operador")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al crear operador", e)
                DatabaseResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }

    /**
     * Lista todos los operadores usando dbo.IOT_sp_ListarOperadores
     */
    suspend fun listarOperadores(): ListaOperadoresResult {
        return withContext(Dispatchers.IO) {
            var connection: Connection? = null
            try {
                connection = dbHelper.getConnection()

                if (connection == null) {
                    return@withContext ListaOperadoresResult.Error("No se pudo conectar a la base de datos")
                }

                val sql = "{CALL dbo.IOT_sp_ListarOperadores}"
                val callableStatement = connection.prepareCall(sql)
                val resultSet = callableStatement.executeQuery()

                val operadores = mutableListOf<Operador>()

                while (resultSet.next()) {
                    operadores.add(
                        Operador(
                            id = resultSet.getInt("Id"),
                            username = resultSet.getString("Username"),
                            nombre = resultSet.getString("Nombre"),
                            apellido = resultSet.getString("Apellido"),
                            tipoUsuario = resultSet.getString("TipoUsuario")
                        )
                    )
                }

                resultSet.close()
                callableStatement.close()

                Log.d(TAG, "✓ ${operadores.size} operadores listados")
                ListaOperadoresResult.Success(operadores)

            } catch (e: Exception) {
                Log.e(TAG, "Error al listar operadores", e)
                ListaOperadoresResult.Error("Error: ${e.message}")
            } finally {
                dbHelper.closeConnection(connection)
            }
        }
    }
}

/**
 * Clase de datos para Operador
 */
data class Operador(
    val id: Int,
    val username: String,
    val nombre: String,
    val apellido: String,
    val tipoUsuario: String
) {
    fun getNombreCompleto(): String = "$nombre $apellido"
}

/**
 * Resultado de validación de operador
 */
sealed class OperadorResult {
    data class Success(val operador: Operador) : OperadorResult()
    data class Error(val message: String) : OperadorResult()
}

/**
 * Resultado de lista de operadores
 */
sealed class ListaOperadoresResult {
    data class Success(val operadores: List<Operador>) : ListaOperadoresResult()
    data class Error(val message: String) : ListaOperadoresResult()
}