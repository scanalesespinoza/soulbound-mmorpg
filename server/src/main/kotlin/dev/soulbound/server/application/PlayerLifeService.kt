package dev.soulbound.server.application

import dev.soulbound.server.domain.player.Player
import dev.soulbound.server.domain.player.PlayerId
import dev.soulbound.server.domain.player.PlayerRepository
import org.springframework.stereotype.Service
import kotlin.math.max

data class DamageResult(
    val player: Player,
    val died: Boolean
)

@Service
class PlayerLifeService(
    private val playerRepository: PlayerRepository
) {

    fun applyDamage(playerId: PlayerId, rawDamage: Int, defense: Int = 0): DamageResult? {
        val player = playerRepository.findById(playerId) ?: return null
        val damage = max(1, rawDamage - defense)
        val updated = player.applyDamage(damage)
        playerRepository.save(updated)
        return DamageResult(updated, updated.isDead())
    }
}
