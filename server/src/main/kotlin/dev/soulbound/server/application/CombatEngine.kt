package dev.soulbound.server.application

import dev.soulbound.server.domain.monster.Monster
import dev.soulbound.server.domain.player.Player
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.concurrent.ThreadLocalRandom

class CombatEngine(private val config: CombatBalanceConfig = CombatBalanceConfig()) {
    private val rand = ThreadLocalRandom.current()

    fun damagePlayerToMonster(attacker: Player, defender: Monster): Int {
        val base = attacker.stats.attack - defender.defense
        val randomFactor = randomFactor()
        val scaled = base * config.playerToEnemyMultiplier * randomFactor
        return max(1, scaled.roundToInt())
    }

    fun damageMonsterToPlayer(attacker: Monster, defender: Player): Int {
        val base = attacker.attack - defender.stats.defense
        val randomFactor = randomFactor()
        val scaled = base * config.enemyToPlayerMultiplier * randomFactor
        return max(1, scaled.roundToInt())
    }

    private fun randomFactor(): Double =
        rand.nextDouble(config.randomFactorMin, config.randomFactorMax)
}
