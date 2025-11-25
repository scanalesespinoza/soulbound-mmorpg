package dev.soulbound.server.infrastructure.repository

import dev.soulbound.server.domain.world.MapDefinition
import dev.soulbound.server.domain.world.MapId
import dev.soulbound.server.domain.world.MapRepository
import dev.soulbound.server.domain.world.Position
import dev.soulbound.server.domain.world.Region
import dev.soulbound.server.domain.world.SpawnPoint
import org.springframework.stereotype.Repository

@Repository
class InMemoryMapRepository : MapRepository {
    private val maps: List<MapDefinition>

    init {
        val defaultId = MapId("default")
        val safeRegion = Region("safe-center", -12f, 12f, -12f, 12f)
        val spawnPoints = listOf(
            SpawnPoint("sp-1", defaultId, Position(-15f, 10f), enemyType = "Zombie", respawnSeconds = 6),
            SpawnPoint("sp-2", defaultId, Position(15f, -12f), enemyType = "Zombie", respawnSeconds = 6),
            SpawnPoint("sp-3", defaultId, Position(10f, 15f), enemyType = "Zombie", respawnSeconds = 6),
            SpawnPoint("sp-4", defaultId, Position(-10f, -15f), enemyType = "Zombie", respawnSeconds = 6)
        )
        maps = listOf(
            MapDefinition(
                id = defaultId,
                name = "Starting Area",
                limitX = 45f,
                limitZ = 45f,
                safeZones = listOf(safeRegion),
                spawnPoints = spawnPoints
            )
        )
    }

    override fun get(mapId: MapId): MapDefinition? = maps.find { it.id == mapId }

    override fun findAll(): List<MapDefinition> = maps
}
