package dev.soulbound.server.ws.dto

import dev.soulbound.server.domain.player.Player

data class PlayerDto(
    val id: String,
    val name: String,
    val level: Int,
    val xp: Int,
    val nextLevelXp: Int,
    val hp: Int,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val moveSpeed: Float,
    val x: Float,
    val z: Float,
    val spawnX: Float,
    val spawnZ: Float,
    val mapId: String,
    val dead: Boolean
) {
    companion object {
        fun from(player: Player): PlayerDto = PlayerDto(
            id = player.id.value,
            name = player.name,
            level = player.level,
            xp = player.experience,
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
            mapId = player.mapId.value,
            dead = player.isDead()
        )
    }
}
