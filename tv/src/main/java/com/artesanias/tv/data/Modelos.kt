package com.artesanias.tv.data

// Modelos de datos del módulo TV: son simples `data class`, sin `@Entity`
// ni Room, porque la app de TV no tiene base de datos propia — solo
// mantiene en memoria (ver TvDataStore) lo último que le mandó el
// teléfono por el socket, para dibujarlo en pantalla.

/** Un producto del catálogo, tal como lo manda el teléfono (ver TvDataSender.enviarCatalogo). */
data class TvProducto(
    val nombre: String,
    val precio: Double,
    val stock: Int,
    val categoria: String = ""
)

/** Una fila del ranking de más vendidos (Pantalla 2). */
data class TvMasVendido(
    val nombre: String,
    val cantidad: Int
)

/** Una compra de la semana, para la tabla de la Pantalla 2. */
data class TvCompraSemana(
    val fecha: String,
    val cliente: String,
    val total: Double
)

/** Datos de la alerta de "compra grande" que dispara el overlay sobre cualquier pantalla. */
data class TvCompraGrandeEvent(
    val producto: String,
    val monto: Double
)
