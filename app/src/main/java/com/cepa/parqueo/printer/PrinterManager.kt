package com.cepa.parqueo.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import com.cepa.parqueo.ReceiptData
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

            Toast.makeText(context, "Ticket impreso", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            showFallback(context, fallbackText, "Error: ${e.message}")
        }
    }

    private fun sendEscPosPrint(out: OutputStream, data: ReceiptData) {
        out.write(byteArrayOf(0x1B, 0x40)) // Inicializar

        // Encabezado
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write(byteArrayOf(0x1B, 0x45, 0x01)) // Negrita on
        out.write("CENTRO PANAMERICANO DE OJOS\n".utf8())
        out.write("TICKET DE INGRESO\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00)) // Negrita off
        out.write("============================\n".utf8())

        // QR Code con la placa
        printQRCode(out, data.plate)

        // Datos del vehiculo
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Izquierda

        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        out.write(byteArrayOf(0x1B, 0x45, 0x01))
        out.write("TIPO: ${data.vehicleType}\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("\n".utf8())

        out.write(byteArrayOf(0x1B, 0x45, 0x01))
        out.write("PLACA: ${data.plate}\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))

        out.write("\n".utf8())
        out.write("FECHA: ${dateFormatter.format(data.entryTime)}\n".utf8())
        out.write("HORA:  ${timeFormatter.format(data.entryTime)}\n".utf8())
        out.write("\n".utf8())
        out.write("ID: ${data.uniqueId}\n".utf8())
        out.write("\n".utf8())

        // Footer
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
        out.write("============================\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x01))
        out.write("VALIDAR ESTE TICKET\n".utf8())
        out.write("PARA SU SALIDA\n".utf8())
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("============================\n".utf8())
        out.write("\n\n".utf8())

        out.write(byteArrayOf(0x1D, 0x56, 0x01)) // Corte
    }

    /**
     * Genera el QR con ZXing y lo envia como imagen raster (GS v 0).
     * Compatible con impresoras que no soportan comandos QR nativos.
     *
     * Para ajustar el tamano del QR, modifica targetPixels:
     *   200 = pequeno | 300 = mediano | 400 = grande
     *
     * Requiere en build.gradle:
     *   implementation 'com.google.zxing:core:3.5.2'
     */
    private fun printQRCode(out: OutputStream, data: String) {
        try {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                com.google.zxing.EncodeHintType.MARGIN to 1
            )
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter()
                .encode(data, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints)

            val targetPixels = 200
            val scale = maxOf(1, targetPixels / bitMatrix.width)
            val imgWidth  = bitMatrix.width  * scale
            val imgHeight = bitMatrix.height * scale
            val bytesPerRow = (imgWidth + 7) / 8

            val imgData = ByteArray(bytesPerRow * imgHeight)
            for (y in 0 until imgHeight) {
                for (x in 0 until imgWidth) {
                    if (bitMatrix.get(x / scale, y / scale)) {
                        val byteIndex = y * bytesPerRow + (x / 8)
                        imgData[byteIndex] = (imgData[byteIndex].toInt() or (1 shl (7 - x % 8))).toByte()
                    }
                }
            }

            out.write(byteArrayOf(0x1B, 0x61, 0x01)) // Centrar
            out.write(byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (bytesPerRow and 0xFF).toByte(), (bytesPerRow shr 8).toByte(),
                (imgHeight and 0xFF).toByte(),   (imgHeight shr 8).toByte()
            ))
            out.write(imgData)
            out.write("\n".utf8())
            out.write(byteArrayOf(0x1B, 0x61, 0x00)) // Izquierda

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildReceiptText(data: ReceiptData): String {
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("CEPA - TICKET DE INGRESO")
            appendLine("========================")
            appendLine("[QR: ${data.plate}]")
            appendLine()
            appendLine("TIPO: ${data.vehicleType}")
            appendLine("PLACA: ${data.plate}")
            appendLine(dateFormatter.format(data.entryTime))
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