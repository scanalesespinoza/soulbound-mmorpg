package dev.soulbound.server.infrastructure.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.soulbound.server.domain.player.Player
import dev.soulbound.server.domain.player.PlayerId
import dev.soulbound.server.domain.player.PlayerRepository
import dev.soulbound.server.infrastructure.repository.mapper.PlayerMapper
import dev.soulbound.server.infrastructure.repository.model.PlayerEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Persistencia simple basada en archivo JSON. No es @Primary para no interferir con la in-memory.
 * Activar con @Qualifier("fileBasedPlayerRepository") si se desea usarla.
 * El archivo por defecto es players.json en el cwd, configurable con player.repo.file.path.
 */
@Repository("fileBasedPlayerRepository")
class FileBasedPlayerRepository(
    @Value("\${player.repo.file.path:players.json}") private val filePath: String
) : PlayerRepository {
    private val mapper = PlayerMapper()
    private val objectMapper = jacksonObjectMapper()
    private val lock = ReentrantReadWriteLock()
    private val players = ConcurrentHashMap<PlayerId, PlayerEntity>()

    init {
        loadFromFile()
    }

    private fun loadFromFile() {
        val file = File(filePath)
        if (!file.exists()) return
        lock.write {
            try {
                val list: List<PlayerEntity> = objectMapper.readValue(file)
                players.clear()
                list.forEach { entity -> players[PlayerId(entity.id)] = entity }
            } catch (_: Exception) {
                // si el archivo está corrupto, se ignora y se arranca con vacío
            }
        }
    }

    private fun persistToFile() {
        val file = File(filePath)
        val snapshot = lock.read { players.values.toList() }
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, snapshot)
        } catch (_: Exception) {
            // en un MVP simplemente ignoramos errores de escritura
        }
    }

    override fun findById(id: PlayerId): Player? = lock.read { players[id]?.let { mapper.toDomain(it) } }

    override fun save(player: Player): Player {
        lock.write {
            players[player.id] = mapper.toEntity(player)
        }
        persistToFile()
        return player
    }

    override fun findAll(): Collection<Player> = lock.read { players.values.map { mapper.toDomain(it) } }

    override fun delete(id: PlayerId) {
        lock.write {
            players.remove(id)
        }
        persistToFile()
    }
}
