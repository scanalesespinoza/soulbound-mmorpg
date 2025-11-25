package dev.soulbound.server.config

import dev.soulbound.server.domain.player.LevelingSystem
import dev.soulbound.server.domain.player.LinearLevelingSystem
import dev.soulbound.server.application.CombatBalanceConfig
import dev.soulbound.server.application.CombatEngine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GameConfig {
    @Bean
    fun levelingSystem(): LevelingSystem = LinearLevelingSystem()

    @Bean
    fun combatBalanceConfig(): CombatBalanceConfig = CombatBalanceConfig()

    @Bean
    fun combatEngine(config: CombatBalanceConfig): CombatEngine = CombatEngine(config)
}
