package com.artesanias.app.di

import android.content.Context
import com.artesanias.app.data.local.*
import com.artesanias.app.data.remote.TvDataSender
import com.artesanias.app.data.remote.WearDataSender
import com.artesanias.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de inyección de dependencias de Hilt: le enseña al framework
 * CÓMO construir los objetos que las clases piden en su constructor (los
 * DAOs y repositorios), ya que esas clases no tienen un constructor
 * `@Inject` "automático" tan simple (dependen de la base de datos, que a
 * su vez necesita el Context de la app).
 *
 * - `@Module`: marca esta clase/objeto como una fuente de instrucciones
 *   de inyección para Hilt.
 * - `@InstallIn(SingletonComponent::class)`: dice que estas instrucciones
 *   viven mientras viva la aplicación completa (no una Activity o
 *   Fragment en particular), coherente con que todo aquí se provee como
 *   `@Singleton` (una sola instancia compartida por toda la app).
 * - `@Provides`: marca cada función como "así se construye este tipo".
 *   Hilt lee los parámetros de la función para saber qué otras
 *   dependencias necesita resolver primero (p.ej. para dar un
 *   `ProductoRepository` primero resuelve su `ProductoDao`, que a su vez
 *   necesita la `ArtesaniasDatabase`).
 * - `@ApplicationContext`: le pide a Hilt específicamente el Context de la
 *   aplicación (no el de una Activity), que es el que hay que usar aquí
 *   porque estos objetos sobreviven a cualquier pantalla.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ArtesaniasDatabase =
        ArtesaniasDatabase.getInstance(ctx)

    // Cada DAO se obtiene directamente de la base de datos ya construida
    // (Room genera la implementación real de estos métodos `abstract`).
    @Provides @Singleton
    fun provideUsuarioDao(db: ArtesaniasDatabase) = db.usuarioDao()

    @Provides @Singleton
    fun provideCategoriaDao(db: ArtesaniasDatabase) = db.categoriaDao()

    @Provides @Singleton
    fun provideProductoDao(db: ArtesaniasDatabase) = db.productoDao()

    @Provides @Singleton
    fun provideOrdenDao(db: ArtesaniasDatabase) = db.ordenDao()

    @Provides @Singleton
    fun provideDetalleOrdenDao(db: ArtesaniasDatabase) = db.detalleOrdenDao()

    @Provides @Singleton
    fun provideNotificacionDao(db: ArtesaniasDatabase) = db.notificacionDao()

    @Provides @Singleton
    fun provideAuthRepository(usuarioDao: UsuarioDao) = AuthRepository(usuarioDao)

    @Provides @Singleton
    fun provideWearSender(@ApplicationContext ctx: Context) = WearDataSender(ctx)

    // Los repositorios que dependen de otros repositorios (ProductoRepository
    // dentro de OrdenRepository) se resuelven en cadena: Hilt ve que
    // provideOrdenRepository pide un ProductoRepository, busca cómo
    // proveerlo (la función de abajo) y lo construye primero.
    @Provides @Singleton
    fun provideProductoRepository(
        productoDao: ProductoDao,
        notificacionDao: NotificacionDao,
        wearSender: WearDataSender,
        tvSender: TvDataSender
    ) = ProductoRepository(productoDao, notificacionDao, wearSender, tvSender)

    @Provides @Singleton
    fun provideOrdenRepository(
        ordenDao: OrdenDao,
        detalleOrdenDao: DetalleOrdenDao,
        productoRepo: ProductoRepository,
        notificacionDao: NotificacionDao,
        wearSender: WearDataSender,
        tvSender: TvDataSender
    ) = OrdenRepository(ordenDao, detalleOrdenDao, productoRepo, notificacionDao, wearSender, tvSender)

    @Provides @Singleton
    fun provideUsuarioRepository(usuarioDao: UsuarioDao) = UsuarioRepository(usuarioDao)
}
