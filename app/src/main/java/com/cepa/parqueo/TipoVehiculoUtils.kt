package com.cepa.parqueo

/**
 * Traduce el strRateKey de la BD a un texto legible para tickets, facturas
 * e interfaces.
 *
 * Centralizado a propósito: antes cada Activity tenía su propio mapeo local
 * (ej. IngresoVehiculoActivity todavía comparaba contra "C" en vez de "P"
 * desde que se renombraron las tarifas de A/M/C a L/M/P), lo que hacía que
 * los camiones se imprimieran como "Vehiculo" genérico. Usar esta única
 * función evita que se vuelva a desincronizar.
 */
fun tipoVehiculoTexto(strRateKey: String?): String = when (strRateKey) {
    "L" -> "Vehiculo Liviano"
    "M" -> "Moto"
    "P" -> "Camion / Pesado"
    "T" -> "Tarjeta de Acceso"
    "Z" -> "Cortesia"
    "E" -> "Ticket Extraviado"
    "EP" -> "Ticket Extraviado Pesado"
    else -> "Vehiculo"
}