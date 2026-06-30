package com.artesanias.app.di

import android.content.Context
import com.artesanias.app.data.local.*
import com.artesanias.app.data.remote.WearDataSender
import com.artesanias.app.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ArtesaniasDatabase =
        ArtesaniasDatabase.getInstance(ctx)

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

    @Provides @Singleton
    fun provideProductoRepository(
        productoDao: ProductoDao,
        notificacionDao: NotificacionDao,
        wearSender: WearDataSender
    ) = ProductoRepository(productoDao, notificacionDao, wearSender)

    @Provides @Singleton
    fun provideOrdenRepository(
        ordenDao: OrdenDao,
        detalleOrdenDao: DetalleOrdenDao,
        productoRepo: ProductoRepository,
        notificacionDao: NotificacionDao,
        wearSender: WearDataSender
    ) = OrdenRepository(ordenDao, detalleOrdenDao, productoRepo, notificacionDao, wearSender)

    @Provides @Singleton
    fun provideUsuarioRepository(usuarioDao: UsuarioDao) = UsuarioRepository(usuarioDao)
}
