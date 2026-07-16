package com.cepa.parqueo.database

/**
 * Caché en memoria de los datos de IOT_ControlDeSitio (nombre comercial,
 * dirección, teléfono, etc.). Se llena UNA sola vez al iniciar sesión
 * (ver LoginActivity) y desde ahí la usan los 4 archivos de impresión
 * para el encabezado del ticket — así cada impresión no vuelve a
 * consultar la BD, solo lee un valor que ya está en memoria.
 *
 * Se limpia al cerrar sesión (ver HomeActivity.logout()) para que la
 * próxima sesión siempre recargue el dato correcto, por si el mismo
 * dispositivo se llega a usar en más de un sitio.
 */
object SiteConfigCache {

    @Volatile
    private var config: ConfigSitio? = null

    fun set(nuevaConfig: ConfigSitio) {
        config = nuevaConfig
    }

    fun clear() {
        config = null
    }

    /** Nombre a mostrar en el encabezado de los tickets. Nunca vacío. */
    fun nombreComercial(): String =
        config?.nombreComercial?.takeIf { it.isNotBlank() } ?: "PARQUEO"

    fun direccion(): String? = config?.direccion
    fun telefono(): String? = config?.telefono
    fun actual(): ConfigSitio? = config
}