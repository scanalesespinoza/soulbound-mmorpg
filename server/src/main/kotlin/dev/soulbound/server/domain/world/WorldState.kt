package dev.soulbound.server.domain.world

data class WorldState(
    val mapLimit: Float,
    val safeRadius: Float,
    val wildRadiusMin: Float,
    val wildRadiusMax: Float,
    val maxMonsters: Int,
    val monsterSpeed: Float,
    val chaseRadius: Float,
    val attackRadius: Float,
    val playerAttackRange: Float
)
