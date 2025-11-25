package dev.soulbound.server.ws.dto

data class PlayerRespawnedDto(
    val playerId: String,
    val mapId: String,
    val x: Float,
    val z: Float,
    val hp: Int,
    val maxHp: Int,
    val level: Int,
    val xp: Int,
    val nextLevelXp: Int
)
