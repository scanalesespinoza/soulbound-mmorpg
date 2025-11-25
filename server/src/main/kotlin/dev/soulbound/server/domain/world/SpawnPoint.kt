package dev.soulbound.server.domain.world

data class SpawnPoint(
    val id: String,
    val mapId: MapId,
    val position: Position,
    val enemyType: String,
    val respawnSeconds: Long = 6,
    val jitterRadius: Float = 2f
)
