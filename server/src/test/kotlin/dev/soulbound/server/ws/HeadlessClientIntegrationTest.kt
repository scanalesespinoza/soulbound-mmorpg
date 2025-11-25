package dev.soulbound.server.ws

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class WsMessage(val type: String, val data: Any?)

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HeadlessClientIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: WebSocketClient
    private val mapper = jacksonObjectMapper()
    private val inbox: BlockingQueue<WsMessage> = LinkedBlockingQueue()

    @BeforeEach
    fun setup() {
        val uri = URI.create("ws://localhost:$port/ws")
        client = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send(mapper.writeValueAsString(mapOf("type" to "join", "data" to "TestPlayer")))
            }

            override fun onMessage(message: String?) {
                if (message == null) return
                try {
                    val map: Map<String, Any?> = mapper.readValue(message)
                    val type = map["type"] as? String ?: return
                    val data = map["data"]
                    inbox.offer(WsMessage(type, data))
                } catch (_: Exception) {
                    // ignore parse errors for the test
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }
        client.connectBlocking(3, TimeUnit.SECONDS)
    }

    @AfterEach
    fun tearDown() {
        if (client.isOpen) client.close()
    }

    @Test
    fun `join and respawn flow emits events`() {
        val join = inbox.poll(3, TimeUnit.SECONDS)
        assertThat(join?.type).isEqualTo("join_ack")

        // request respawn explicitly (should work even if alive)
        client.send(mapper.writeValueAsString(mapOf("type" to "respawn", "data" to emptyMap<String, Any>())))
        val respawn = waitForType("player_respawned")
        assertThat(respawn?.type).isEqualTo("player_respawned")
    }

    private fun waitForType(type: String): WsMessage? {
        val deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline) {
            val msg = inbox.poll(500, TimeUnit.MILLISECONDS) ?: continue
            if (msg.type == type) return msg
        }
        return null
    }
}
