package com.artesanias.tv.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado en memoria de la TV. Arranca con datos de demostración para que las
 * pantallas no se vean vacías si el teléfono todavía no se ha conectado, y
 * se sobreescribe con datos reales en cuanto TvServer recibe el primer sync.
 *
 * `StateFlow` vs `SharedFlow`: un `StateFlow` siempre tiene un valor actual
 * (aquí, el último catálogo/ranking/tabla recibido) y se lo entrega de
 * inmediato a cualquier pantalla que empiece a observarlo, ideal para
 * "estado" que se puede volver a mostrar. Un `SharedFlow` con `replay = 0`
 * (como `compraGrandeEvents` más abajo) no guarda nada: solo notifica a
 * quien esté escuchando EN ESE MOMENTO, correcto para una alerta puntual
 * que no debe "repetirse sola" si la pantalla se vuelve a abrir después.
 */
object TvDataStore {

    private val _productos = MutableStateFlow(datosDemoProductos())
    val productos: StateFlow<List<TvProducto>> = _productos.asStateFlow()

    private val _masVendidos = MutableStateFlow(datosDemoMasVendidos())
    val masVendidos: StateFlow<List<TvMasVendido>> = _masVendidos.asStateFlow()

    private val _comprasSemana = MutableStateFlow(datosDemoComprasSemana())
    val comprasSemana: StateFlow<List<TvCompraSemana>> = _comprasSemana.asStateFlow()

    /** replay = 0: es un evento puntual (notificación), no un estado a restaurar. */
    val compraGrandeEvents = MutableSharedFlow<TvCompraGrandeEvent>(replay = 0, extraBufferCapacity = 4)

    private val _conectado = MutableStateFlow(false)
    val conectado: StateFlow<Boolean> = _conectado.asStateFlow()

    fun actualizarProductos(items: List<TvProducto>) {
        _productos.value = items
        _conectado.value = true
    }

    fun actualizarMasVendidos(items: List<TvMasVendido>) {
        _masVendidos.value = items
        _conectado.value = true
    }

    fun actualizarComprasSemana(items: List<TvCompraSemana>) {
        _comprasSemana.value = items
        _conectado.value = true
    }

    suspend fun emitirCompraGrande(evento: TvCompraGrandeEvent) {
        _conectado.value = true
        compraGrandeEvents.emit(evento)
    }

    private fun datosDemoProductos() = listOf(
        TvProducto("Plato Talavera Grande", 350.0, 12, "Talavera"),
        TvProducto("Vasija Barro Negro", 480.0, 4, "Barro Negro"),
        TvProducto("Jarro Talavera", 180.0, 20, "Talavera"),
        TvProducto("Cazuela de Barro", 220.0, 8, "Utilitaria"),
        TvProducto("Jarrón Mayólica", 650.0, 3, "Mayólica"),
        TvProducto("Tazón Barro Rojo", 95.0, 2, "Utilitaria"),
        TvProducto("Florero Talavera Mini", 120.0, 15, "Talavera"),
        TvProducto("Incensario Barro Negro", 380.0, 5, "Barro Negro")
    )

    private fun datosDemoMasVendidos() = listOf(
        TvMasVendido("Jarro Talavera", 14),
        TvMasVendido("Florero Talavera Mini", 11),
        TvMasVendido("Cazuela de Barro", 8),
        TvMasVendido("Tazón Barro Rojo", 6),
        TvMasVendido("Vasija Barro Negro", 3)
    )

    private fun datosDemoComprasSemana() = listOf(
        TvCompraSemana("Lun", "María González", 480.0),
        TvCompraSemana("Mar", "Cliente Tienda", 180.0),
        TvCompraSemana("Mié", "Cliente Tienda", 650.0),
        TvCompraSemana("Jue", "María González", 220.0),
        TvCompraSemana("Vie", "Cliente Tienda", 1150.0),
        TvCompraSemana("Sáb", "Cliente Tienda", 95.0)
    )
}
