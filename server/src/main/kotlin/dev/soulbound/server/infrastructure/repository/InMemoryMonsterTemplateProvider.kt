package dev.soulbound.server.infrastructure.repository

import dev.soulbound.server.domain.monster.MonsterTemplate
import dev.soulbound.server.domain.monster.MonsterTemplateProvider
import org.springframework.stereotype.Repository

@Repository
class InMemoryMonsterTemplateProvider : MonsterTemplateProvider {
    private val templates = listOf(
        MonsterTemplate(
            code = "zombie",
            displayName = "Zombie",
            maxHp = 60,
            attack = 10,
            defense = 3,
            xpReward = 25,
            moveSpeed = 2.2f,
            weight = 3
        ),
        MonsterTemplate(
            code = "brute",
            displayName = "Brute",
            maxHp = 120,
            attack = 16,
            defense = 6,
            xpReward = 50,
            moveSpeed = 1.6f,
            weight = 1
        ),
        MonsterTemplate(
            code = "runner",
            displayName = "Runner",
            maxHp = 45,
            attack = 8,
            defense = 1,
            xpReward = 30,
            moveSpeed = 3.0f,
            weight = 2
        )
    )

    override fun all(): List<MonsterTemplate> = templates
}
