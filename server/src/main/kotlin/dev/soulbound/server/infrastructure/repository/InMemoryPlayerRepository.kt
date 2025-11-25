package dev.soulbound.server.infrastructure.repository

import dev.soulbound.server.domain.player.Player
import dev.soulbound.server.domain.player.PlayerId
import dev.soulbound.server.domain.player.PlayerRepository
import dev.soulbound.server.infrastructure.repository.mapper.PlayerMapper
import org.springframework.stereotype.Repository
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap

@Repository
@Primary
class InMemoryPlayerRepository : PlayerRepository {
    private val mapper = PlayerMapper()
    private val players = ConcurrentHashMap<PlayerId, dev.soulbound.server.infrastructure.repository.model.PlayerEntity>()

    override fun findById(id: PlayerId): Player? = players[id]?.let { mapper.toDomain(it) }

    override fun save(player: Player): Player {
        val entity = mapper.toEntity(player)
        players[player.id] = entity
        return mapper.toDomain(entity)
    }

    override fun findAll(): Collection<Player> = players.values.map { mapper.toDomain(it) }

    override fun delete(id: PlayerId) {
        players.remove(id)
    }
}
