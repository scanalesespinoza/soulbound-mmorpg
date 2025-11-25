package dev.soulbound.server.application

import dev.soulbound.server.domain.world.MapDefinition
import dev.soulbound.server.domain.world.MapId
import dev.soulbound.server.domain.world.MapRepository
import dev.soulbound.server.domain.world.Position
import dev.soulbound.server.domain.world.SpawnPoint
import org.springframework.stereotype.Service
import java.util.concurrent.ThreadLocalRandom

@Service
class WorldService(
    private val mapRepository: MapRepository
) {
    private val defaultMapId = MapId("default")
    private val rand = ThreadLocalRandom.current()

    fun currentMap(): MapDefinition = mapRepository.get(defaultMapId) ?: error("Default map missing")

    fun getMap(mapId: MapId): MapDefinition? = mapRepository.get(mapId)

    fun spawnPoints(mapId: MapId = defaultMapId): List<SpawnPoint> =
        mapRepository.get(mapId)?.spawnPoints ?: emptyList()

    fun isInSafeZone(mapId: MapId, position: Position): Boolean {
        val map = mapRepository.get(mapId) ?: return false
        return map.safeZones.any { it.contains(position) }
    }

    fun clampToBounds(mapId: MapId, position: Position): Position {
        val map = mapRepository.get(mapId) ?: return position
        return position.clamp(map.limitX, map.limitZ)
    }

    fun canMoveTo(mapId: MapId, position: Position): Boolean {
        val map = mapRepository.get(mapId) ?: return false
        return position.x in -map.limitX..map.limitX && position.z in -map.limitZ..map.limitZ
    }

    fun randomSpawnNear(spawn: SpawnPoint): Position {
        val angle = rand.nextDouble(0.0, Math.PI * 2)
        val radius = rand.nextDouble(0.0, spawn.jitterRadius.toDouble()).toFloat()
        val dx = kotlin.math.cos(angle).toFloat() * radius
        val dz = kotlin.math.sin(angle).toFloat() * radius
        return Position(spawn.position.x + dx, spawn.position.z + dz)
    }
}
