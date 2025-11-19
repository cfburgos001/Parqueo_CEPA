package com.parking.system.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import com.parking.system.ReceiptData
import java.io.OutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

@SuppressLint("MissingPermission")
object PrinterManager {

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

    fun printReceipt(context: Context, data: ReceiptData) {
        val fallbackText = buildReceiptText(data)

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

            Toast.makeText(context, "✓ Ticket impreso", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            showFallback(context, fallbackText, "Error: ${e.message}")
        }
    }

    /**
     * Imprime ticket con CODE128 de la PLACA (más fácil de escanear)
     */
    private fun sendEscPosPrint(out: OutputStream, data: ReceiptData) {
        // Inicializar
        out.write(byteArrayOf(0x1B, 0x40))

        // Encabezado
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write("CENTRO PANAMERICANO DE OJOS\n".utf8())
        out.write("TICKET DE INGRESO\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("============================\n".utf8())
        out.write("\n".utf8())

        // CODE128 - ESCANEABLE (imprime solo la PLACA para mejor lectura)
        printBarcode128(out, data.plate)
        out.write("\n".utf8())

        // Información principal
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Izquierda

        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita
        out.write("PLACA: ${data.plate}\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))

        out.write("\n".utf8())
        out.write("FECHA: ${dateFormatter.format(data.entryTime)}\n".utf8())
        out.write("HORA:  ${timeFormatter.format(data.entryTime)}\n".utf8())
        out.write("\n".utf8())
        out.write("ID: ${data.uniqueId}\n".utf8())

        // Footer
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write("============================\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x01))
        out.write("VALIDAR ESTE TICKET\n".utf8())
        out.write("PARA SU SALIDA\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("============================\n".utf8())


        out.write("\n\n\n".utf8())

        // Corte
        out.write(byteArrayOf(0x1D, 0x56, 0x01))
    }

    /**
     * Imprime CODE128 usando comandos ESC/POS
     */
    private fun printBarcode128(out: OutputStream, data: String) {
        try {
            // Centrar código de barras
            out.write(byteArrayOf(0x1B, 0x61, 0x01))

            // GS H - Posición del texto HRI (abajo)
            out.write(byteArrayOf(0x1D, 0x48, 0x02))

            // GS h - Altura del código de barras (100 puntos)
            out.write(byteArrayOf(0x1D, 0x68, 0x64))

            // GS w - Ancho del código de barras (3 = mediano)
            out.write(byteArrayOf(0x1D, 0x77, 0x03))

            // GS k - Imprimir código de barras CODE128
            // Formato: GS k 73 n [datos]
            out.write(0x1D) // GS
            out.write(0x6B) // k
            out.write(0x49) // CODE128 (73 = 0x49)
            out.write(data.length) // Longitud
            out.write(data.utf8()) // Datos

            out.write("\n".utf8())

            // Volver a alinear izquierda
            out.write(byteArrayOf(0x1B, 0x61, 0x00))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildReceiptText(data: ReceiptData): String {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("CENTRO PANAMERICANO DE OJOS")
            appendLine("TICKET DE INGRESO")
            appendLine("========================")
            appendLine()
            appendLine("[CODE128: ${data.plate}]")
            appendLine()
            appendLine("PLACA: ${data.plate}")
            appendLine()
            appendLine(dateFormatter.format(data.entryTime))
            appendLine()
            appendLine("ID: ${data.uniqueId}")
            appendLine("========================")
            appendLine("VALIDAR ESTE TICKET")
            appendLine("========================")
        }
    }

    private fun showFallback(context: Context, text: String, reason: String) {
        Toast.makeText(context, "$reason\n\n$text", Toast.LENGTH_LONG).show()
    }

    fun isBluetoothEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    fun isPrinterPaired(context: Context): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            adapter.bondedDevices.any { it.address == PRINTER_MAC }
        } catch (e: Exception) {
            false
        }
    }

    private fun String.utf8(): ByteArray = toByteArray(Charset.forName("UTF-8"))
}