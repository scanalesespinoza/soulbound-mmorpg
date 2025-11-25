package dev.soulbound.server.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.soulbound.server.application.GameEvent
import dev.soulbound.server.application.GameService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

data class WsMessage(val type: String, val data: Any?)

@Component
class GameWebSocketHandler(
    private val mapper: ObjectMapper,
    private val gameService: GameService
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
        log.info("WS connected: ${'$'}{session.id}")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        sessions.remove(session.id)
        gameService.disconnect(session.id)
        log.info("WS disconnected: ${'$'}{session.id}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val envelope: Map<String, Any?> = mapper.readValue(message.payload)
            when (envelope["type"]) {
                "join" -> {
                    val name = envelope["data"] as? String ?: "Player"
                    handleJoin(session, name)
                }
                "attack" -> {
                    val data = envelope["data"] as? Map<*, *>
                    val monsterId = (data?.get("monsterId") as? Number)?.toInt()
                    val fx = (data?.get("fx") as? Number)?.toFloat() ?: 0f
                    val fz = (data?.get("fz") as? Number)?.toFloat() ?: 1f
                    val events = gameService.attack(session.id, fx, fz, monsterId)
                    broadcastEvents(events)
                }
                "pos" -> {
                    val data = envelope["data"] as? Map<*, *> ?: return
                    val px = (data["x"] as? Number)?.toFloat() ?: return
                    val pz = (data["z"] as? Number)?.toFloat() ?: return
                    gameService.updatePosition(session.id, px, pz)
                }
                "respawn" -> {
                    broadcastEvents(gameService.respawn(session.id))
                }
            }
        } catch (ex: Exception) {
            log.error("Failed to handle message", ex)
        }
    }

    @Scheduled(fixedRate = 1000)
    fun spawnTick() {
        broadcastEvents(gameService.spawnTick())
    }

    @Scheduled(fixedRate = 200)
    fun worldTick() {
        broadcastEvents(gameService.worldTick())
    }

    private fun handleJoin(session: WebSocketSession, name: String) {
        val result = gameService.join(session.id, name)
        send(session, WsMessage("join_ack", result.player))
        result.monsters.forEach { send(session, WsMessage("monster_spawn", it)) }
    }

    private fun broadcastEvents(events: List<GameEvent>) {
        events.forEach { broadcast(toWsMessage(it)) }
    }

    private fun toWsMessage(event: GameEvent): WsMessage {
        return when (event) {
            is GameEvent.MonsterSpawn -> WsMessage("monster_spawn", event.monster)
            is GameEvent.MonsterMove -> WsMessage("monster_move", event.monster)
            is GameEvent.MonsterUpdate -> WsMessage("monster_update", event.monster)
            is GameEvent.MonsterKilled -> WsMessage("monster_killed", mapOf("id" to event.id, "by" to event.byPlayerId))
            is GameEvent.PlayerUpdate -> WsMessage("player_update", event.player)
            is GameEvent.PlayerDead -> WsMessage("player_dead", event.player)
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
}
