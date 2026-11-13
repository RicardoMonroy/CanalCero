package io.github.ricardomonroy.canalcero.mesh

import org.meshtastic.proto.Config

/** Cada cuánto el nodo transmite su posición. El firmware la manda antes si detecta movimiento (smart). */
enum class PerfilPosicion(
    val nombre: String,
    val detalle: String,
) {
    NORMAL("Normal", "Cada 15 min, o antes si te mueves más de 100 m"),
    RODADA("Rodada", "Cada minuto, o cada 30 s si te mueves más de 50 m. Usa más batería y ocupa más el canal."),
    AHORRO("Ahorro", "Cada hora. Para nodos fijos o batería baja."),
    ;

    fun aplicar(c: Config.PositionConfig): Config.PositionConfig =
        when (this) {
            NORMAL -> c.copy(position_broadcast_secs = 900, position_broadcast_smart_enabled = true, broadcast_smart_minimum_distance = 100, broadcast_smart_minimum_interval_secs = 30)
            RODADA -> c.copy(position_broadcast_secs = 60, position_broadcast_smart_enabled = true, broadcast_smart_minimum_distance = 50, broadcast_smart_minimum_interval_secs = 30)
            AHORRO -> c.copy(position_broadcast_secs = 3600, position_broadcast_smart_enabled = false)
        }

    companion object {
        /** Null si el nodo tiene una configuración propia que no coincide con ningún perfil. */
        fun de(c: Config.PositionConfig): PerfilPosicion? = entries.firstOrNull { it.aplicar(c) == c }
    }
}

data class Rol(
    val rol: Config.DeviceConfig.Role,
    val nombre: String,
    val detalle: String,
    val aviso: String? = null,
)

/** Roles útiles para CanalCero, del más común al más especializado. */
val ROLES =
    listOf(
        Rol(Config.DeviceConfig.Role.CLIENT, "Cliente", "Nodo ligado a un teléfono. El normal para familia, amigos y moto."),
        Rol(Config.DeviceConfig.Role.CLIENT_MUTE, "Cliente silencioso", "No retransmite. Úsalo cuando hay varios nodos juntos en el mismo lugar."),
        Rol(
            Config.DeviceConfig.Role.CLIENT_BASE,
            "Base de casa",
            "Nodo en la azotea que da prioridad a los nodos marcados como favoritos (tu familia).",
        ),
        Rol(
            Config.DeviceConfig.Role.ROUTER_LATE,
            "Repetidor",
            "Retransmite todo, después de los demás. Para un punto alto que cubra huecos.",
            aviso = "Solo en un punto alto y fijo: en un lugar bajo empeora la red.",
        ),
        Rol(
            Config.DeviceConfig.Role.ROUTER,
            "Router",
            "Infraestructura con prioridad en la red. Solo para puntos estratégicos (cerro, torre).",
            aviso = "El firmware apaga pantalla y WiFi y puede dejar de aceptar la app por Bluetooth. Para revertirlo quizá necesites cable USB.",
        ),
    )

/** El nodo sin región configurada no transmite. */
fun Config.LoRaConfig?.sinRegion(): Boolean = this == null || region == Config.LoRaConfig.RegionCode.UNSET

/** Región para México: US 902–928 MHz, preset LongFast (compatible con la red pública). */
fun Config.LoRaConfig?.paraMexico(): Config.LoRaConfig =
    (this ?: Config.LoRaConfig()).copy(
        region = Config.LoRaConfig.RegionCode.US,
        use_preset = true,
        modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST,
        tx_enabled = true,
    )
