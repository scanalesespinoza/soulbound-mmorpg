package dev.soulbound.server.domain.world

data class Region(
    val id: String,
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float
) {
    fun contains(pos: Position): Boolean =
        pos.x in minX..maxX && pos.z in minZ..maxZ
}
