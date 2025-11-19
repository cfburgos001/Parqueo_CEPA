package com.parking.system

import java.util.*

/**
 * Clase de datos para el recibo de entrada de vehículo
 */
data class ReceiptData(
    val uniqueId: String,      // ID único del ticket (PK-timestamp)
    val plate: String,          // Placa del vehículo
    val entryTime: Date         // Fecha y hora de entrada
)