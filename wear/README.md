# ⌚ Módulo Wear OS (`wear/`)

App para Wear OS que convierte el reloj del administrador en un canal de alertas y respuestas rápidas para el negocio: avisa de stock bajo y compras grandes, y permite resolverlas sin sacar el teléfono.

⬅️ [Volver al README principal](../README.md)

---

## 🎯 Qué hace

Es el otro extremo de la comunicación bidireccional descrita en el [README principal](../README.md#-comunicación-teléfono--reloj): recibe mensajes del teléfono vía **Wearable Data Layer** y puede responder con comandos que el teléfono ejecuta sobre su base de datos.

### Pantallas (Activities, sin Navigation Component)

| Activity | Se abre cuando... | Acción |
|---|---|---|
| `MainActivity` | Al abrir la app manualmente | Botón de ping de prueba, acceso a "Ajustar Inventario", atajo a QR |
| `StockListActivity` | Desde `MainActivity` → "Ajustar Inventario" | Lista todos los productos con stock bajo reportados por el teléfono |
| `StockAlertActivity` | Automático al recibir `/alerta/stock`, o al tocar un producto en la lista | Pide agregar unidades a un producto específico |
| `CompraAlertActivity` | Automático al recibir `/alerta/compra-grande` | Muestra el detalle de la compra; si es >$1000 MXN pide confirmación explícita |
| `StockResultadoActivity` | Automático tras confirmar una compra grande | Informa si el stock quedó suficiente o si algún producto quedó bajo |

---

## 🛠️ Stack y librerías

| Librería | Versión | Para qué |
|---|---|---|
| `androidx.wear:wear` | 1.3.0 | Componentes propios de Wear OS (`WearableRecyclerView`, `BoxInsetLayout`) |
| `com.google.android.gms:play-services-wearable` | 18.1.0 | Wearable Data Layer: `NodeClient`, `MessageClient` |
| `androidx.wear:wear-remote-interactions` / `wear-phone-interactions` | 1.0.0 / 1.1.0 | Utilidades estándar de interacción reloj↔teléfono |
| `com.google.android.material` | 1.11.0 | Estilos Material adaptados a Wear |
| `androidx.activity-ktx` / `fragment-ktx` | 1.8.2 / 1.6.2 | `ComponentActivity` + `lifecycleScope` |
| `androidx.lifecycle` (viewmodel/runtime-ktx) | 2.7.0 | Scopes de corrutinas |
| `com.google.dagger:hilt-android` | 2.50 | Inyección de dependencias (ver [Notas](#-notas-de-desarrollo) — uso mínimo en este módulo) |
| `kotlinx-coroutines-android` / `-play-services` | 1.7.3 | `.await()` sobre las llamadas de `NodeClient`/`MessageClient`, que devuelven `Task<T>` de Google Play Services |

---

## 🏗️ Estructura

```
wear/src/main/java/com/artesanias/wear/
├── WearApplication.kt                  # @HiltAndroidApp
├── data/
│   └── PhoneMessageListenerService.kt  # Recibe mensajes del teléfono (WearableListenerService)
└── presentation/
    └── Activities.kt                   # Las 5 Activities + adapters + data class ProductoStockItem
```

---

## 🚀 Instalación

### 1. Emparejar el reloj (físico o emulador) con el teléfono

Es un requisito de la Wearable Data Layer, no de esta app: el reloj y el teléfono deben estar emparejados (vía Play Store / Galaxy Wearable / Wear OS Emulator Pairing Assistant) **antes** de instalar la app, igual que cualquier app de Wear OS.

- **Dispositivo físico**: empareja el reloj con el teléfono desde la app "Wear OS" o la app del fabricante.
- **Emulador**: Android Studio → Device Manager → crea un AVD de Wear OS, y empareja con el AVD/dispositivo del teléfono desde el **Wear OS Emulator Pairing Assistant** (aparece al iniciar el AVD del reloj).

### 2. Instalar

```
Run → Run 'wear'   # En el reloj (físico o emulador) ya emparejado
```

### 3. Confirmar la conexión con la app

Dentro de la app del reloj, el botón de ping en `MainActivity` manda un mensaje de prueba al teléfono y confirma que el `NodeClient` encuentra el nodo emparejado.

---

## 📝 Notas de desarrollo

- Usa `ComponentActivity` (no `AppCompatActivity`) en todas las pantallas: es más liviano y suficiente para layouts XML simples, y es compatible tanto con Wear OS 2.x como 3.x.
- La comunicación usa `MessageClient` (no `DataClient`): los mensajes son comandos/eventos puntuales que necesitan entregarse pronto (alertas, confirmaciones), no un estado que deba sincronizarse y persistir — para eso es mejor `DataClient`, pero no es el caso aquí.
- Los layouts usan `BoxInsetLayout` para que el contenido no se recorte en pantallas redondas.
- `WearApplication` lleva `@HiltAndroidApp` por consistencia con el módulo `app`, pero este módulo casi no usa inyección de dependencias — las Activities llaman directo a `Wearable.getNodeClient(...)` / `getMessageClient(...)`, no hay repositorios ni ViewModels con `@Inject`.
