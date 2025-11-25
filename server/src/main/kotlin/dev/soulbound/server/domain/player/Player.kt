package dev.soulbound.server.domain.player

data class Player(
    val id: String,
    val name: String,
    var x: Float = 0f,
    var z: Float = 0f,
    var hp: Int = 100,
    var maxHp: Int = 100,
    var attack: Int = 12,
    var defense: Int = 4,
    var moveSpeed: Float = 6f,
    var spawnX: Float = 0f,
    var spawnZ: Float = 0f,
    var level: Int = 1,
    var xp: Int = 0,
    var dead: Boolean = false
) {
    fun nextLevelXp() = 100 * level
}
