package dev.soulbound.server.domain.world

interface MapRepository {
    fun get(mapId: MapId): MapDefinition?
    fun findAll(): List<MapDefinition>
}
