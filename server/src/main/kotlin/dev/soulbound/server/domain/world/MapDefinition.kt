package dev.soulbound.server.domain.world

data class MapDefinition(
    val id: MapId,
    val name: String,
    val limitX: Float,
    val limitZ: Float = limitX,
    val regions: List<Region> = emptyList(),
    val safeZones: List<Region> = emptyList(),
    val spawnPoints: List<SpawnPoint> = emptyList()
)
