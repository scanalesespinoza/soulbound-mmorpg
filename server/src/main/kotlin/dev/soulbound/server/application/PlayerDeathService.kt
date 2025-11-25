package dev.soulbound.server.application

import dev.soulbound.server.domain.player.Player
import dev.soulbound.server.domain.player.PlayerId
import dev.soulbound.server.domain.player.PlayerRepository
import dev.soulbound.server.domain.world.Position
import dev.soulbound.server.domain.world.MapId
import dev.soulbound.server.domain.world.MapRepository
import org.springframework.stereotype.Service
import kotlin.math.floor

data class PlayerDeathResult(
    val player: Player,
    val xpLost: Int
)

data class PlayerRespawnResult(
    val player: Player
)

@Service
class PlayerDeathService(
    private val playerRepository: PlayerRepository,
    private val mapRepository: MapRepository
) {

    fun handlePlayerDeath(playerId: PlayerId): PlayerDeathResult? {
        val player = playerRepository.findById(playerId) ?: return null
        val xpLoss = floor(player.experience * 0.1).toInt()
        val newXp = (player.experience - xpLoss).coerceAtLeast(0)
        val updated = player.kill().copy(experience = newXp)
        val saved = playerRepository.save(updated)
        return PlayerDeathResult(saved, xpLoss)
    }

    fun respawnPlayer(playerId: PlayerId): PlayerRespawnResult? {
        val player = playerRepository.findById(playerId) ?: return null
        val map = mapRepository.get(player.mapId) ?: return null
        val respawnPos = firstSafeSpot(map.safeZones) ?: player.spawnPosition
        val revived = player.revive(respawnPos, player.stats.maxHp)
        val saved = playerRepository.save(revived)
        return PlayerRespawnResult(saved)
    }

    private fun firstSafeSpot(safeZones: List<dev.soulbound.server.domain.world.Region>): Position? {
        val region = safeZones.firstOrNull() ?: return null
        val x = (region.minX + region.maxX) / 2f
        val z = (region.minZ + region.maxZ) / 2f
        return Position(x, z)
    }
}
