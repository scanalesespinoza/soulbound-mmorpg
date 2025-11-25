package dev.soulbound.server.domain.monster

data class EnemyDefinition(
    val type: EnemyType,
    val displayName: String,
    val baseStats: EnemyStats,
    val weight: Int = 1,
    val minLevel: Int = 1,
    val maxLevel: Int = Int.MAX_VALUE
)

interface EnemyDefinitionProvider {
    fun all(): List<EnemyDefinition>
    fun find(type: EnemyType): EnemyDefinition?
}
