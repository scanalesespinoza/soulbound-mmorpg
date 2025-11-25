package dev.soulbound.server.domain.monster

interface MonsterRepository {
    fun findById(id: Int): Monster?
    fun save(monster: Monster): Monster
    fun delete(id: Int)
    fun findAll(): Collection<Monster>
}
