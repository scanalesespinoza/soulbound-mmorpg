package dev.soulbound.server.domain.monster

data class EnemyStats(
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val moveSpeed: Float,
    val xpReward: Int
)
