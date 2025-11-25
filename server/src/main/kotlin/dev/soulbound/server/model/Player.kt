package dev.soulbound.server.model

data class Player(
    val id: String,
    val name: String,
    var x: Float = 0f,
    var z: Float = 0f,
    var hp: Int = 100,
    var spawnX: Float = 0f,
    var spawnZ: Float = 0f,
    var level: Int = 1,
    var xp: Int = 0
) {
    fun nextLevelXp() = 100 + (level - 1) * 50
    fun maxHp() = 100 + (level - 1) * 20
}
