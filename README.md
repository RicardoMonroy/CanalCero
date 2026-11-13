# CanalCero

App Android para mandar mensajes y compartir ubicación por radio LoRa usando nodos [Meshtastic](https://meshtastic.org), **cuando no hay señal de celular ni internet**: apagones, zonas rurales, rodadas en moto, excursiones.

Es compatible con la red pública de Meshtastic y con su app oficial, pero simplifica todo: emparejar, crear canales privados con un QR, ver a tu grupo en el mapa y pedir ayuda.

> Proyecto personal y comunitario de Querétaro, México. Independiente de Meshtastic.

## Qué puedes hacer

- **Chatear** por canal (grupo) o en privado con otra persona, cifrado de extremo a extremo. Las palomitas indican si el mensaje llegó.
- **Canales privados** para tu familia o grupo: se crean con un toque y se comparten con un código QR o un enlace.
- **Mapa** con la ubicación de cada nodo, distancia y dirección. Puedes **descargar la zona** que quieras para usarlo sin internet.
- **Ubicación en vivo** con una persona o un canal, durante 15 min, 1 h o 4 h, y que se apague sola.
- **Modo ruta**: marca a tu grupo y la app te avisa si alguien se aleja o pierde señal.
- **Emergencia**: ✅ Estoy bien, ⚠️ Necesito ayuda, 📋 Pase de lista y 🆘 SOS con alarma y ubicación en vivo.
- **Explorar la red**: buscar nodos cercanos, ver quién te oye directo y por qué repetidores pasa tu mensaje.
- **Medir alcance**: la app registra sola la distancia máxima a la que te llega cada nodo sin repetidor, y puedes exportar una prueba completa en CSV.
- Funciona con la **pantalla apagada** y se reconecta sola si el nodo se reinicia.

## Qué necesitas

- Un teléfono **Android 10 o más nuevo** con Bluetooth.
- Un **nodo Meshtastic de 915 MHz** (región US, la que se usa en México) con firmware 2.5 o más nuevo. Ejemplos: Heltec V3/V4, SenseCAP T1000-E, RAK WisMesh, LilyGO T-Echo. En [docs/EQUIPOS.md](docs/EQUIPOS.md) hay una guía de compra por uso y presupuesto.

## Instalar

### Opción recomendada: Obtainium (con actualizaciones automáticas)

[Obtainium](https://github.com/ImranR98/Obtainium) es una app libre que instala apps directo desde GitHub y te avisa cuando hay versión nueva.

1. Instala Obtainium desde su [página de descargas](https://github.com/ImranR98/Obtainium/releases) (archivo `app-arm64-v8a-release.apk`) o desde [F-Droid](https://f-droid.org/packages/dev.imranr.obtainium.fdroid/).
2. Abre Obtainium y toca **Agregar app**.
3. En **URL de la fuente** pega:
   ```
   https://github.com/RicardoMonroy/CanalCero
   ```
4. Toca **Agregar** y luego **Instalar**. Si pregunta qué archivo usar, elige el que dice **arm64**.
5. Cuando publiquemos una versión nueva, Obtainium te avisará y la actualizas con un toque.

### Opción manual

1. Entra a [Releases](https://github.com/RicardoMonroy/CanalCero/releases) desde el teléfono.
2. Descarga el APK más reciente (el que dice **arm64**; si no abre, prueba el **universal**).
3. Ábrelo. Android pedirá permiso para **instalar apps de origen desconocido** desde tu navegador: acéptalo solo para esta instalación.

> Las versiones se publican a partir de la **v0.1.0**. Si aún no ves ninguna en Releases, todavía no hay versión pública.

## Primer uso

1. **Enciende tu nodo** y déjalo cerca del teléfono.
2. Abre CanalCero, toca **Buscar nodos** y elige el tuyo.
3. **PIN de emparejamiento**: si tu nodo tiene pantalla, el PIN aparece ahí. Si no tiene pantalla (como el T1000-E), normalmente es `123456`.
4. Si el nodo es nuevo, ve a **Ajustes → Mi nodo**:
   - **Configurar para México (915 MHz)**: sin región el nodo no transmite.
   - Ponle tu **nombre** y un **nombre corto** de hasta 4 letras (el que se ve en el mapa).
5. Para tu familia o grupo, en **Ajustes → Canales** crea un **canal privado** y compártelo con el QR. Los demás lo escanean desde la misma pantalla.

### Que funcione con la pantalla apagada (importante para emergencias)

Algunas marcas cierran las apps en segundo plano para ahorrar batería. Si dejas de recibir mensajes con el teléfono bloqueado, quita la restricción:

- **Samsung**: Ajustes → Aplicaciones → CanalCero → Batería → **Sin restricciones**.
- **Xiaomi / Redmi / POCO**: Ajustes → Apps → CanalCero → Ahorro de batería → **Sin restricciones**, y activa **Inicio automático**.
- **Motorola / Pixel**: Ajustes → Apps → CanalCero → Batería → **Sin restricciones**.

Mientras está conectada, CanalCero muestra una notificación fija: es lo que la mantiene viva.

## Privacidad: quién ve qué

| Dónde | Quién lo ve |
|---|---|
| Canal **Principal** (público, LongFast) | Cualquier persona con Meshtastic dentro del alcance o a través de repetidores. |
| **Canal privado** | Solo quien tenga su QR o enlace. Si se filtra, crea uno nuevo. |
| **Mensaje directo** | Solo esa persona (cifrado con su llave pública). |
| **Tu ubicación** | Exacta solo en canales privados, por directo o en vivo. CanalCero te avisa si el canal público la está enviando y la apaga con un toque. |

- **Borrar** un mensaje o conversación lo quita **solo de tu teléfono**. Meshtastic no permite borrar a distancia.
- **Salir de un canal** lo quita de tu nodo, pero los demás lo siguen usando con la misma llave.

## Compatibilidad con la app oficial de Meshtastic

CanalCero usa el mismo protocolo, así que puedes estar en los mismos canales con amigos que usen la app oficial. Las funciones especiales (Estoy bien, SOS, pase de lista) viajan como texto con emoji, y la app oficial las muestra tal cual.

## Medir el alcance de tus equipos

En **Ruta → Prueba de alcance**:

- **Récords de alcance directo**: se miden solos. Cada vez que un nodo te llega **sin repetidor** y los dos comparten **ubicación exacta y reciente** (canal privado o en vivo), la app guarda la distancia en línea recta máxima por nodo y su modelo de equipo. Así puedes comparar antenas y equipos.
- **Prueba completa**: toca **Iniciar**, aléjate con un nodo y al terminar **Exporta el CSV** con la señal (RSSI/SNR), los saltos y la distancia de cada paquete.

## Compilar desde el código

Requiere JDK 17 y Android SDK (API 37). Funciona igual en Linux y Windows.

```bash
./gradlew assembleDebug
```

En Windows usa `gradlew.bat assembleDebug`. Para conectar un Heltec por USB en Windows instala el driver CP210x.

Visión del proyecto y hoja de ruta: [docs/VISION.md](docs/VISION.md).

## Licencia

[GPL-3.0](LICENSE). Puedes usarla, estudiarla, modificarla y compartirla; las versiones modificadas deben seguir siendo libres.

## Créditos

- [Meshtastic](https://meshtastic.org): protocolo y protobufs (GPL-3.0), en `app/src/main/proto`.
- [MapLibre Native](https://maplibre.org) (BSD-2) con teselas de [OpenFreeMap](https://openfreemap.org); datos del mapa © colaboradores de [OpenStreetMap](https://www.openstreetmap.org/copyright) (ODbL).
- Íconos: [Material Symbols](https://github.com/google/material-design-icons) de Google, licencia Apache 2.0 (`app/src/main/res/drawable/ic_*.xml`, salvo `ic_launcher*` e `ic_notificacion`).
- [ZXing](https://github.com/zxing/zxing) para los códigos QR (Apache 2.0).
