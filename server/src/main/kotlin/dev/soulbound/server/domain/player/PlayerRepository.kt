package dev.soulbound.server.domain.player

interface PlayerRepository {
    fun findById(id: String): Player?
    fun save(player: Player): Player
    fun findAll(): Collection<Player>
    fun delete(id: String)
}
