package dev.soulbound.server.domain.player

data class Stats(
    val maxHp: Int = 100,
    val currentHp: Int = maxHp,
    val attack: Int = 14,
    val defense: Int = 4,
    val moveSpeed: Float = 6f
) {
    val isDead: Boolean get() = currentHp <= 0

    fun applyDamage(amount: Int): Stats {
        val dmg = amount.coerceAtLeast(0)
        val hp = (currentHp - dmg).coerceAtLeast(0)
        return copy(currentHp = hp)
    }

    fun heal(amount: Int): Stats {
        val healed = (currentHp + amount.coerceAtLeast(0)).coerceAtMost(maxHp)
        return copy(currentHp = healed)
    }

    fun withMaxHpDelta(delta: Int): Stats {
        val newMax = (maxHp + delta).coerceAtLeast(1)
        val newCurrent = currentHp.coerceAtMost(newMax)
        return copy(maxHp = newMax, currentHp = newCurrent)
    }
}
