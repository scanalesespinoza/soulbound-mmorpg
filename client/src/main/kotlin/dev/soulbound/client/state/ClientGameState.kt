package dev.soulbound.client.state

import java.util.concurrent.ConcurrentHashMap

class ClientGameState {
    @Volatile
    var player: ClientPlayerState = ClientPlayerState()
        private set

    private val enemies = ConcurrentHashMap<Int, ClientEnemyState>()

    fun updatePlayer(newState: ClientPlayerState) {
        player = newState
    }

    fun upsertEnemy(state: ClientEnemyState) {
        enemies[state.id] = state
    }

    fun removeEnemy(id: Int) {
        enemies.remove(id)
    }

    fun getEnemies(): Collection<ClientEnemyState> = enemies.values
}
