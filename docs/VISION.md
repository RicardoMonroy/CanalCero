# CanalCero — visión del proyecto

App Android para comunicarse por LoRa usando nodos Meshtastic, pensada para cuando hay **cero señal**: apagones prolongados en Querétaro y rodadas en moto por zonas sin cobertura (p. ej. presa de Zimapán).

## Principios

- **Firmware Meshtastic sin modificar.** La app habla con el nodo por BLE usando los protobufs oficiales (`ToRadio` / `FromRadio`).
- **Compatible con la red pública y con la app oficial.** Región `US` (915 MHz, México), preset `LONG_FAST`. Funciones especiales (SOS, "Estoy bien", pase de lista) viajan como texto normal con formato reconocible, para que la app oficial también las muestre.
- **Configuración mínima.** La familia no configura nada: el admin prepara cada nodo y comparte canales por QR.
- **Solo Android.** Kotlin + Jetpack Compose.
- **Ahorro de batería.** Tema oscuro por defecto (pantallas OLED), servicio en segundo plano eficiente.

## Casos de uso

### 1. Respaldo de comunicación en apagón (casa y familia)
- Nodo fijo en casa con batería + panel solar, alto y con buena antena (rol `ROUTER` / `ROUTER_LATE`).
- Botón "Estoy bien" con ubicación, pase de lista (quién respondió y hace cuánto), SOS.
- Historial persistente en el teléfono (el Heltec V3 no tiene PSRAM → sin Store & Forward).

### 2. Rodadas en moto
- Nodo por moto con GPS propio, alimentado de 12 V con buck a USB.
- Mapas offline descargados antes de salir.
- Mapa del grupo: distancia, rumbo, "visto hace X min", alerta si alguien se queda atrás.
- Mensajes rápidos de un toque (Paro, Gasolina, Falla mecánica, Accidente).
- Lectura en voz alta (TTS) por el intercomunicador del casco.
- Intervalo de posición ~30–60 s en movimiento para no saturar el canal.

## Hoja de ruta

1. **MVP:** conectar por BLE, chat por canal y directo, lista de nodos, mapa con posiciones.
2. **Modo admin:** aprovisionar nodos (región, preset, rol, canales) y compartir canales por QR.
   - Renombrar el nodo (`AdminMessage.set_owner`: nombre largo + corto de 4 caracteres; p. ej. hoy `rm79_d534` y `P15…`).
   - Precisión de posición por canal: público `LongFast` = 0 (no compartir), privado = 32 (exacta). El firmware manda la posición solo por el primer canal con precisión ≠ 0.
   - Intervalo de transmisión de posición (más corto para modo moto).
   - Ubicación exacta por mensaje directo. Probado (2026-11-13, firmware 2.7.15): el firmware **no** recorta la posición que manda el teléfono.
   - ✅ Fase 2 completa y probada entre S23 y Pixel 10.
3. **Modo ruta** (moto, viaje, salidas fuera de la zona habitual): mapas offline, distancia/rumbo, mensajes rápidos, TTS, prueba de alcance (registro RSSI/SNR + distancia).
   - Compartir ubicación solo con personas o canales elegidos y por tiempo limitado ("1 h", "hasta llegar"): la app manda la posición por directo/canal privado; el nodo no la difunde.
   - Aviso si el canal público tiene precisión ≠ 0 (en ese caso la ubicación sale aproximada a cualquiera en LongFast).
4. **Modo emergencia:** "Estoy bien", pase de lista, SOS, historial persistente.
5. (Opcional) Puente MQTT.

## Hardware

| Uso | Actual / sugerido |
|---|---|
| Ligado a teléfono | Heltec V3 (ESP32-S3 + SX1262, sin GPS, sin PSRAM) — ya tenemos 2 |
| Nodo fijo casa | RAK WisBlock 4631 (nRF52, bajo consumo) + LiFePO4 + solar 5–10 W |
| Nodo moto | Heltec Wireless Tracker o LilyGO T-Beam (con GPS) |

Teléfonos de prueba: Samsung S23, Pixel 10.

Guía de compra detallada (por finalidad y rango de precio, antenas, energía): [EQUIPOS.md](EQUIPOS.md).

## Identidad gráfica

- Nombre: **CanalCero** — el canal 0 es el primario en Meshtastic; "cero señal" es el escenario de uso.
- Logo: un "0" (anillo) con nodos conectados.
- Paleta:
  - Naranja señal `#F26B1D` (primario, legible al sol)
  - Grafito `#1C1F23` (fondo oscuro)
  - Arena `#F4EFE6` (texto/superficie clara)
  - Verde "Estoy bien" `#2FB36B` (solo ese uso)
  - Rojo SOS `#E5383B` (solo ese uso)
- Botones grandes, usables con guantes.

## Distribución

- Repo público en GitHub, licencia GPL-3.0 (los protobufs de Meshtastic son GPL-3.0).
- APK firmado en GitHub Releases; actualizaciones con Obtainium.

## Contexto de red (nov 2026)

- meshmap.net muestra un nodo cercano: `DAD75 (QR75)`, `!8fccfdc0`, ThinkNode M5, `US / LONG_FAST`, firmware 2.7.26, rol CLIENT, sin vecinos por radio. Precisión del mapa ±4 km.
- Firmware objetivo: Meshtastic 2.7.x.
