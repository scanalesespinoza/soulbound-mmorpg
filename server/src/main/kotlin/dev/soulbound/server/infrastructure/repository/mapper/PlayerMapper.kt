package dev.soulbound.server.infrastructure.repository.mapper

import dev.soulbound.server.domain.player.Player
import dev.soulbound.server.domain.player.PlayerId
import dev.soulbound.server.domain.player.Stats
import dev.soulbound.server.domain.world.MapId
import dev.soulbound.server.domain.world.Position
import dev.soulbound.server.infrastructure.repository.model.PlayerEntity

class PlayerMapper {
    fun toEntity(player: Player): PlayerEntity =
        PlayerEntity(
            id = player.id.value,
            name = player.name,
            mapId = player.mapId.value,
            level = player.level,
            experience = player.experience,
            nextLevelXp = player.nextLevelXp,
            hp = player.stats.currentHp,
            maxHp = player.stats.maxHp,
            attack = player.stats.attack,
            defense = player.stats.defense,
            moveSpeed = player.stats.moveSpeed,
            x = player.position.x,
            z = player.position.z,
            spawnX = player.spawnPosition.x,
            spawnZ = player.spawnPosition.z,
            dead = player.dead
        )

    fun toDomain(entity: PlayerEntity): Player =
        Player(
            id = PlayerId(entity.id),
            name = entity.name,
            mapId = MapId(entity.mapId),
            position = Position(entity.x, entity.z),
            spawnPosition = Position(entity.spawnX, entity.spawnZ),
            level = entity.level,
            experience = entity.experience,
            nextLevelXp = entity.nextLevelXp,
            stats = Stats(
                maxHp = entity.maxHp,
                currentHp = entity.hp,
                attack = entity.attack,
                defense = entity.defense,
                moveSpeed = entity.moveSpeed
            ),
            dead = entity.dead
        )
}
