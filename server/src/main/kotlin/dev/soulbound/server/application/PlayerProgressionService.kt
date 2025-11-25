package dev.soulbound.server.application

import dev.soulbound.server.domain.player.LevelingSystem
import dev.soulbound.server.domain.player.Player
import dev.soulbound.server.domain.player.PlayerId
import dev.soulbound.server.domain.player.PlayerRepository
import dev.soulbound.server.domain.player.Stats
import org.springframework.stereotype.Service

data class ProgressResult(
    val player: Player,
    val leveledUp: Boolean
)

@Service
class PlayerProgressionService(
    private val playerRepository: PlayerRepository,
    private val levelingSystem: LevelingSystem
) {

    fun addExperience(playerId: PlayerId, xpGained: Int): ProgressResult? {
        val player = playerRepository.findById(playerId) ?: return null
        var totalXp = player.experience + xpGained
        var level = player.level
        var leveledUp = false
        var nextXp = levelingSystem.nextLevelXp(level)
        var stats: Stats = player.stats

        while (totalXp >= nextXp) {
            totalXp -= nextXp
            level += 1
            leveledUp = true
            stats = stats
                .withMaxHpDelta(20)
                .copy(
                    attack = stats.attack + 2,
                    defense = stats.defense + 1,
                    currentHp = stats.maxHp
                )
            nextXp = levelingSystem.nextLevelXp(level)
        }

        val updated = player.withProgression(level, totalXp, nextXp, stats)
        playerRepository.save(updated)
        return ProgressResult(updated, leveledUp)
    }
}
