package dev.soulbound.server.model

data class Monster(
    val id: Int,
    val name: String,
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
