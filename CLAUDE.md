# CanalCero

App Android (Kotlin + Jetpack Compose) para comunicarse por LoRa con nodos Meshtastic. Visión completa, casos de uso, hoja de ruta e identidad: [docs/VISION.md](docs/VISION.md).

## Contexto

- Dueño: Ricardo, Querétaro, México. Responder en español.
- Se desarrolla en dos máquinas: PC Linux (casa) y laptop Windows (pruebas de campo con amigos). Todo debe compilar en ambas; nada de rutas absolutas ni scripts solo-bash en el build.
- Hardware actual: 2× Heltec V3 (sin GPS, sin PSRAM). Teléfonos: Samsung S23, Pixel 10.
- Firmware Meshtastic sin modificar (2.7.x). Región `US`, preset `LONG_FAST`. Debe seguir siendo compatible con la app oficial y la red pública.

## Reglas

- Nunca subir al repo (es público): keystore de firma, contraseñas, ni PSKs de canales privados. La firma de release se lee de `~/.config/canalcero/signing.properties` (o `-Pcanalcero.signing=<ruta>`).
- Mensajes especiales (SOS, "Estoy bien", pase de lista) van como texto normal con formato reconocible, no por puerto propio, para que la app oficial los muestre.
- Payload LoRa ≤ ~230 bytes. Nada de fotos ni voz.

## Código

- `mesh/MeshBle.kt`: cliente GATT (servicio `6ba1b218-…`). Handshake: escribir `ToRadio(want_config_id)` y leer `FromRadio` hasta recibir bytes vacíos; repetir cada vez que `FromNum` notifica.
- `mesh/MeshState.kt`: estado + reductor puro `aplicar(FromRadio)`, probado en JVM (`app/src/test`).
- `mesh/Canales.kt`: canal privado (PSK AES-256 aleatoria), enlace/QR compatible con la app oficial (`meshtastic.org/e/?add=true#<ChannelSet base64url>`). Admin local en `MeshBle.administrar()`: pide `session_passkey` con `get_owner_request` y envuelve los cambios en `begin/commit_edit_settings`.
- Protobufs: copia del tag `v2.7.26` de meshtastic/protobufs en `app/src/main/proto`, generados con Wire. `nanopb.proto` vive en `app/src/main/proto-deps` (solo import). Para actualizar, reemplazar los `.proto` con un tag más nuevo.

## Build

```bash
./gradlew assembleDebug        # Linux
gradlew.bat assembleDebug      # Windows
```

Requiere JDK 17 y Android SDK (compileSdk 37). `local.properties` con `sdk.dir` lo genera Android Studio; no se versiona.

En Windows, para conectar un Heltec V3 por USB, instalar el driver CP210x.
