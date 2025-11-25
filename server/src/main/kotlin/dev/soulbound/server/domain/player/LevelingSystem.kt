package dev.soulbound.server.domain.player

interface LevelingSystem {
    fun nextLevelXp(level: Int): Int
}

class LinearLevelingSystem : LevelingSystem {
    override fun nextLevelXp(level: Int): Int = 100 * level
}
