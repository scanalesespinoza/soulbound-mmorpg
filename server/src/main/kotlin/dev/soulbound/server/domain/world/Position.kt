package dev.soulbound.server.domain.world

data class Position(
    val x: Float = 0f,
    val z: Float = 0f
) {
    fun clamp(limitX: Float, limitZ: Float = limitX): Position =
        copy(x = x.coerceIn(-limitX, limitX), z = z.coerceIn(-limitZ, limitZ))
}
