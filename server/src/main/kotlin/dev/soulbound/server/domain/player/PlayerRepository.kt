package dev.soulbound.server.domain.player

interface PlayerRepository {
    fun findById(id: PlayerId): Player?
    fun save(player: Player): Player
    fun findAll(): Collection<Player>
    fun delete(id: PlayerId)
}
