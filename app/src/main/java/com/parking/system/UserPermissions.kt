package com.parking.system

/**
 * Funciones de extensión para verificar permisos de usuario
 */
fun UserType.canAccessMaintenance(): Boolean {
    return this == UserType.ADMINISTRADOR
}

fun UserType.canAccessEntry(): Boolean {
    return true // Todos pueden registrar entradas
}

fun UserType.canAccessExit(): Boolean {
    return true // Todos pueden registrar salidas
}

