package dev.soulbound.server.ws.dto

data class PlayerDiedDto(
    val playerId: String,
    val mapId: String,
    val xpLost: Int,
    val xpAfter: Int,
    val level: Int
)
