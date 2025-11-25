package dev.soulbound.server.model

data class Monster(
    val id: Int,
    val name: String,
    var hp: Int,
    var x: Float,
    var z: Float
)
