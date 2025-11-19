package com.parking.system.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Helper para conexión con SQL Server usando JTDS
 * Actualizado para usar nueva BD: Datapark con prefijo IOT_
 */
class DatabaseHelper(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseHelper"
        private const val DRIVER = "net.sourceforge.jtds.jdbc.Driver"
    }

    /**
     * Obtiene la configuración del servidor desde SharedPreferences
     */
    private fun getServerConfig(): ServerConfig {
        val sharedPref = context.getSharedPreferences("ServerConfig", Context.MODE_PRIVATE)
        return ServerConfig(
            serverIp = sharedPref.getString("server_ip", "10.0.1.39") ?: "10.0.1.39",
            serverPort = sharedPref.getString("server_port", "1433") ?: "1433",
            databaseName = sharedPref.getString("database_name", "Datapark") ?: "Datapark",
            username = sharedPref.getString("db_username", "pos") ?: "pos",
            password = sharedPref.getString("db_password", "Po\$2025#") ?: "Po\$2025#"
        )
    }

    /**
     * Obtiene una conexión a SQL Server
     * IMPORTANTE: Usar siempre en coroutine con Dispatchers.IO
     */
    suspend fun getConnection(): Connection? = withContext(Dispatchers.IO) {
        try {
            val config = getServerConfig()

            // Cargar el driver JTDS
            Class.forName(DRIVER)

            // Construir URL de conexión
            // Formato: jdbc:jtds:sqlserver://IP:PUERTO/DATABASE
            val connectionUrl = buildString {
                append("jdbc:jtds:sqlserver://")
                append(config.serverIp)
                append(":")
                append(config.serverPort)
                append("/")
                append(config.databaseName)
                append(";user=")
                append(config.username)
                append(";password=")
                append(config.password)
            }

            Log.d(TAG, "Intentando conectar a: ${config.serverIp}:${config.serverPort}/${config.databaseName}")

            val connection = DriverManager.getConnection(connectionUrl)
            Log.d(TAG, "✓ Conexión exitosa a Datapark")
            connection

        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Driver JTDS no encontrado", e)
            null
        } catch (e: SQLException) {
            Log.e(TAG, "Error SQL: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error de conexión: ${e.message}", e)
            null
        }
    }

    /**
     * Prueba la conexión a la base de datos
     */
    suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            val connection = getConnection()

            if (connection != null) {
                // Hacer una consulta simple para verificar
                val statement = connection.createStatement()
                val resultSet = statement.executeQuery("SELECT @@VERSION AS Version")

                var version = "Desconocida"
                if (resultSet.next()) {
                    version = resultSet.getString("Version")
                }

                resultSet.close()
                statement.close()
                connection.close()

                ConnectionResult.Success("Conexión exitosa a Datapark\nSQL Server: ${version.take(50)}...")
            } else {
                ConnectionResult.Error("No se pudo establecer conexión")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en test de conexión", e)
            ConnectionResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Cierra una conexión de forma segura
     */
    fun closeConnection(connection: Connection?) {
        try {
            connection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar conexión", e)
        }
    }
}

/**
 * Configuración del servidor
 */
data class ServerConfig(
    val serverIp: String,
    val serverPort: String,
    val databaseName: String,
    val username: String,
    val password: String
)

/**
 * Resultado de una prueba de conexión
 */
sealed class ConnectionResult {
    data class Success(val message: String) : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}