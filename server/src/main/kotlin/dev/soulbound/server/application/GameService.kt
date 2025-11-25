package dev.soulbound.server.application

import dev.soulbound.server.domain.monster.Monster
import dev.soulbound.server.domain.monster.MonsterRepository
import dev.soulbound.server.domain.player.Player
import dev.soulbound.server.domain.player.PlayerId
import dev.soulbound.server.domain.player.PlayerRepository
import dev.soulbound.server.domain.player.Stats
import dev.soulbound.server.domain.world.Position
import dev.soulbound.server.domain.world.MapId
import dev.soulbound.server.domain.world.WorldRepository
import dev.soulbound.server.domain.world.WorldState
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class JoinResult(val player: Player, val monsters: List<Monster>)

sealed class GameEvent {
    data class MonsterSpawn(val monster: Monster) : GameEvent()
    data class MonsterMove(val monster: Monster) : GameEvent()
    data class MonsterUpdate(val monster: Monster) : GameEvent()
    data class MonsterKilled(val id: Int, val byPlayerId: String) : GameEvent()
    data class PlayerUpdate(val player: Player) : GameEvent()
    data class PlayerDead(val player: Player) : GameEvent()
}

data class MoveState(var targetX: Float, var targetZ: Float, var timer: Double)
data class RespawnTask(val at: Long)

@Service
class GameService(
    private val playerRepository: PlayerRepository,
    private val monsterRepository: MonsterRepository,
    private val worldRepository: WorldRepository,
    private val progressionService: PlayerProgressionService,
    private val lifeService: PlayerLifeService,
    private val worldService: WorldService
    ) {
    private val rand = ThreadLocalRandom.current()
    private val monsterIdGen = AtomicInteger(1)
    private val moveStates = ConcurrentHashMap<Int, MoveState>()
    private val respawnQueue = PriorityBlockingQueue<RespawnTask>(11, compareBy { it.at })

    fun join(sessionId: String, name: String): JoinResult {
        val player = Player(
            id = PlayerId(sessionId),
            name = name,
            mapId = MapId("default"),
            position = Position(0f, 0f),
            spawnPosition = Position(0f, 0f),
            stats = Stats(),
            level = 1,
            experience = 0,
            nextLevelXp = 100
        )
        playerRepository.save(player)
        return JoinResult(player, monsterRepository.findAll().toList())
    }

    fun disconnect(sessionId: String) {
        playerRepository.delete(PlayerId(sessionId))
    }

    fun updatePosition(sessionId: String, x: Float, z: Float) {
        playerRepository.findById(PlayerId(sessionId))?.let { player ->
            if (!player.isDead()) {
                val newPos = Position(x, z)
                val clamped = worldService.clampToBounds(player.mapId, newPos)
                if (worldService.canMoveTo(player.mapId, clamped)) {
                    playerRepository.save(player.withPosition(clamped))
                }
            }
        }
    }

    fun respawn(sessionId: String): List<GameEvent> {
        val p = playerRepository.findById(PlayerId(sessionId)) ?: return emptyList()
        val revived = p.revive()
        playerRepository.save(revived)
        return listOf(GameEvent.PlayerUpdate(revived))
    }

    fun attack(sessionId: String, fx: Float, fz: Float, monsterId: Int?): List<GameEvent> {
        val world = worldRepository.get()
        val player = playerRepository.findById(PlayerId(sessionId)) ?: return emptyList()
        val targets = if (monsterId != null) {
            listOfNotNull(monsterRepository.findById(monsterId))
        } else {
            monsterRepository.findAll()
        }
        val events = mutableListOf<GameEvent>()
        val range = world.playerAttackRange
        val damage = player.stats.attack
        val facingLen = kotlin.math.sqrt((fx * fx + fz * fz).toDouble()).toFloat().coerceAtLeast(0.001f)
        val facingNormX = fx / facingLen
        val facingNormZ = fz / facingLen
        targets.forEach { m ->
            val dist = distance(player.position.x, player.position.z, m.x, m.z)
            if (dist <= range) {
                val dirX = m.x - player.position.x
                val dirZ = m.z - player.position.z
                val dirLen = kotlin.math.sqrt((dirX * dirX + dirZ * dirZ).toDouble()).toFloat().coerceAtLeast(0.001f)
                val dot = (dirX / dirLen) * facingNormX + (dirZ / dirLen) * facingNormZ
                if (dot >= 0f) {
                    m.hp -= damage
                    knockback(m, player.position.x, player.position.z, world)
                    if (m.hp <= 0) {
                        handleMonsterDeath(player, m, events)
                    } else {
                        monsterRepository.save(m)
                        events.add(GameEvent.MonsterUpdate(m))
                    }
                }
            }
        }
        return events
    }

    fun spawnTick(): List<GameEvent> {
        val world = worldRepository.get()
        val events = mutableListOf<GameEvent>()
        val now = System.currentTimeMillis()
        if (respawnQueue.isEmpty() && monsterRepository.findAll().size < world.maxMonsters) {
            repeat(world.maxMonsters - monsterRepository.findAll().size) {
                spawnMonster(world)?.let { events.add(it) }
            }
        }
        while (respawnQueue.peek()?.at?.let { it <= now } == true) {
            respawnQueue.poll()
            if (monsterRepository.findAll().size < world.maxMonsters) {
                spawnMonster(world)?.let { events.add(it) }
            }
        }
        return events
    }

    fun worldTick(): List<GameEvent> {
        val world = worldRepository.get()
        val dt = 0.2f
        val events = mutableListOf<GameEvent>()
        monsterRepository.findAll().forEach { monster ->
            val moveState = moveStates.computeIfAbsent(monster.id) { newMoveState(world) }
            val nearest = nearestPlayer(monster.x, monster.z, world)
            val target = if (nearest != null && nearest.second <= world.chaseRadius && !worldService.isInSafeZone(nearest.first.mapId, nearest.first.position)) {
                moveState.timer = 0.5
                Pair(nearest.first.position.x, nearest.first.position.z)
            } else {
                moveState.timer -= dt.toDouble()
                val distToTarget = distance(monster.x, monster.z, moveState.targetX, moveState.targetZ)
                if (distToTarget < 0.5f || moveState.timer <= 0.0) {
                    val newState = newMoveState(world)
                    moveState.targetX = newState.targetX
                    moveState.targetZ = newState.targetZ
                    moveState.timer = newState.timer
                }
                Pair(moveState.targetX, moveState.targetZ)
            }
            val dirX = target.first - monster.x
            val dirZ = target.second - monster.z
            val dist = sqrt((dirX * dirX + dirZ * dirZ).toDouble()).toFloat()
            if (dist > 0.001f) {
                val step = (world.monsterSpeed * dt).coerceAtMost(dist)
                val nx = monster.x + dirX / dist * step
                val nz = monster.z + dirZ / dist * step
                val bounded = confineMonsterToWild(nx, nz, world)
                val moved = bounded.first != monster.x || bounded.second != monster.z
                monster.x = bounded.first
                monster.z = bounded.second
                monsterRepository.save(monster)
                if (moved) {
                    events.add(GameEvent.MonsterMove(monster))
                }
                playerRepository.findAll().forEach { p ->
                    if (!p.isDead() && !worldService.isInSafeZone(p.mapId, p.position) && distance(monster.x, monster.z, p.position.x, p.position.z) <= world.attackRadius) {
                        val dmgResult = lifeService.applyDamage(p.id, monster.attack, p.stats.defense)
                        dmgResult?.let { res ->
                            if (res.died) {
                                events.add(GameEvent.PlayerDead(res.player))
                            }
                            events.add(GameEvent.PlayerUpdate(res.player))
                        }
                    }
                }
            }
        }
        return events
    }

    private fun handleMonsterDeath(player: Player, monster: Monster, events: MutableList<GameEvent>) {
        monsterRepository.delete(monster.id)
        moveStates.remove(monster.id)
        scheduleRespawn()
        val progress = progressionService.addExperience(player.id, monster.xpReward)
        val updatedPlayer = progress?.player ?: player
        events.add(GameEvent.MonsterKilled(monster.id, player.id.value))
        events.add(GameEvent.PlayerUpdate(updatedPlayer))
    }

    private fun scheduleRespawn() {
        val delayMillis = rand.nextLong(5000L, 10001L)
        respawnQueue.offer(RespawnTask(System.currentTimeMillis() + delayMillis))
    }

    private fun spawnMonster(world: WorldState): GameEvent? {
        val currentMap = worldService.currentMap()
        val spawnPoints = currentMap.spawnPoints
        if (spawnPoints.isEmpty()) return null
        val chosen = spawnPoints[rand.nextInt(spawnPoints.size)]
        val pos = worldService.randomSpawnNear(chosen)
        val spawnPos = confineMonsterToWild(pos.x, pos.z, world)
        val monster = Monster(
            id = monsterIdGen.getAndIncrement(),
            name = "Zombie",
            hp = 60,
            maxHp = 60,
            attack = 10,
            defense = 3,
            xpReward = 25,
            moveSpeed = world.monsterSpeed,
            x = spawnPos.first,
            z = spawnPos.second,
            spawnX = spawnPos.first,
            spawnZ = spawnPos.second
        )
        monsterRepository.save(monster)
        moveStates[monster.id] = newMoveState(world)
        return GameEvent.MonsterSpawn(monster)
    }

    private fun newMoveState(world: WorldState): MoveState {
        val target = randomWildPosition(world)
        val timer = rand.nextDouble(1.0, 3.0)
        return MoveState(target.first, target.second, timer)
    }

    private fun randomWildPosition(world: WorldState): Pair<Float, Float> {
        val currentMap = worldService.currentMap()
        val radius = rand.nextDouble(world.wildRadiusMin.toDouble(), world.wildRadiusMax.toDouble()).toFloat()
        val angle = rand.nextDouble(0.0, Math.PI * 2)
        val x = (cos(angle) * radius).toFloat()
        val z = (sin(angle) * radius).toFloat()
        val clamped = Position(x, z).clamp(currentMap.limitX, currentMap.limitZ)
        return Pair(clamped.x, clamped.z)
    }

    private fun confineMonsterToWild(x: Float, z: Float, world: WorldState): Pair<Float, Float> {
        val map = worldService.currentMap()
        val clamped = Position(x, z).clamp(map.limitX, map.limitZ)
        val distToCenter = distance(clamped.x, clamped.z, 0f, 0f)
        val safeRadius = world.safeRadius
        return if (distToCenter < safeRadius) {
            if (distToCenter < 0.001f) {
                Pair(safeRadius, 0f)
            } else {
                val scale = safeRadius / distToCenter
                Pair(clamped.x * scale, clamped.z * scale)
            }
        } else Pair(clamped.x, clamped.z)
    }

    private fun nearestPlayer(x: Float, z: Float, world: WorldState): Pair<Player, Float>? {
        var best: Player? = null
        var bestDist = Float.MAX_VALUE
        playerRepository.findAll().forEach {
            if (worldService.isInSafeZone(it.mapId, it.position)) return@forEach
            val d = distance(x, z, it.position.x, it.position.z)
            if (d < bestDist) {
                bestDist = d
                best = it
            }
        }
        return if (best != null) Pair(best!!, bestDist) else null
    }

    private fun knockback(monster: Monster, px: Float, pz: Float, world: WorldState) {
        val dirX = monster.x - px
        val dirZ = monster.z - pz
        val len = sqrt((dirX * dirX + dirZ * dirZ).toDouble()).toFloat().coerceAtLeast(0.001f)
        val kb = 2.4f
        val bounded = confineMonsterToWild(monster.x + dirX / len * kb, monster.z + dirZ / len * kb, world)
        monster.x = bounded.first
        monster.z = bounded.second
    }

    private fun distance(x1: Float, z1: Float, x2: Float, z2: Float): Float {
        val dx = x1 - x2
        val dz = z1 - z2
        return sqrt((dx * dx + dz * dz).toDouble()).toFloat()
    }
}
