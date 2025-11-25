package dev.soulbound.server.application

data class CombatBalanceConfig(
    val randomFactorMin: Double = 0.9,
    val randomFactorMax: Double = 1.1,
    val playerToEnemyMultiplier: Double = 1.0,
    val enemyToPlayerMultiplier: Double = 1.0
)
