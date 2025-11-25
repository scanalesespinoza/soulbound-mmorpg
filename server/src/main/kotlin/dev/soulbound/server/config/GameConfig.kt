package dev.soulbound.server.config

import dev.soulbound.server.domain.player.LevelingSystem
import dev.soulbound.server.domain.player.LinearLevelingSystem
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GameConfig {
    @Bean
    fun levelingSystem(): LevelingSystem = LinearLevelingSystem()
}
