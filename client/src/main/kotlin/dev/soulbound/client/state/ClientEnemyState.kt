package dev.soulbound.client.state

data class ClientEnemyState(
    val id: Int,
    val name: String = "Enemy",
    val hp: Int,
    val maxHp: Int,
    val x: Float,
    val z: Float
)
