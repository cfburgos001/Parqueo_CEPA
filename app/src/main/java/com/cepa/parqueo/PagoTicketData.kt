package com.cepa.parqueo

import java.util.Date

/**
 * Datos para imprimir el ticket de pago
 */
data class PagoTicketData(
    val placa: String,
    val fechaEntrada: Date,
    val fechaPago: Date,
    val tiempoTotalMinutos: Int,
    val tiempoCobrableMinutos: Int,
    val monto: Double,
    val metodoPago: String,  // "Efectivo" o "Tarjeta"
    val operador: String,
    val idDispositivo: String,
    val estadoCobro: String  // "GRACIA_ENTRADA", "DEBE_PAGAR", etc.
)