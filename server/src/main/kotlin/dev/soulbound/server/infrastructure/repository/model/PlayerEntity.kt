package dev.soulbound.server.infrastructure.repository.model

data class PlayerEntity(
    val id: String,
    val name: String,
    val mapId: String,
    val level: Int,
    val experience: Int,
    val nextLevelXp: Int,
    val hp: Int,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val moveSpeed: Float,
    val x: Float,
    val z: Float,
    val spawnX: Float,
    val spawnZ: Float,
    val dead: Boolean
)
