package io.github.ricardomonroy.canalcero.mesh

import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleSettings
import java.security.SecureRandom

/** Límites del firmware (nanopb max_size - 1 por el terminador). */
const val MAX_BYTES_NOMBRE_LARGO = 39
const val MAX_BYTES_NOMBRE_CORTO = 4
const val MAX_BYTES_NOMBRE_CANAL = 11

/** Precisión de posición: 0 = no compartir, 32 = exacta. */
const val PRECISION_EXACTA = 32

/** Mismo formato que la app oficial, para que un QR de CanalCero también funcione ahí. */
private const val PREFIJO_URL = "https://meshtastic.org/e/?add=true#"

/** Canal secundario nuevo con llave AES-256 aleatoria y posición exacta. */
fun canalPrivado(
    nombre: String,
    azar: SecureRandom = SecureRandom(),
): ChannelSettings =
    ChannelSettings(
        name = nombre,
        psk = ByteArray(32).also(azar::nextBytes).toByteString(),
        module_settings = ModuleSettings(position_precision = PRECISION_EXACTA),
    )

fun urlCanal(
    s: ChannelSettings,
    lora: Config.LoRaConfig?,
): String {
    val b64 =
        ChannelSet(settings = listOf(s), lora_config = lora)
            .encodeByteString()
            .base64Url()
            .trimEnd('=')
    return PREFIJO_URL + b64
}

/** Lee un enlace de canal de Meshtastic (con o sin ?add=true). Null si no es válido. */
fun leerUrlCanal(url: String): ChannelSettings? {
    val frag = url.trim().substringAfter('#', "").ifEmpty { return null }
    val relleno = frag + "=".repeat((4 - frag.length % 4) % 4)
    val bytes: ByteString = relleno.decodeBase64() ?: return null
    val set = runCatching { ChannelSet.ADAPTER.decode(bytes) }.getOrNull() ?: return null
    return set.settings.firstOrNull()?.takeIf { it.psk.size > 0 }
}

/** Primer índice libre para un canal secundario (el firmware admite 0–7). */
fun indiceLibre(canales: Map<Int, Channel>): Int? = (1..7).firstOrNull { it !in canales }

/** ¿Ya tenemos este canal? Lo identifican nombre + llave, no el índice. */
fun yaExiste(
    canales: Map<Int, Channel>,
    s: ChannelSettings,
): Boolean = canales.values.any { it.settings?.name == s.name && it.settings?.psk == s.psk }

/**
 * El firmware manda la posición solo por el primer canal con precisión != 0.
 * Para que vaya exacta por el privado, el primario público se pone en 0 (además, no se publica la ubicación).
 */
fun primarioSinPosicion(primario: Channel): Channel {
    val s = primario.settings ?: ChannelSettings()
    val m = s.module_settings ?: ModuleSettings()
    return primario.copy(settings = s.copy(module_settings = m.copy(position_precision = 0)))
}
