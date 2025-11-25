package dev.soulbound.server.domain.monster

data class MonsterTemplate(
    val code: String,
    val displayName: String,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val xpReward: Int,
    val moveSpeed: Float,
    val weight: Int = 1
)

interface MonsterTemplateProvider {
    fun all(): List<MonsterTemplate>
}
