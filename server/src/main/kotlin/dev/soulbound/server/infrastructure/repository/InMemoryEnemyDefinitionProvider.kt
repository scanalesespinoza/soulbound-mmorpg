package dev.soulbound.server.infrastructure.repository

import dev.soulbound.server.domain.monster.EnemyDefinition
import dev.soulbound.server.domain.monster.EnemyDefinitionProvider
import dev.soulbound.server.domain.monster.EnemyStats
import dev.soulbound.server.domain.monster.EnemyType
import org.springframework.stereotype.Repository

@Repository
class InMemoryEnemyDefinitionProvider : EnemyDefinitionProvider {
    private val defs = listOf(
        EnemyDefinition(
            type = EnemyType.GOBLIN,
            displayName = "Goblin",
            baseStats = EnemyStats(maxHp = 50, attack = 10, defense = 2, moveSpeed = 2.4f, xpReward = 25),
            weight = 4,
            minLevel = 1,
            maxLevel = 3
        ),
        EnemyDefinition(
            type = EnemyType.ORC,
            displayName = "Orc",
            baseStats = EnemyStats(maxHp = 120, attack = 18, defense = 5, moveSpeed = 1.8f, xpReward = 80),
            weight = 2,
            minLevel = 3,
            maxLevel = 6
        ),
        EnemyDefinition(
            type = EnemyType.SKELETON,
            displayName = "Skeleton",
            baseStats = EnemyStats(maxHp = 80, attack = 14, defense = 3, moveSpeed = 2.6f, xpReward = 50),
            weight = 3,
            minLevel = 2,
            maxLevel = 6
        )
    )

    override fun all(): List<EnemyDefinition> = defs

    override fun find(type: EnemyType): EnemyDefinition? = defs.find { it.type == type }
}
