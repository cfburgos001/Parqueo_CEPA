package com.cepa.parqueo

import java.util.Date

/**
 * Clase de datos para el recibo de entrada de vehículo
 * VERSIÓN 2: Con tipo de vehículo
 */
data class ReceiptData(
    val uniqueId: String,          // ID único del ticket (PK-timestamp)
    val plate: String,              // Placa del vehículo
    val entryTime: Date,            // Fecha y hora de entrada
    val vehicleType: String = "Vehículo"  // ⭐ NUEVO: Tipo de vehículo
)