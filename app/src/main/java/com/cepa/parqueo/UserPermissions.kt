package com.cepa.parqueo

/**
 * Funciones de extensión para verificar permisos de usuario
 */
fun UserType.canAccessMaintenance(): Boolean {
    return this == UserType.ADMINISTRADOR
}

fun UserType.canAccessEntry(): Boolean {
    // CAJA no puede registrar entradas
    return this != UserType.CAJA
}

fun UserType.canAccessExit(): Boolean {
    return true // Todos pueden registrar salidas
}

/**
 * CAJA solo puede cobrar, no puede abrir la pluma
 */
fun UserType.canOpenGate(): Boolean {
    return this != UserType.CAJA
}

/**
 * Verifica si el usuario es tipo CAJA
 */
fun UserType.isCaja(): Boolean {
    return this == UserType.CAJA
}