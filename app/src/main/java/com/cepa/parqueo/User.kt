package com.cepa.parqueo

data class User(
    val username: String,
    val password: String,
    val type: UserType
)

enum class UserType {
    ADMINISTRADOR,
    OPERADOR,
    CAJA
}