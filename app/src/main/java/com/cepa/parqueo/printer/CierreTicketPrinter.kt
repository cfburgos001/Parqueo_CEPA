package com.cepa.parqueo.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import com.cepa.parqueo.CierreTicketData
import java.io.OutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Printer Manager para Ticket de Cierre de Caja
 */
@SuppressLint("MissingPermission")
object CierreTicketPrinter {

    private const val PRINTER_MAC = "66:11:22:33:44:55"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun getDevice(): BluetoothDevice? {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) return null
            adapter.getRemoteDevice(PRINTER_MAC)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun printCierreTicket(context: Context, data: CierreTicketData) {
        val fallbackText = buildTicketText(data)

        try {
            val device = getDevice()
            if (device == null) {
                showFallback(context, fallbackText, "Impresora no encontrada")
                return
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.cancelDiscovery()

            val socket = try {
                val socketSafe = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socketSafe.connect()
                socketSafe
            } catch (e1: Exception) {
                try {
                    val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    insecure.connect()
                    insecure
                } catch (e2: Exception) {
                    showFallback(context, fallbackText, "No se pudo conectar")
                    return
                }
            }

            val outputStream: OutputStream = socket.outputStream
            sendEscPosPrint(outputStream, data)
            outputStream.flush()
            socket.close()

            Toast.makeText(context, "✓ Ticket de cierre impreso", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            showFallback(context, fallbackText, "Error: ${e.message}")
        }
    }

    private fun sendEscPosPrint(out: OutputStream, data: CierreTicketData) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        // Inicializar
        out.write(byteArrayOf(0x1B, 0x40))

        // ========== ENCABEZADO ==========
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write("CENTRO PANAMERICANO DE OJOS\n".utf8())
        out.write("CIERRE DE CAJA\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("================================\n".utf8())
        out.write("\n".utf8())

        // ========== INFORMACIÓN DEL OPERADOR ==========
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Izquierda
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write("OPERADOR: ${data.operador}\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("\n".utf8())

        // ========== FECHAS ==========
        out.write("FECHA APERTURA:\n".utf8())
        out.write("  ${dateFormat.format(data.fechaApertura)} ${timeFormat.format(data.fechaApertura)}\n".utf8())
        out.write("\n".utf8())

        out.write("FECHA CIERRE:\n".utf8())
        out.write("  ${dateFormat.format(data.fechaCierre)} ${timeFormat.format(data.fechaCierre)}\n".utf8())
        out.write("\n".utf8())

        out.write("================================\n".utf8())
        out.write("\n".utf8())

        // ========== RESUMEN FINANCIERO ==========
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write("RESUMEN FINANCIERO\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("--------------------------------\n".utf8())

        out.write("Vehiculos Cobrados: ${data.cantidadVehiculos}\n".utf8())
        out.write("\n".utf8())

        // Monto total en grande
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write(byteArrayOf(0x1D, 0x21, 0x11)) // Doble altura y ancho
        out.write("TOTAL COBRADO\n".utf8())
        out.write(byteArrayOf(0x1D, 0x21, 0x22)) // Triple altura y ancho
        out.write("$${String.format("%.2f", data.montoTotal)}\n".utf8())
        out.write(byteArrayOf(0x1D, 0x21, 0x00)) // Reset tamaño
        out.write("\n".utf8())

        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Izquierda

        // ========== VEHÍCULOS DENTRO ==========
        out.write("================================\n".utf8())
        out.write("\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write("VEHICULOS DENTRO DEL PARQUEO\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("--------------------------------\n".utf8())

        if (data.vehiculosDentro == 0) {
            out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
            out.write("*** NO HAY VEHICULOS ***\n".utf8())
            out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Izquierda
        } else {
            out.write("Total: ${data.vehiculosDentro}\n".utf8())
            out.write("\n".utf8())

            // ⭐ CAMBIO: Solo listar placas (más compacto)
            data.vehiculosDentroDetalle.forEachIndexed { index, vehiculo ->
                val numero = index + 1
                out.write("${numero}. ${vehiculo.placa}\n".utf8())
            }
        }

        // ========== FOOTER ==========
        out.write("\n".utf8())
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write("================================\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x01))
        out.write("CIERRE REGISTRADO\n".utf8())
        out.write("CORRECTAMENTE\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("================================\n".utf8())

        out.write("\n\n\n".utf8())

        // Corte
        out.write(byteArrayOf(0x1D, 0x56, 0x01))
    }

    private fun buildTicketText(data: CierreTicketData): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        return buildString {
            appendLine("CEPA")
            appendLine("CIERRE DE CAJA")
            appendLine("================================")
            appendLine()
            appendLine("OPERADOR: ${data.operador}")
            appendLine()
            appendLine("FECHA APERTURA:")
            appendLine(dateFormat.format(data.fechaApertura))
            appendLine()
            appendLine("FECHA CIERRE:")
            appendLine(dateFormat.format(data.fechaCierre))
            appendLine()
            appendLine("================================")
            appendLine()
            appendLine("RESUMEN FINANCIERO")
            appendLine("--------------------------------")
            appendLine("Vehiculos Cobrados: ${data.cantidadVehiculos}")
            appendLine()
            appendLine("TOTAL COBRADO")
            appendLine("$${String.format("%.2f", data.montoTotal)}")
            appendLine()
            appendLine("================================")
            appendLine()
            appendLine("VEHICULOS DENTRO DEL PARQUEO")
            appendLine("--------------------------------")

            if (data.vehiculosDentro == 0) {
                appendLine("*** NO HAY VEHICULOS ***")
            } else {
                appendLine("Total: ${data.vehiculosDentro}")
                appendLine()

                // ⭐ CAMBIO: Solo listar placas
                data.vehiculosDentroDetalle.forEachIndexed { index, vehiculo ->
                    val numero = index + 1
                    appendLine("${numero}. ${vehiculo.placa}")
                }
            }

            appendLine()
            appendLine("================================")
            appendLine("CIERRE REGISTRADO CORRECTAMENTE")
            appendLine("================================")
        }
    }

    private fun showFallback(context: Context, text: String, reason: String) {
        Toast.makeText(context, "$reason\n\n$text", Toast.LENGTH_LONG).show()
    }

    private fun String.utf8(): ByteArray = toByteArray(Charset.forName("UTF-8"))
}