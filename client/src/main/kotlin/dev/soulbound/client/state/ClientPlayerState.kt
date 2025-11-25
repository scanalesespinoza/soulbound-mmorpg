package dev.soulbound.client.state

data class ClientPlayerState(
    val id: String = "",
    val name: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val nextLevelXp: Int = 100,
    val hp: Int = 100,
    val maxHp: Int = 100,
    val attack: Int = 10,
    val defense: Int = 4,
    val moveSpeed: Float = 6f,
    val x: Float = 0f,
    val z: Float = 0f,
    val spawnX: Float = 0f,
    val spawnZ: Float = 0f,
    val mapId: String = "default",
    val dead: Boolean = false
) {
    val alive: Boolean get() = !dead && hp > 0
}
