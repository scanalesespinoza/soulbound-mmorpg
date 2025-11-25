package dev.soulbound.client.net

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.soulbound.client.WsMessage
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue

class NetworkClient(private val serverUri: URI) {
    private val mapper = jacksonObjectMapper()
    private lateinit var wsClient: WebSocketClient
    val inbox: ConcurrentLinkedQueue<WsMessage> = ConcurrentLinkedQueue()

    fun connect(playerName: String) {
        wsClient = object : WebSocketClient(serverUri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send(mapper.writeValueAsString(mapOf("type" to "join", "data" to playerName)))
            }

            override fun onMessage(message: String?) {
                if (message == null) return
                try {
                    val map: Map<String, Any?> = mapper.readValue(message)
                    val type = map["type"] as? String ?: return
                    val data = map["data"]
                    inbox.add(WsMessage(type, data))
                } catch (e: Exception) {
                    println("Failed to parse: $message")
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) { ex?.printStackTrace() }
        }
        wsClient.connect()
    }

    fun disconnect() {
        if (this::wsClient.isInitialized && wsClient.isOpen) {
            wsClient.close()
        }
    }

    fun sendPosition(x: Float, z: Float) {
        if (!isOpen()) return
        val payload = mapOf("type" to "pos", "data" to mapOf("x" to x, "z" to z))
        wsClient.send(mapper.writeValueAsString(payload))
    }

    fun sendAttack(fx: Float, fz: Float, monsterId: Int? = null) {
        if (!isOpen()) return
        val data = mutableMapOf<String, Any>("fx" to fx, "fz" to fz)
        if (monsterId != null) data["monsterId"] = monsterId
        val payload = mapOf("type" to "attack", "data" to data)
        wsClient.send(mapper.writeValueAsString(payload))
    }

    fun sendRespawn() {
        if (!isOpen()) return
        val payload = mapOf("type" to "respawn", "data" to emptyMap<String, Any>())
        wsClient.send(mapper.writeValueAsString(payload))
    }

    fun isOpen(): Boolean = this::wsClient.isInitialized && wsClient.isOpen
}
