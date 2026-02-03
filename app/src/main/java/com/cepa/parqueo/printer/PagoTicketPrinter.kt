package com.cepa.parqueo.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import com.cepa.parqueo.PagoTicketData
import java.io.OutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Printer Manager para Ticket de Pago de Estacionamiento
 */
@SuppressLint("MissingPermission")
object PagoTicketPrinter {

    private const val PRINTER_MAC = "66:11:22:33:44:55"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun String.utf8(): ByteArray = this.toByteArray(Charset.forName("UTF-8"))

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

    fun printPagoTicket(context: Context, data: PagoTicketData) {
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

            Toast.makeText(context, "Ticket de pago impreso", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            showFallback(context, fallbackText, "Error: ${e.message}")
        }
    }

    private fun sendEscPosPrint(out: OutputStream, data: PagoTicketData) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        // Inicializar impresora
        out.write(byteArrayOf(0x1B, 0x40))

        // ========== ENCABEZADO ==========
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write("CENTRO PANAMERICANO DE OJOS\n".utf8())
        out.write("COMPROBANTE DE PAGO\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00)) // Quitar negrita
        out.write("===============================\n".utf8())

        // ========== PLACA ==========
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write(byteArrayOf(0x1D, 0x21, 0x11)) // Doble tamaño
        out.write("${data.placa}\n".utf8())
        out.write(byteArrayOf(0x1D, 0x21, 0x00)) // Reset tamaño
        out.write(byteArrayOf(0x1B, 0x45, 0x00)) // Quitar negrita
        out.write("-------------------------------\n".utf8())

        // ========== TIEMPOS ==========
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Alinear izquierda
        out.write("ENTRADA: ${dateFormat.format(data.fechaEntrada)} ${timeFormat.format(data.fechaEntrada)}\n".utf8())
        out.write("PAGO:    ${dateFormat.format(data.fechaPago)} ${timeFormat.format(data.fechaPago)}\n".utf8())

        // Tiempo de estancia
        val horas = data.tiempoTotalMinutos / 60
        val minutos = data.tiempoTotalMinutos % 60
        val tiempoTexto = if (horas > 0) "$horas h $minutos min" else "$minutos min"
        out.write("TIEMPO:  $tiempoTexto\n".utf8())
        out.write("-------------------------------\n".utf8())

        // ========== MONTO ==========
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar

        if (data.monto > 0) {
            out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
            out.write("TOTAL\n".utf8())
            out.write(byteArrayOf(0x1D, 0x21, 0x11)) // Doble tamaño
            out.write("$${String.format("%.2f", data.monto)}\n".utf8())
            out.write(byteArrayOf(0x1D, 0x21, 0x00)) // Reset tamaño
            out.write(byteArrayOf(0x1B, 0x45, 0x00)) // Quitar negrita
        } else {
            out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
            out.write("SIN CARGO\n".utf8())
            out.write(byteArrayOf(0x1B, 0x45, 0x00)) // Quitar negrita
            out.write("(Periodo de gracia)\n".utf8())
        }

        // ========== MÉTODO DE PAGO ==========
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Alinear izquierda
        out.write("-------------------------------\n".utf8())
        out.write("METODO:     ${data.metodoPago}\n".utf8())
        out.write("OPERADOR:   ${data.operador}\n".utf8())
        out.write("DISPOSITIVO:${data.idDispositivo}\n".utf8())

        // ========== FOOTER ==========
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write("===============================\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write("PAGO REGISTRADO\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00)) // Quitar negrita
        out.write("Tiene 15 min para salir\n".utf8())
        out.write("===============================\n".utf8())

        // Espacio final y corte
        out.write("\n\n".utf8())
        out.write(byteArrayOf(0x1D, 0x56, 0x01)) // Corte parcial
    }

    /**
     * Texto de fallback si no se puede imprimir
     */
    private fun buildTicketText(data: PagoTicketData): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val horas = data.tiempoTotalMinutos / 60
        val minutos = data.tiempoTotalMinutos % 60
        val tiempoTexto = if (horas > 0) "$horas h $minutos min" else "$minutos min"

        return buildString {
            appendLine("CEPA - COMPROBANTE DE PAGO")
            appendLine("================================")
            appendLine("PLACA: ${data.placa}")
            appendLine("--------------------------------")
            appendLine("ENTRADA: ${dateFormat.format(data.fechaEntrada)}")
            appendLine("PAGO:    ${dateFormat.format(data.fechaPago)}")
            appendLine("TIEMPO:  $tiempoTexto")
            appendLine("--------------------------------")
            if (data.monto > 0) {
                appendLine("TOTAL: $${String.format("%.2f", data.monto)}")
            } else {
                appendLine("SIN CARGO (Periodo de gracia)")
            }
            appendLine("METODO: ${data.metodoPago}")
            appendLine("OPERADOR: ${data.operador}")
            appendLine("================================")
            appendLine("PAGO REGISTRADO")
            appendLine("Tiene 15 min para salir")
        }
    }

    private fun showFallback(context: Context, text: String, reason: String) {
        Toast.makeText(context, "$reason\n\n$text", Toast.LENGTH_LONG).show()
    }
}