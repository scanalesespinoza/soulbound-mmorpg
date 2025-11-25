package dev.soulbound.server.infrastructure.repository

import dev.soulbound.server.domain.world.WorldRepository
import dev.soulbound.server.domain.world.WorldState
import org.springframework.stereotype.Repository
import java.util.concurrent.atomic.AtomicReference

@Repository
class InMemoryWorldRepository : WorldRepository {
    private val state = AtomicReference(
        WorldState(
            mapLimit = 45f,
            safeRadius = 12f,
            wildRadiusMin = 18f,
            wildRadiusMax = 44f,
            maxMonsters = 8,
            monsterSpeed = 2.25f,
            chaseRadius = 20f,
            attackRadius = 1.8f,
            playerAttackRange = 2.2f
        )
    )

    override fun get(): WorldState = state.get()

    override fun save(world: WorldState): WorldState {
        state.set(world)
        return world
    }
}
