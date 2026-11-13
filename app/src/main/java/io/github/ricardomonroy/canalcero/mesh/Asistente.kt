package io.github.ricardomonroy.canalcero.mesh

/** Pasos del asistente para un nodo nuevo, en orden. La batería es del teléfono y la agrega la UI. */
enum class Paso { REGION, NOMBRE, PRIVACIDAD, CANAL, BATERIA }

/** El firmware nombra los nodos de fábrica "Meshtastic abcd" (últimos 4 hex de su ID). */
fun MeshState.nombreDeFabrica(): Boolean = miNodo?.let { nodos[it]?.user?.long_name }?.let { it.isBlank() || it.startsWith("Meshtastic ") } ?: false

/** Pasos que faltan según el estado del nodo. Se recalcula solo: un paso hecho desaparece. */
fun MeshState.pasosPendientes(): List<Paso> =
    buildList {
        if (lora.sinRegion()) add(Paso.REGION)
        if (nombreDeFabrica()) add(Paso.NOMBRE)
        if (ubicacionPublica()) add(Paso.PRIVACIDAD)
        if (canales.keys.none { it > 0 }) add(Paso.CANAL)
    }

/** Un nodo recién sacado de la caja: sin región o con el nombre de fábrica. */
fun MeshState.esNodoNuevo(): Boolean = lora.sinRegion() || nombreDeFabrica()
