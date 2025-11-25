package dev.soulbound.server.infrastructure.repository

import dev.soulbound.server.domain.player.Player
import dev.soulbound.server.domain.player.PlayerRepository
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class InMemoryPlayerRepository : PlayerRepository {
    private val players = ConcurrentHashMap<String, Player>()

    override fun findById(id: String): Player? = players[id]

    override fun save(player: Player): Player {
        players[player.id] = player
        return player
    }

    override fun findAll(): Collection<Player> = players.values

    override fun delete(id: String) {
        players.remove(id)
    }
}
