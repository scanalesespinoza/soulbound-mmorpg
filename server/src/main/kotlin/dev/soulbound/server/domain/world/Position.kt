package dev.soulbound.server.domain.world

data class Position(
    val x: Float = 0f,
    val z: Float = 0f
) {
    fun clamp(mapLimit: Float): Position =
        copy(x = x.coerceIn(-mapLimit, mapLimit), z = z.coerceIn(-mapLimit, mapLimit))
}
