package dev.soulbound.server.domain.world

interface WorldRepository {
    fun get(): WorldState
    fun save(world: WorldState): WorldState
}
