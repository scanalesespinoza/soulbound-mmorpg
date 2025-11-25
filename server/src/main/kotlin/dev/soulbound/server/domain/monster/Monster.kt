package dev.soulbound.server.domain.monster

data class Monster(
    val id: Int,
    val name: String,
    val type: EnemyType,
    var hp: Int,
    var maxHp: Int,
    var attack: Int,
    var defense: Int,
    var xpReward: Int,
    var moveSpeed: Float,
    var x: Float,
    var z: Float,
    var spawnX: Float,
    var spawnZ: Float
)
