package dev.soulbound.server.domain.player

import dev.soulbound.server.domain.world.MapId
import dev.soulbound.server.domain.world.Position

data class Player(
    val id: PlayerId,
    val name: String,
    val mapId: MapId = MapId("default"),
    val position: Position = Position(),
    val spawnPosition: Position = Position(),
    val level: Int = 1,
    val experience: Int = 0,
    val nextLevelXp: Int = 100,
    val stats: Stats = Stats(),
    val dead: Boolean = false
) {
    fun isDead(): Boolean = dead || stats.isDead

    fun withPosition(newPosition: Position): Player = copy(position = newPosition)

    fun withSpawn(newSpawn: Position): Player = copy(spawnPosition = newSpawn)

    fun applyDamage(amount: Int): Player {
        val updatedStats = stats.applyDamage(amount)
        return copy(stats = updatedStats, dead = updatedStats.isDead)
    }

    fun kill(): Player = copy(stats = stats.copy(currentHp = 0), dead = true)

    fun heal(amount: Int): Player = copy(stats = stats.heal(amount), dead = false)

    fun revive(at: Position = spawnPosition, hpAmount: Int = stats.maxHp): Player =
        copy(dead = false, stats = stats.copy(currentHp = hpAmount), position = at)

    fun withStats(newStats: Stats): Player = copy(stats = newStats, dead = newStats.isDead)

    fun withProgression(level: Int, experience: Int, nextLevelXp: Int, updatedStats: Stats): Player =
        copy(level = level, experience = experience, nextLevelXp = nextLevelXp, stats = updatedStats, dead = updatedStats.isDead)
}
