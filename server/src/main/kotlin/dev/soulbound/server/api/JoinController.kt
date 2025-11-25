package dev.soulbound.server.api

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class JoinRequest(val name: String)

@RestController
@RequestMapping("/api")
class JoinController {
    @PostMapping("/join")
    fun join(@RequestBody req: JoinRequest) = mapOf("status" to "ok", "name" to req.name)
}
