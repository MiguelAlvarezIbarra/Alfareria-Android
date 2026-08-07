package com.artesanias.tv.data

data class TvProducto(
    val nombre: String,
    val precio: Double,
    val stock: Int,
    val categoria: String = ""
)

data class TvMasVendido(
    val nombre: String,
    val cantidad: Int
)

data class TvCompraSemana(
    val fecha: String,
    val cliente: String,
    val total: Double
)

data class TvCompraGrandeEvent(
    val producto: String,
    val monto: Double
)
