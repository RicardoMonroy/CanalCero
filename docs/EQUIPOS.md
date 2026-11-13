# Guía de compra de equipos — CanalCero

Investigado el 2026-11-13. Los precios son de listados de fabricantes y tiendas; casi ninguno trae fecha, así que tómalos como referencia y confírmalos antes de comprar.

## Cómo asegurar que un equipo sirve

Todo lo que aparece aquí cumple tres condiciones:

1. **Está en la lista oficial de Meshtastic** (https://meshtastic.org/docs/hardware/devices/). Se marca explícitamente cuando no se pudo confirmar.
2. **Existe en versión 915 MHz** (región `US`, banda ISM 902–928 MHz, legal en México). Al comprar, elige siempre la variante **915 MHz** / **US915**. Una de 868 MHz o 433 MHz **no sirve** aquí.
3. **Habla Bluetooth con el teléfono**, así que funciona con CanalCero y con la app oficial.

Lo que **no** se puede garantizar es el alcance: depende de la altura, la línea de vista, la antena y el terreno. Eso se mide con nuestras pruebas.

### ESP32 vs nRF52: lo que más importa para baterías

| Chip | Consumo típico en recepción | Úsalo para |
|---|---|---|
| **nRF52840** | ~3–8 mA | Todo lo que va con batería o solar: bolsillo, casa, repetidores |
| **ESP32-S3** (tu Heltec V3) | ~30–80 mA | Equipos siempre conectados a corriente (moto, escritorio) |

Un nRF52 gasta entre 6 y 10 veces menos. Para un apagón de varios días, esa es la diferencia entre que el nodo aguante o se apague.

Fuente: East Mesh wiki, https://wiki.eastmesh.au/power/overview/

## Importación a México

- Desde **2025-08-15**, los paquetes de países sin tratado con México (**China** incluida, o sea AliExpress y la tienda de Heltec) pagan una tasa global de **33.5 %**.
- Desde **EE. UU. o Canadá**: exentos hasta US$50, luego 17 % (US$50–117) y 19 % arriba de eso.
- Una fuente dice que la tasa del 33.5 % vencía en abril de 2026 y otras dicen que sigue vigente. Verifícalo en el DOF o el SAT antes de una compra grande.
- No se pudieron consultar Mercado Libre ni Amazon.com.mx (bloquearon la consulta). Los únicos precios en MXN encontrados son de Newegg MX. **Revisa a mano** Mercado Libre, Amazon.com.mx y AliExpress.

---

## 1. Equipo de bolsillo ligado al teléfono (familia y amigos)

El teléfono pone la pantalla, el teclado y, si el equipo no tiene GPS, también la ubicación. Lo importante es que sea chico, aguante días de batería y sea difícil de descomponer.

| Rango | Equipo | Chip | GPS | Batería | Precio |
|---|---|---|---|---|---|
| Económico | **Heltec Mesh Node T114 Rev 2.0** | nRF52840 | opcional | no incluye (placa sola) | US$17.90–33.70; kit con GPS **MXN 2,061** en Newegg MX |
| Económico | **Heltec WiFi LoRa 32 V4** (sucesor de tu V3) | ESP32-S3 | opcional | no incluye | US$17.90–27.50 |
| Medio | **Seeed SenseCAP T1000-E** | nRF52840 | sí | 700 mAh, IP65, ~2–3 días | US$39.90–42.99; **MXN 1,731** en Newegg MX (envío desde HK) |
| Medio | **Seeed Wio Tracker L1 Pro** | nRF52840 | sí | sí | US$39.90 |
| Medio | **Heltec MeshPocket** | nRF52840 | no | 5000 mAh, también es power bank | US$49–59 |
| Medio | **Elecrow ThinkNode M1** | nRF52840 | sí | 1200 mAh, pantalla e-ink | US$54.95 |
| Premium | **LilyGO T-Echo Plus** | nRF52840 | sí | 2400 mAh, e-ink | US$64.22 (a veces agotado) |
| Premium | **RAK WisMesh Pocket V2** | nRF52840 | sí | 3200 mAh, ~3 días con GPS | US$84.97–99 |
| Premium | **LilyGO T-Deck Plus** | ESP32-S3 | sí | 2000 mAh, 18–24 h | US$69.99; tiene teclado propio, funciona sin teléfono |

**Recomendación para familia: SenseCAP T1000-E.**
- Tiene forma de tarjeta, resiste el agua (IP65) y trae GPS y batería.
- No tiene pantalla, y tampoco hace falta: todo se hace desde CanalCero. Así no hay nada que configurar en el equipo.
- Ojo: su batería es chica. Los "3 meses" que anuncian aplican solo a la versión LoRaWAN, no a la de Meshtastic.

**Si quieres más batería para apagones:** Heltec MeshPocket. Sus 5000 mAh además cargan el teléfono, aunque no tiene GPS.

**Para ti, como laboratorio:** el T114 es la forma más barata de probar un nRF52 junto a tus Heltec V3. Necesita una batería LiPo y una caja aparte.

**Evita la T-Deck Plus para apagones:** es ESP32 y dura menos de un día.

---

## 2. Nodo fijo en casa (repetidor en la azotea, debe aguantar apagones)

Debe ir en lo más alto que puedas, con panel solar y batería, en caja exterior. Rol `ROUTER` o `ROUTER_LATE`.

| Rango | Equipo | Energía | Protección | Precio |
|---|---|---|---|---|
| Económico | **Heltec MeshSolar** (placa nRF52) + kit de caja solar | panel de 18 V + 3× 3000 mAh (versión MeshTower) | caja del kit | placa US$38.99–44.90 + kit US$42.94–45.20. ⚠️ No aparece por nombre en la lista oficial |
| Medio | **SenseCAP Solar Node P1-Pro** | panel de 5 W + 4× 18650 incluidas + GPS | exterior | US$89.90 al lanzar, ~US$119 en revendedores. La P1 base cuesta US$69.90 y **no trae baterías** |
| Medio | **Elecrow ThinkNode M6** | panel de 6 W + 2× 18650, GPS | IP65 | US$99.95–119 |
| Premium | **RAK WisMesh Repeater Mini** (existe una V2 de "hasta 1 W") | 3200 mAh, panel en la tapa | IP67 | ~US$99.97 |
| Premium | **RAK WisMesh Repeater** | 10.8 V / 5200 mAh, panel externo de 10 W, entrada de 5–16 V DC | IP67 | US$99 solo, o US$299 con la batería solar Solar Battery Lite |

**Recomendación: SenseCAP Solar Node P1-Pro.**
- Es nRF52, trae panel y baterías, y se instala tal cual.
- Si quieres algo más robusto para años en la azotea, con conector para una antena de fibra de vidrio grande: **RAK WisMesh Repeater**.

**Descartados:**
- **Station G2:** tiene mucha potencia, pero está descontinuado.
- **Station G3:** necesita ≥25 W, mucha energía para una instalación solar.

### Dimensionar panel y batería

| Nodo | Consumo promedio | Panel |
|---|---|---|
| nRF52 repetidor con GPS | ~30 mA | 3–5 W |
| ESP32 repetidor sin GPS | ~50 mA | 5–10 W |
| ESP32 repetidor con GPS | ~70 mA | ~10 W |

- **Batería:** consumo × horas sin sol. Por ejemplo, 50 mA × 72 h ≈ 3.6 Ah, más un margen por días nublados.
- **Regla práctica:** ~10 Wp de panel por cada 10 Wh de batería.

---

## 3. Nodo para la moto (con GPS propio, alimentado de 12 V)

No encontramos equipos hechos para moto. La opción práctica es un equipo con GPS, metido en una caja sellada y alimentado desde la batería de la moto.

| Rango | Equipo | Chip | GPS | Notas | Precio |
|---|---|---|---|---|---|
| Económico | **Heltec Wireless Tracker V2** | ESP32-S3 | sí, multi-GNSS | 28 dBm; no trae batería ni caja | US$33.90 (V1.1: US$22.90) |
| Económico | **Heltec V4 + módulo GNSS** | ESP32-S3 | opcional | 28 dBm; necesita firmware >2.7.20 y elegir bien la variante S3R2 o S3R8 | US$17.90–27.50 + GNSS |
| Medio | **RAK WisBlock Starter Kit US915** (RAK19007 + RAK4631) + GPS RAK12500 | nRF52840 | módulo | modular; necesitas tu propia caja IP67 | ~US$31 + GPS (DigiKey, 12 semanas de entrega) |
| Medio | **RAK WisMesh Board ONE + RAK12500** | nRF52840 | módulo | antena IPEX | US$29.97–65.97 |
| Premium | **LilyGO T-Beam Supreme** | ESP32-S3 | sí | portabaterías 18650 (respaldo si se apaga la moto) | US$52.97–59.97 |

**Recomendación:**
- **Si va siempre conectado a la moto:** **Heltec Wireless Tracker V2**. Como tiene corriente constante, que sea ESP32 da igual.
- **Si quieres que siga transmitiendo con la moto apagada**, por ejemplo tras una caída: **T-Beam Supreme** con una 18650.

**Instalación:**
- **Convertidor 12 V a 5 V USB-C, encapsulado e impermeable (IP67 o IP68).** Busca entrada de 8–32 V y 3 A. Muchos "3 A" traen salida USB-A, así que revisa el conector.
- **Caja sellada** con prensaestopas en el cable y el equipo bien sujeto contra la vibración.
- **Antena lo más alta posible y cerca del radio:** cada metro de cable pierde señal.
- **Dónde conectarlo:** a la corriente que se apaga con la llave, o con un fusible, para no descargar la batería de la moto.

---

## 4. Antenas, cables y protección

### Antena exterior para casa (fibra de vidrio omnidireccional)

| Antena | Ganancia | Conector | Precio |
|---|---|---|---|
| **Rokland 5.8 dBi**, 31" | 5.8 dBi | N (macho o hembra según la página, revisa) | US$39.97–42.97; kit Meshtastic con abrazadera y cable US$49.97 |
| **ALFA AOA-915-5ACM** | 5 dBi | N macho, compacta | ~€12–16 en Europa. ⚠️ Hay falsificaciones: cómprala en una tienda confiable |

**Más ganancia no siempre es mejor.** Una antena de 8–10 dBi concentra la señal en un plano más plano: alcanza más lejos en terreno llano, pero pierde a los nodos que estén mucho más arriba o más abajo. Querétaro tiene cerros, así que 5–6 dBi es el punto de equilibrio.

### Antena de mano (mejora la que trae el equipo)

| Antena | Precio |
|---|---|
| Heltec, 915 MHz | US$3.90 |
| MESHTAC 2.5 dBi flexible SMA (Rokland) | — |
| Látigo muzi de 17 cm | — |

- Las antenas "de 10 dBi" para mano son publicidad: medidas dan 5–6 dBi.
- En una medición con NanoVNA, la antena chata que viene de fábrica dio SWR ~3.5 contra ~1.3 del látigo muzi. Cambiar esa antena es la mejora más barata que hay.

### Antena para la moto

| Antena | Montaje | Precio |
|---|---|---|
| **2J Antennas 2J3915M-915** | base magnética, SMA macho | ~£10.71 (también en digikey.com.mx) |
| **DPD Productions 900 MHz ISM low-profile** | NMO, 4" de alto, 17 ft de cable | — |

### Cable coaxial (pérdida a 915 MHz)

| Cable | Pérdida | 10 m pierden |
|---|---|---|
| **LMR-400** | ~0.128 dB/m | ~1.3 dB |
| **LMR-240** | ~0.25 dB/m (dato sin verificar) | ~2.5 dB |

Si la antena queda a más de 3–4 m del radio, usa LMR-400. Mejor aún: sube el radio al mástil junto con la antena y lleva hasta arriba solo el cable de corriente.

### Protección contra rayos

**Obligatorio para la antena de la azotea:** pararrayos coaxial (descargador N macho/N hembra) conectado a tierra.

| Descargador | Rango | Precio |
|---|---|---|
| Proxicast | 0–6 GHz | US$19.95 |
| RAK | 0–2.7 GHz | US$19.97 |
| L-com, gas de 90 V | 0–3 GHz | US$33.99–35.59 |

### Conectores: los errores más comunes

- **RP-SMA ≠ SMA.** Las antenas de Helium usan RP-SMA y **no** embonan con el SMA de los equipos Meshtastic.
- **Las antenas de fibra de vidrio usan conector N.** Para tu Heltec necesitas un cable o adaptador de N hembra a SMA macho.
- **Heltec V3/V4 y RAK Board ONE traen IPEX/U.FL en la placa.** Para usar una antena externa necesitas un pigtail de IPEX a SMA.
- **Nunca transmitas sin antena conectada:** puedes dañar el chip LoRa.

---

## Resumen: qué comprar primero

| Prioridad | Qué | Para qué | Aprox. |
|---|---|---|---|
| 1 | Antenas de látigo buenas para tus 2 Heltec V3 | Mejorar ya el alcance de las pruebas | US$4–10 c/u |
| 2 | 1× SenseCAP Solar Node P1-Pro, o un RAK WisMesh Repeater + antena Rokland 5.8 dBi + pararrayos | Nodo de casa en la azotea | US$90–170 |
| 3 | 1× Heltec Wireless Tracker V2 + convertidor 12 V a USB-C IP67 + caja | Probar el modo moto | US$40–60 |
| 4 | SenseCAP T1000-E para cada familiar | Red de respaldo familiar | US$40 c/u |

## Pendiente por verificar

- Precios en MXN en Mercado Libre, Amazon.com.mx y AliExpress.
- Si sigue vigente la tasa de importación del 33.5 %.
- Precios de RAK WisMesh Repeater Mini V2, muzi R1 Neo y RAK WisMesh Tag (no se encontraron).
- Si Heltec MeshSolar aparece en la lista oficial.
- Consumo real de Heltec V4 y T114 con Meshtastic (lo podemos medir nosotros).
