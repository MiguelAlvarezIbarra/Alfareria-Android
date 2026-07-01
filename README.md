# 🏺 ArtesaniasApp — Tienda de Alfarería con Wear OS

Aplicación Android completa para tienda de artesanías y alfarería, con módulo para Wear OS que notifica al administrador sobre stock bajo y compras grandes.

---
# Información del equipo

**Integrantes:**
- Miguel Ángel Álvarez Ibarra
- Claudio Ángel Huerta Ducoing
- Pedro Uriel Perez Monzón

**Grupo:** GIDS6092

---

## 🎯 Objetivo

Desarrollar una aplicación Android para la gestión y venta de artesanías y productos de alfarería que permita a los clientes explorar el catálogo, realizar compras y consultar sus pedidos, mientras que los administradores puedan gestionar productos, usuarios e inventario. Además, integrar un módulo para Wear OS que facilite el monitoreo del negocio mediante notificaciones de stock bajo, compras importantes y acciones remotas desde un reloj inteligente.

---

## 📋 Características

### 📱 Módulo Phone (app)
- **Autenticación**: Login y registro con roles (Admin / Cliente)
- **Tienda**: Catálogo con búsqueda en tiempo real, carrito de compras
- **Mis Órdenes**: Historial de compras del cliente
- **Cámara dual**: Foto de producto (CameraX) + escáner QR para parear reloj (ZXing)
- **Panel de Admin**:
  - Gestión de productos (crear, editar, stock)
  - Gestión de usuarios (crear, activar/desactivar)
  - Dashboard con estadísticas en tiempo real
- **Comunicación Wear OS**: Notificaciones automáticas via Wearable Data Layer

### ⌚ Módulo Wear OS (wear)
- **Alertas de stock bajo** (≤ 5 unidades): vibración corta + notificación
- **Alertas de compra grande** (> $500 MXN): vibración larga + notificación informativa
- **Confirmación de compra muy grande** (> $1000 MXN): requiere confirmación desde el reloj
- **Respuesta bidireccional**: el admin puede agregar stock y confirmar órdenes directamente desde el reloj

---

## 🛠️ Requisitos

| Herramienta | Versión mínima |
|---|---|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17+ |
| Android SDK | API 26+ (Android 8.0) |
| Wear OS en emulador | API 28+ |
| Kotlin | 1.9.22 |
| Gradle | 8.2.2 |

---

## 🚀 Instalación

### 1. Abrir en Android Studio
```
File → Open → Seleccionar carpeta ArtesaniasApp/
```

### 2. Sincronizar Gradle
```
File → Sync Project with Gradle Files
```
*(o clic en "Sync Now" cuando aparezca el banner)*

### 3. Instalar módulo phone
```
Run → Run 'app'    # En dispositivo físico o emulador API 26+
```

### 4. Instalar módulo Wear OS
```
Run → Run 'wear'   # En reloj emparejado o emulador Wear OS
```

> **Nota**: Para emulador Wear OS, primero empareja el AVD del reloj con el AVD del teléfono en el Wear OS Emulator Pairing Assistant.

---

## 👤 Credenciales por defecto

| Rol | Email | Contraseña |
|---|---|---|
| Admin | admin@artesanias.mx | Admin123 |
| Cliente | cliente@artesanias.mx | Cliente123 |

---

## 🏗️ Arquitectura

```
ArtesaniasApp/
├── app/                          # Módulo teléfono
│   └── src/main/
│       ├── java/com/artesanias/app/
│       │   ├── data/
│       │   │   ├── local/        # Room (DAOs, Database)
│       │   │   ├── model/        # Entidades y data classes
│       │   │   ├── remote/       # WearableDataListenerService
│       │   │   └── repository/   # Repositorios con lógica de negocio
│       │   ├── di/               # Módulos Hilt
│       │   ├── ui/
│       │   │   ├── admin/        # Fragments de administración
│       │   │   ├── auth/         # Login y Registro
│       │   │   ├── camera/       # CameraX + QR scan
│       │   │   ├── shared/       # Adapters compartidos
│       │   │   └── store/        # Tienda, carrito, órdenes
│       │   ├── util/             # SessionManager, HashUtil, QRUtil
│       │   ├── ArtesaniasApplication.kt
│       │   ├── MainActivity.kt
│       │   └── ViewModels.kt
│       └── res/
│           ├── layout/           # 15 layouts (fragments + items + dialogs)
│           ├── navigation/       # nav_graph.xml
│           ├── menu/             # menu_admin.xml, menu_cliente.xml
│           ├── drawable/         # Iconos vectoriales
│           └── values/           # strings, themes, colors
│
└── wear/                         # Módulo reloj
    └── src/main/
        ├── java/com/artesanias/wear/
        │   ├── data/
        │   │   └── PhoneMessageListenerService.kt
        │   ├── presentation/
        │   │   └── Activities.kt  # Main, StockAlert, CompraAlert
        │   └── WearApplication.kt
        └── res/
            ├── layout/            # 3 layouts de actividades
            └── values/            # strings, themes
```

### Patrón: MVVM + Clean Architecture

```
UI (Fragment) ←→ ViewModel ←→ Repository ←→ DAO / WearDataSender
```

**Stack tecnológico:**
- **DI**: Hilt 2.50
- **Base de datos**: Room 2.6.1 con Flow/LiveData
- **Coroutines**: kotlinx.coroutines 1.7.3
- **Navegación**: Navigation Component 2.7.6
- **Cámara**: CameraX 1.3.1
- **QR**: ZXing Core 3.5.2 + zxing-android-embedded 4.3.0
- **Imágenes**: Glide 4.16.0
- **Wear OS**: Wearable Data Layer (MessageClient + DataClient)

---

## 🔗 Comunicación Teléfono ↔ Reloj

### Teléfono → Reloj (paths de mensajes)

| Path | Datos | Acción en reloj |
|---|---|---|
| `/alerta/stock` | `"productoId:nombre:stock"` | Vibra + abre StockAlertActivity |
| `/alerta/compra-grande` | `"mensaje de compra"` | Vibra + abre CompraAlertActivity |
| `/stock/actualizado` | `"confirmación"` | Notificación simple |
| `/orden/confirmada` | `"confirmación"` | Notificación simple |

### Reloj → Teléfono (respuestas)

| Path | Datos | Acción en teléfono |
|---|---|---|
| `/stock/agregar` | `"productoId:cantidad"` | Agrega stock al producto en BD |
| `/orden/confirmar` | `"ordenId"` | Confirma la orden en BD |
| `/ping` | `"hola"` | Responde con `/ping/pong` |

---

## 📷 Cómo parear el reloj

1. En la app del teléfono, inicia sesión como **Admin**
2. En el menú inferior, ve a **Cámara**
3. Selecciona **Modo QR** (botón inferior)
4. En el reloj, abre la app → toca **"Ver QR de conexión"**
5. Escanea el QR mostrado en el reloj con la cámara del teléfono
6. El nodo ID queda registrado para comunicación directa

---

## 🎨 Paleta de colores

| Color | Hex | Uso |
|---|---|---|
| Marrón terracota | `#5D4037` | Color primario / botones |
| Marrón oscuro | `#3E2723` | Color primario dark |
| Crema | `#F5E6D3` | Textos principales |
| Naranja alerta | `#FF7043` | Alertas de stock |
| Verde compra | `#66BB6A` | Confirmaciones de venta |
| Ámbar | `#FFB300` | Avisos de confirmación requerida |

---

## 🗄️ Base de datos

### Datos pre-cargados

**Categorías:** Talavera Poblana, Barro Negro, Mayólica, Alfarería Utilitaria

**Productos de ejemplo:**
- Jarrón de Talavera Grande ($850 MXN, stock: 3 ⚠️)
- Platos de Talavera Set x6 ($650 MXN, stock: 8)
- Olla de Barro Negro ($420 MXN, stock: 12)
- Cazuela de Barro ($280 MXN, stock: 2 ⚠️)
- Florero de Mayólica ($380 MXN, stock: 7)
- Jarra Decorativa ($520 MXN, stock: 5 ⚠️)
- Tazón de Cerámica ($180 MXN, stock: 20)
- Maceta Artesanal ($240 MXN, stock: 15)

*Los productos marcados con ⚠️ tienen stock bajo y generarán alertas al reloj.*

### Umbrales de negocio

| Condición | Umbral | Acción |
|---|---|---|
| Stock bajo | ≤ 5 unidades | Alerta al reloj (vibración corta) |
| Compra grande | > $500 MXN | Notificación al reloj |
| Compra muy grande | > $1,000 MXN | Alerta urgente + confirmación requerida |

---

## 🔐 Seguridad

- Contraseñas hasheadas con **SHA-256** (demo)
- Control de acceso por rol en Navigation (destino inicial dinámico)
- Validación de email único al registrar
- Usuarios desactivados no pueden iniciar sesión

> **Producción**: Se recomienda migrar a BCrypt con salt para el hash de contraseñas.

---

## 📝 Notas de desarrollo

- El módulo Wear OS usa `Activity` (no `ComponentActivity`) para máxima compatibilidad con Wear OS 2.x y 3.x
- `lifecycleScope` está disponible desde `androidx.activity:activity-ktx`
- La comunicación Wear usa `MessageClient` (no DataClient) para comandos y respuestas instantáneas
- Los layouts Wear usan `BoxInsetLayout` para adaptarse a pantallas redondas y cuadradas

---

## 🐛 Problemas conocidos / TODOs

- [ ] Migrar hash de contraseñas a BCrypt con salt
- [ ] Implementar Firebase Cloud Messaging como canal alternativo al Wear Data Layer
- [ ] Agregar tests unitarios para los Repositorios
- [ ] Implementar paginación (Paging 3) para el catálogo grande
- [ ] Agregar exportación de reportes de ventas en CSV
