package dev.soulbound.server.domain.world

import dev.soulbound.server.domain.monster.EnemyType

data class SpawnPoint(
    val id: String,
    val mapId: MapId,
    val position: Position,
    val enemyType: EnemyType,
    val minLevel: Int = 1,
    val maxLevel: Int = Int.MAX_VALUE,
    val respawnSeconds: Long = 6,
    val jitterRadius: Float = 2f
)
