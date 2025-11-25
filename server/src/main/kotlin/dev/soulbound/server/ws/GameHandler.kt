package dev.soulbound.server.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.soulbound.server.model.Monster
import dev.soulbound.server.model.Player
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

data class WsMessage(val type: String, val data: Any?)
data class MoveState(var targetX: Float, var targetZ: Float, var timer: Double)
data class RespawnTask(val at: Long)

@Component
class GameHandler(private val mapper: ObjectMapper) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val players = ConcurrentHashMap<String, Player>()
    private val monsters = ConcurrentHashMap<Int, Monster>()
    private val moveStates = ConcurrentHashMap<Int, MoveState>()
    private val respawnQueue = PriorityBlockingQueue<RespawnTask>(11, compareBy { it.at })
    private val monsterIdGen = AtomicInteger(1)
    private val maxMonsters = 6
    private val mapLimit = 23f
    private val monsterSpeed = 2.25f
    private val chaseRadius = 15f
    private val attackRadius = 1.2f
    private val attackDamage = 8

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
        log.info("WS connected: ${'$'}{session.id}")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        sessions.remove(session.id)
        players.remove(session.id)
        log.info("WS disconnected: ${'$'}{session.id}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val json = message.payload
            val envelope: Map<String, Any?> = mapper.readValue(json)
            when (envelope["type"]) {
                "join" -> {
                    val name = envelope["data"] as? String ?: "Player"
                    val player = Player(id = session.id, name = name)
                    players[session.id] = player
                    player.spawnX = player.x
                    player.spawnZ = player.z
                    send(session, WsMessage("join_ack", player))
                    // push current monsters so the player sees the world state right away
                    monsters.values.forEach { send(session, WsMessage("monster_spawn", it)) }
                }
                "attack" -> {
                    val data = envelope["data"] as? Map<*, *>
                    val monsterId = (data?.get("monsterId") as? Number)?.toInt()
                    val fx = (data?.get("fx") as? Number)?.toFloat() ?: 0f
                    val fz = (data?.get("fz") as? Number)?.toFloat() ?: 1f
                    val player = players[session.id] ?: return
                    val px = player.x
                    val pz = player.z
                    val range = 2.2f
                    val damage = 14 + (player.level - 1) * 4
                    val facingLen = kotlin.math.sqrt((fx * fx + fz * fz).toDouble()).toFloat().coerceAtLeast(0.001f)
                    val facingNormX = fx / facingLen
                    val facingNormZ = fz / facingLen
                    val targets = if (monsterId != null) {
                        listOfNotNull(monsters[monsterId])
                    } else {
                        monsters.values.toList()
                    }
                    targets.forEach { m ->
                        val dist = distance(px, pz, m.x, m.z)
                        if (dist <= range) {
                            val dirX = m.x - px
                            val dirZ = m.z - pz
                            val dirLen = kotlin.math.sqrt((dirX * dirX + dirZ * dirZ).toDouble()).toFloat().coerceAtLeast(0.001f)
                            val dot = (dirX / dirLen) * facingNormX + (dirZ / dirLen) * facingNormZ
                            if (dot >= 0f) { // front arc 180 deg
                                m.hp -= damage
                                knockback(m, px, pz)
                                resolveMonsterDamage(player, m)
                            }
                        }
                    }
                }
                "pos" -> {
                    val data = envelope["data"] as? Map<*, *> ?: return
                    val px = (data["x"] as? Number)?.toFloat() ?: return
                    val pz = (data["z"] as? Number)?.toFloat() ?: return
                    players[session.id]?.let {
                        it.x = px
                        it.z = pz
                    }
                }
                "respawn" -> {
                    val p = players[session.id] ?: return
                    p.hp = p.maxHp()
                    p.x = p.spawnX
                    p.z = p.spawnZ
                    broadcast(WsMessage("player_update", p))
                }
            }
        } catch (ex: Exception) {
            log.error("Failed to handle message", ex)
        }
    }

    private fun send(session: WebSocketSession, msg: WsMessage) {
        try {
            session.sendMessage(TextMessage(mapper.writeValueAsString(msg)))
        } catch (e: Exception) {
            log.warn("Failed to send message", e)
        }
    }

    private fun broadcast(msg: WsMessage) {
        val text = mapper.writeValueAsString(msg)
        sessions.values.forEach {
            try {
                if (it.isOpen) it.sendMessage(TextMessage(text))
            } catch (e: Exception) {
                log.warn("Failed to send message", e)
            }
        }
    }

    private fun scheduleRespawn() {
        val delayMillis = ThreadLocalRandom.current().nextLong(5000L, 10001L)
        respawnQueue.offer(RespawnTask(System.currentTimeMillis() + delayMillis))
    }

    @Scheduled(fixedRate = 1000)
    fun spawnTick() {
        val now = System.currentTimeMillis()
        // Top up immediately if none present and no pending respawn
        if (respawnQueue.isEmpty() && monsters.size < maxMonsters) {
            repeat(maxMonsters - monsters.size) { spawnMonster() }
        }
        // Consume ready respawns
        while (respawnQueue.peek()?.at?.let { it <= now } == true) {
            respawnQueue.poll()
            if (monsters.size < maxMonsters) {
                spawnMonster()
            }
        }
    }

    @Scheduled(fixedRate = 200)
    fun worldTick() {
        val dt = 0.2f
        monsters.forEach { (id, monster) ->
            val moveState = moveStates.computeIfAbsent(id) { newMoveState() }
            val nearest = nearestPlayer(monster.x, monster.z)
            val target = if (nearest != null && nearest.second <= chaseRadius) {
                moveState.timer = 0.5
                Pair(nearest.first.x, nearest.first.z)
            } else {
                moveState.timer -= dt.toDouble()
                val distToTarget = distance(monster.x, monster.z, moveState.targetX, moveState.targetZ)
                if (distToTarget < 0.5f || moveState.timer <= 0.0) {
                    val newState = newMoveState()
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
                val step = (monsterSpeed * dt).coerceAtMost(dist)
                val nx = monster.x + dirX / dist * step
                val nz = monster.z + dirZ / dist * step
                monster.x = nx.coerceIn(-mapLimit, mapLimit)
                monster.z = nz.coerceIn(-mapLimit, mapLimit)
                broadcast(WsMessage("monster_move", monster))
                // damage nearby players
                players.values.forEach { p ->
                    if (distance(monster.x, monster.z, p.x, p.z) <= attackRadius) {
                        p.hp = (p.hp - attackDamage).coerceAtLeast(0)
                        broadcast(WsMessage("player_update", p))
                    }
                }
            }
        }
    }

    private fun newMoveState(): MoveState {
        val rand = ThreadLocalRandom.current()
        val targetX = rand.nextDouble(-mapLimit.toDouble(), mapLimit.toDouble()).toFloat()
        val targetZ = rand.nextDouble(-mapLimit.toDouble(), mapLimit.toDouble()).toFloat()
        val timer = rand.nextDouble(1.0, 3.0)
        return MoveState(targetX, targetZ, timer)
    }

    private fun nearestPlayer(x: Float, z: Float): Pair<Player, Float>? {
        var best: Player? = null
        var bestDist = Float.MAX_VALUE
        players.values.forEach {
            val d = distance(x, z, it.x, it.z)
            if (d < bestDist) {
                bestDist = d
                best = it
            }
        }
        return if (best != null) Pair(best!!, bestDist) else null
    }

    private fun distance(x1: Float, z1: Float, x2: Float, z2: Float): Float {
        val dx = x1 - x2
        val dz = z1 - z2
        return sqrt((dx * dx + dz * dz).toDouble()).toFloat()
    }

    private fun knockback(monster: Monster, px: Float, pz: Float) {
        val dirX = monster.x - px
        val dirZ = monster.z - pz
        val len = sqrt((dirX * dirX + dirZ * dirZ).toDouble()).toFloat().coerceAtLeast(0.001f)
        val kb = 2.4f
        monster.x = (monster.x + dirX / len * kb).coerceIn(-mapLimit, mapLimit)
        monster.z = (monster.z + dirZ / len * kb).coerceIn(-mapLimit, mapLimit)
    }

    private fun resolveMonsterDamage(player: Player, monster: Monster) {
        if (monster.hp <= 0) {
            monsters.remove(monster.id)
            moveStates.remove(monster.id)
            scheduleRespawn()
            player.xp += 25
            while (player.xp >= player.nextLevelXp()) {
                player.xp -= player.nextLevelXp()
                player.level += 1
                player.hp = player.maxHp()
            }
            broadcast(WsMessage("monster_killed", mapOf("id" to monster.id, "by" to player.id)))
            broadcast(WsMessage("player_update", player))
        } else {
            broadcast(WsMessage("monster_update", monster))
        }
    }

    private fun spawnMonster() {
        val rand = ThreadLocalRandom.current()
        val x = rand.nextDouble(-mapLimit.toDouble(), mapLimit.toDouble()).toFloat()
        val z = rand.nextDouble(-mapLimit.toDouble(), mapLimit.toDouble()).toFloat()
        val m = Monster(
            id = monsterIdGen.getAndIncrement(),
            name = "Zombie",
            hp = 40,
            x = x,
            z = z
        )
        monsters[m.id] = m
        moveStates[m.id] = newMoveState()
        log.info("Spawned ${m.name} id=${m.id} at ($x,$z)")
        broadcast(WsMessage("monster_spawn", m))
    }
}
