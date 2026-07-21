package com.cepa.parqueo

import java.util.Date

/**
 * Datos para imprimir un Ticket Extraviado (normal o pesado).
 */
data class TicketExtraviadoData(
    val codigo: String,
    val tipo: String,   // "TICKET EXTRAVIADO" o "TICKET EXTRAVIADO PESADO"
    val monto: Double,
    val fecha: Date = Date()
)