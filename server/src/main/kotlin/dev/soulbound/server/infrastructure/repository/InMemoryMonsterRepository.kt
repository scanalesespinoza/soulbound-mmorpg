package dev.soulbound.server.infrastructure.repository

import dev.soulbound.server.domain.monster.Monster
import dev.soulbound.server.domain.monster.MonsterRepository
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class InMemoryMonsterRepository : MonsterRepository {
    private val monsters = ConcurrentHashMap<Int, Monster>()

    override fun findById(id: Int): Monster? = monsters[id]

    override fun save(monster: Monster): Monster {
        monsters[monster.id] = monster
        return monster
    }

    override fun delete(id: Int) {
        monsters.remove(id)
    }

    override fun findAll(): Collection<Monster> = monsters.values
}
