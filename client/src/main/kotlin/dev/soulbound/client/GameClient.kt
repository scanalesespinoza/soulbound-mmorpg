package dev.soulbound.client

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jme3.app.SimpleApplication
import com.jme3.font.BitmapText
import com.jme3.collision.CollisionResults
import com.jme3.input.ChaseCamera
import com.jme3.input.KeyInput
import com.jme3.input.MouseInput
import com.jme3.input.controls.ActionListener
import com.jme3.input.controls.KeyTrigger
import com.jme3.input.controls.MouseButtonTrigger
import com.jme3.light.DirectionalLight
import com.jme3.material.Material
import com.jme3.material.RenderState
import com.jme3.math.ColorRGBA
import com.jme3.math.Vector3f
import com.jme3.math.Ray
import com.jme3.math.FastMath
import com.jme3.math.Quaternion
import com.jme3.scene.Geometry
import com.jme3.scene.Node
import com.jme3.scene.Spatial
import com.jme3.scene.shape.Box
import com.jme3.scene.shape.Line
import com.jme3.scene.shape.Sphere
import com.jme3.scene.shape.Quad
import com.jme3.system.AppSettings
import com.jme3.audio.AudioNode
import com.jme3.audio.AudioSource
import kotlin.math.max
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue

data class WsMessage(val type: String, val data: Any?)
data class MonsterPayload(val id: Int, val name: String, val hp: Int, val x: Float, val z: Float)
data class PlayerPayload(
    val id: String?,
    val name: String?,
    val level: Int,
    val xp: Int,
    val x: Float? = null,
    val z: Float? = null,
    val hp: Int? = null,
    val spawnX: Float? = null,
    val spawnZ: Float? = null
)
data class MonsterState(
    val id: Int,
    var hp: Int,
    val name: String,
    val geom: Geometry,
    var targetX: Float,
    var targetZ: Float
)
data class Notification(var text: String, var ttl: Float)

class GameClientApp(private val playerName: String) : SimpleApplication() {
    private val mapper = jacksonObjectMapper()
    private val inbox = ConcurrentLinkedQueue<WsMessage>()
    private lateinit var statusText: BitmapText
    private lateinit var wsClient: WebSocketClient
    private lateinit var chaseCam: ChaseCamera
    private lateinit var playerNode: Node
    private lateinit var playerBody: Geometry
    private lateinit var playerHead: Geometry
    private lateinit var playerHeadNode: Node
    private lateinit var swordNode: Node
    private lateinit var swordGeom: Geometry
    private lateinit var floorGeom: Geometry
    private lateinit var attackViz: Node
    private var walkTime = 0f
    private var playerVelocityY = 0f
    private val gravity = -9.8f
    private val groundY = 0.1f
    private val moveSpeed = 8f
    private val clickArriveThreshold = 0.2f
    private val floorLimit = 23f
    private val maxHp = 100f
    private var hp = maxHp
    private var clickMarker: Geometry? = null
    private var pathLine: Geometry? = null
    private var moveForward = false
    private var moveBackward = false
    private var moveLeft = false
    private var moveRight = false
    private var moveTarget: Vector3f? = null
    private var facingDir = Vector3f(0f, 0f, 1f)
    private var lastSentPos = Vector3f.ZERO.clone()
    private var timeSincePosSent = 0f
    private var attackTimer = 0f
    private val attackDuration = 0.4f
    private val swordSwingDuration = 0.3f
    private val attackCooldownDuration = 0.1f
    private var attackCooldownTimer = 0f
    private val swordDamageValue = 10
    private var spawnX: Float = 0f
    private var spawnZ: Float = 0f
    private var hitFlashTimer = 0f
    private var walkSound: AudioNode? = null
    private var attackSound: AudioNode? = null
    private var hurtSound: AudioNode? = null
    private var zombieGroan: AudioNode? = null
    private var zombieGroanTimer = 0f
    private lateinit var playerPosText: BitmapText
    private lateinit var swordDmgText: BitmapText
    private lateinit var healthBg: Geometry
    private lateinit var healthFill: Geometry
    private lateinit var notificationsText: BitmapText
    private val notifications = mutableListOf<Notification>()
    private val monsters = mutableMapOf<Int, MonsterState>()
    private val monsterRoot = Node("monsters")
    private var level = 1
    private var xp = 0

    private fun setupAudio() {
        walkSound = loadSound("Sound/Effects/Footsteps.ogg", looping = true, volume = 0.4f)
        attackSound = loadSound("Sound/Effects/BladeSwipe.ogg", looping = false, volume = 0.8f)
        hurtSound = loadSound("Sound/Effects/Hit_Hurt.ogg", looping = false, volume = 0.8f)
        zombieGroan = loadSound("Sound/Effects/Zombie.ogg", looping = false, volume = 0.5f)
    }

    private fun loadSound(path: String, looping: Boolean, volume: Float): AudioNode? {
        return try {
            AudioNode(assetManager, path, false).apply {
                isPositional = false
                this.volume = volume
                isLooping = looping
                guiNode.attachChild(this)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun playLoopingSounds(tpf: Float) {
        val isMoving = moveForward || moveBackward || moveLeft || moveRight || moveTarget != null
        val ws = walkSound
        if (isMoving) {
            if (ws != null && ws.status != AudioSource.Status.Playing) ws.play()
        } else if (ws != null && ws.status == AudioSource.Status.Playing) {
            ws.stop()
        }

        zombieGroanTimer -= tpf
        if (zombieGroanTimer <= 0f && monsters.isNotEmpty()) {
            zombieGroanTimer = 2.5f + Math.random().toFloat() * 2f
            zombieGroan?.playInstance()
        }
    }
    override fun simpleInitApp() {
        flyCam.isEnabled = false
        guiNode.detachAllChildren()
        setupUi()
        setupScene()
        initKeys()
        rootNode.attachChild(monsterRoot)
        viewPort.backgroundColor = ColorRGBA(0.55f, 0.75f, 0.95f, 1f)
        spawnClouds()
        setupAudio()

        chaseCam = ChaseCamera(cam, playerNode, inputManager)
        chaseCam.setDefaultDistance(8f)
        chaseCam.setMinDistance(4f)
        chaseCam.setMaxDistance(15f)
        chaseCam.setLookAtOffset(Vector3f(0f, 1.5f, 0f))
        chaseCam.setTrailingEnabled(true)

        val uri = URI.create("ws://localhost:8080/ws")
        wsClient = object : WebSocketClient(uri) {
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

    override fun simpleUpdate(tpf: Float) {
        while (true) {
            val msg = inbox.poll() ?: break
            when (msg.type) {
                "join_ack" -> {
                    val data = msg.data ?: continue
                    val player = mapper.convertValue<PlayerPayload>(data)
                    level = player.level
                    xp = player.xp
                    player.x?.let { playerNode.localTranslation = playerNode.localTranslation.setX(it) }
                    player.z?.let { playerNode.localTranslation = playerNode.localTranslation.setZ(it) }
                    player.hp?.let { hp = it.toFloat() }
                    player.spawnX?.let { spawnX = it }
                    player.spawnZ?.let { spawnZ = it }
                }
                "monster_spawn" -> {
                    val data = msg.data ?: continue
                    val payload = mapper.convertValue<MonsterPayload>(data)
                    addMonster(payload)
                    notifications.add(Notification("Zombie ${payload.id} en (%.1f, %.1f)".format(payload.x, payload.z), 4f))
                }
                "monster_update" -> {
                    val data = msg.data ?: continue
                    updateMonster(mapper.convertValue<MonsterPayload>(data))
                }
                "monster_move" -> {
                    val data = msg.data ?: continue
                    updateMonster(mapper.convertValue<MonsterPayload>(data))
                }
                "monster_killed" -> {
                    val data = msg.data ?: continue
                    val dataMap = mapper.convertValue<Map<String, Any?>>(data)
                    val id = (dataMap["id"] as? Number)?.toInt()
                    if (id != null) removeMonster(id)
                }
                "player_update" -> {
            val data = msg.data ?: continue
            val p = mapper.convertValue<Map<String, Any?>>(data)
            val oldHp = hp
            level = (p["level"] as? Int) ?: level
            xp = (p["xp"] as? Int) ?: xp
            hp = (p["hp"] as? Number)?.toFloat() ?: hp
            spawnX = (p["spawnX"] as? Number)?.toFloat() ?: spawnX
            spawnZ = (p["spawnZ"] as? Number)?.toFloat() ?: spawnZ
            if (hp < oldHp) hitFlashTimer = 0.4f
        }
    }
        }
        attackCooldownTimer = max(0f, attackCooldownTimer - tpf)
        updateMovement(tpf)
        applyGravity(tpf)
        playLoopingSounds(tpf)
        smoothMonsters(tpf)
        sendPositionIfNeeded(tpf)
        val monstersLine = monsters.values.joinToString(", ") { "${it.name}#${it.id}(hp=${it.hp})" }
        statusText.text = "Jugador: $playerName  Nivel: $level  XP: $xp\nMonstruos: $monstersLine"
        updateOverlays(tpf)
    }

    private fun updateMovement(tpf: Float) {
        var dir = Vector3f.ZERO.clone()
        val viewDir = cam.direction.clone().setY(0f)
        if (viewDir.lengthSquared() > 0f) viewDir.normalizeLocal()
        facingDir = viewDir.clone()
        val leftDir = cam.left.clone().setY(0f)
        if (leftDir.lengthSquared() > 0f) leftDir.normalizeLocal()

        if (moveForward) dir = dir.add(viewDir)
        if (moveBackward) dir = dir.subtract(viewDir)
        if (moveLeft) dir = dir.add(leftDir)
        if (moveRight) dir = dir.subtract(leftDir)

        val manualInput = dir.lengthSquared() > 0f
        if (manualInput) {
            moveTarget = null
            clearPathVisual()
        }

        var moveDirUsed: Vector3f? = null
        if (manualInput) {
            dir.normalizeLocal()
            val displacement = dir.mult(moveSpeed * tpf)
            val newPos = clampToFloor(playerNode.localTranslation.add(displacement))
            playerNode.localTranslation = newPos
            moveDirUsed = dir
        } else {
            moveTarget?.let { target ->
                val toTarget = target.subtract(playerNode.localTranslation)
                val toTargetXZ = Vector3f(toTarget.x, 0f, toTarget.z)
                val dist = toTargetXZ.length()
                if (dist < clickArriveThreshold) {
                    moveTarget = null
                    clearPathVisual()
                } else {
                    val step = toTargetXZ.normalize().mult(moveSpeed * tpf)
                    val newPos = clampToFloor(playerNode.localTranslation.add(step))
                    playerNode.localTranslation = newPos
                    updatePathVisual()
                    moveDirUsed = toTargetXZ.normalize()
                }
            }
        }
        playerNode.lookAt(playerNode.localTranslation.add(facingDir), Vector3f.UNIT_Y)
        applyWalkAnimation(manualInput || moveTarget != null, tpf)
        updateSwordAnimation(manualInput || moveTarget != null, tpf)
    }

    private fun setupUi() {
        val font = assetManager.loadFont("Interface/Fonts/Default.fnt")
        statusText = BitmapText(font, false)
        statusText.size = 24f
        statusText.color = ColorRGBA.White
        statusText.setLocalTranslation(10f, cam.height.toFloat() - 10f, 0f)
        guiNode.attachChild(statusText)

        notificationsText = BitmapText(font, false).apply {
            size = 18f
            color = ColorRGBA.Yellow
            setLocalTranslation(10f, cam.height.toFloat() - 70f, 0f)
        }
        guiNode.attachChild(notificationsText)

        playerPosText = BitmapText(font, false).apply {
            size = 16f
            color = ColorRGBA.White
        }
        guiNode.attachChild(playerPosText)

        swordDmgText = BitmapText(font, false).apply {
            size = 14f
            color = ColorRGBA(1f, 0.8f, 0.4f, 1f)
        }
        guiNode.attachChild(swordDmgText)

        healthBg = Geometry("hp-bg", Quad(60f, 8f)).apply {
            material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
                it.setColor("Color", ColorRGBA(0.2f, 0.2f, 0.2f, 0.8f))
            }
        }
        healthFill = Geometry("hp-fill", Quad(60f, 8f)).apply {
            material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
                it.setColor("Color", ColorRGBA(0.1f, 0.8f, 0.1f, 0.9f))
            }
        }
        guiNode.attachChild(healthBg)
        guiNode.attachChild(healthFill)
    }

    private fun setupScene() {
        val floor = Box(25f, 0.1f, 25f)
        floorGeom = Geometry("floor", floor)
        val floorMat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
        floorMat.setColor("Color", ColorRGBA(0.25f, 0.55f, 0.25f, 1f))
        floorGeom.material = floorMat
        floorGeom.localTranslation = Vector3f.ZERO
        rootNode.attachChild(floorGeom)
        addFloorGrid()

        playerNode = Node("player")
        playerBody = Geometry("player-body", Box(0.5f, 0.8f, 0.5f))
        val bodyMat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
        bodyMat.setColor("Color", ColorRGBA.Blue)
        playerBody.material = bodyMat
        playerBody.localTranslation = Vector3f(0f, 0.8f, 0f)

        playerHeadNode = Node("player-head-node")
        playerHead = Geometry("player-head", Box(0.35f, 0.35f, 0.35f))
        val headMat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
        headMat.setColor("Color", ColorRGBA(0.4f, 0.6f, 1f, 1f))
        playerHead.material = headMat
        playerHead.localTranslation = Vector3f.ZERO
        playerHeadNode.attachChild(playerHead)
        addFaceFeatures(playerHeadNode)
        playerHeadNode.localTranslation = Vector3f(0f, 1.7f, 0f)

        swordNode = Node("sword-node")
        swordGeom = Geometry("sword", Box(0.1f, 0.6f, 0.05f)).apply {
            material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
                it.setColor("Color", ColorRGBA(0.8f, 0.8f, 0.9f, 1f))
            }
            localTranslation = Vector3f(0f, 0.6f, 0f)
        }
        val hilt = Geometry("sword-hilt", Box(0.15f, 0.1f, 0.05f)).apply {
            material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
                it.setColor("Color", ColorRGBA(0.4f, 0.25f, 0.15f, 1f))
            }
            localTranslation = Vector3f(0f, 0f, 0f)
        }
        swordNode.attachChild(swordGeom)
        swordNode.attachChild(hilt)
        swordNode.localTranslation = Vector3f(0.25f, 1.0f, 0.6f)
        swordNode.localRotation = Quaternion().fromAngles(0f, 0f, -FastMath.QUARTER_PI)

        playerNode.attachChild(playerBody)
        playerNode.attachChild(playerHeadNode)
        playerNode.attachChild(swordNode)
        playerNode.localTranslation = Vector3f(0f, 2f, 0f)
        rootNode.attachChild(playerNode)
        attackViz = buildAttackViz()
        attackViz.cullHint = Spatial.CullHint.Always
        rootNode.attachChild(attackViz)

        val light = DirectionalLight()
        light.color = ColorRGBA.White
        light.direction = Vector3f(-0.5f, -1f, -0.3f).normalizeLocal()
        rootNode.addLight(light)
    }

    private fun initKeys() {
        inputManager.addMapping("attack", KeyTrigger(KeyInput.KEY_SPACE))
        inputManager.addMapping("move_forward", KeyTrigger(KeyInput.KEY_W))
        inputManager.addMapping("move_backward", KeyTrigger(KeyInput.KEY_S))
        inputManager.addMapping("move_left", KeyTrigger(KeyInput.KEY_A))
        inputManager.addMapping("move_right", KeyTrigger(KeyInput.KEY_D))
        inputManager.addMapping("move_click", MouseButtonTrigger(MouseInput.BUTTON_LEFT))
        inputManager.addListener(actionListener, "attack", "move_forward", "move_backward", "move_left", "move_right", "move_click")
    }

    private fun applyGravity(tpf: Float) {
        val pos = playerNode.localTranslation.clone()
        playerVelocityY += gravity * tpf
        pos.y += playerVelocityY * tpf
        if (pos.y < groundY) {
            pos.y = groundY
            playerVelocityY = 0f
        }
        playerNode.localTranslation = clampToFloor(pos)
    }

    private fun sendPositionIfNeeded(tpf: Float) {
        timeSincePosSent += tpf
        val pos = playerNode.worldTranslation
        val moved = pos.distance(lastSentPos) > 0.1f
        if (moved || timeSincePosSent > 0.4f) {
            if (wsClient.isOpen) {
                val payload = mapOf("type" to "pos", "data" to mapOf("x" to pos.x, "z" to pos.z))
                wsClient.send(mapper.writeValueAsString(payload))
            }
            lastSentPos = pos.clone()
            timeSincePosSent = 0f
        }
    }

    private fun applyWalkAnimation(isMoving: Boolean, tpf: Float) {
        if (isMoving) {
            walkTime += tpf * 8f
            val bob = kotlin.math.sin(walkTime.toDouble()).toFloat() * 0.1f
            playerBody.localTranslation = Vector3f(0f, 0.8f + bob * 0.5f, 0f)
            playerHeadNode.localTranslation = Vector3f(0f, 1.7f + bob, 0f)
        } else {
            walkTime = 0f
            playerBody.localTranslation = Vector3f(0f, 0.8f, 0f)
            playerHeadNode.localTranslation = Vector3f(0f, 1.7f, 0f)
        }
    }

    private fun updateSwordAnimation(isMoving: Boolean, tpf: Float) {
        val baseRotation = Quaternion().fromAngles(0f, 0f, -FastMath.QUARTER_PI)
        if (attackTimer > 0f) {
            attackTimer -= tpf
            val progress = 1f - (attackTimer / swordSwingDuration).coerceIn(0f, 1f)
            val angle = FastMath.sin(progress * FastMath.TWO_PI) * -FastMath.HALF_PI
            val swing = Quaternion().fromAngles(0f, angle, 0f)
            swordNode.localRotation = swing
            updateAttackViz(true)
            return
        }
        updateAttackViz(false)
        if (isMoving) {
            val swing = FastMath.sin(walkTime * 1.2f) * 0.4f
            val walkRot = Quaternion().fromAngles(0f, 0f, -FastMath.QUARTER_PI + swing)
            swordNode.localRotation = walkRot
        } else {
            swordNode.localRotation = baseRotation
        }
    }

    private fun clampToFloor(pos: Vector3f): Vector3f {
        val clampedX = pos.x.coerceIn(-floorLimit, floorLimit)
        val clampedZ = pos.z.coerceIn(-floorLimit, floorLimit)
        return Vector3f(clampedX, pos.y, clampedZ)
    }

    private fun updatePathVisual() {
        val target = moveTarget ?: return
        if (clickMarker == null) {
            clickMarker = Geometry("click-marker", Sphere(8, 8, 0.25f)).apply {
                material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
                    it.setColor("Color", ColorRGBA.Yellow)
                }
            }
            rootNode.attachChild(clickMarker)
        }
        clickMarker?.localTranslation = target.add(0f, 0.3f, 0f)

        val lineMesh = Line(playerNode.localTranslation.clone(), target)
        if (pathLine == null) {
            pathLine = Geometry("path-line", lineMesh).apply {
                material = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
                    it.setColor("Color", ColorRGBA(1f, 0.9f, 0.3f, 1f))
                }
            }
            rootNode.attachChild(pathLine)
        } else {
            pathLine?.mesh = lineMesh
            pathLine?.updateModelBound()
        }
    }

    private fun clearPathVisual() {
        clickMarker?.let { rootNode.detachChild(it) }
        pathLine?.let { rootNode.detachChild(it) }
        clickMarker = null
        pathLine = null
    }

    private fun spawnClouds() {
        val clouds = Node("clouds")
        val cloudMat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
            it.setColor("Color", ColorRGBA(1f, 1f, 1f, 0.7f))
        }
        for (i in 0 until 6) {
            val quad = Quad(3f + i * 0.3f, 1.2f + (i % 2) * 0.4f)
            val geom = Geometry("cloud-$i", quad)
            geom.material = cloudMat
            geom.localTranslation = Vector3f(-20f + i * 6f, 8f + (i % 3), -12f + (i % 4) * 6f)
            clouds.attachChild(geom)
        }
        rootNode.attachChild(clouds)
    }

    private fun addFloorGrid() {
        val grid = Node("floor-grid")
        val gridMat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
            it.setColor("Color", ColorRGBA(0f, 0f, 0f, 0.35f))
        }
        val step = 2f
        val half = 25
        for (i in -half..half) {
            val x = i * step
            val lineX = Geometry("grid-x-$i", Line(Vector3f(x, groundY + 0.01f, -half * step), Vector3f(x, groundY + 0.01f, half * step)))
            lineX.material = gridMat
            grid.attachChild(lineX)

            val z = i * step
            val lineZ = Geometry("grid-z-$i", Line(Vector3f(-half * step, groundY + 0.01f, z), Vector3f(half * step, groundY + 0.01f, z)))
            lineZ.material = gridMat
            grid.attachChild(lineZ)
        }
        rootNode.attachChild(grid)
    }

    private fun addFaceFeatures(parent: Node) {
        val eyeMat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
            it.setColor("Color", ColorRGBA.White)
        }
        val pupilMat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
            it.setColor("Color", ColorRGBA(0.1f, 0.1f, 0.1f, 1f))
        }
        val mouthMat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
            it.setColor("Color", ColorRGBA(1f, 0.4f, 0.4f, 1f))
        }

        fun eye(name: String, x: Float): Node {
            val eyeNode = Node(name)
            val white = Geometry("${name}-white", Box(0.1f, 0.08f, 0.01f))
            white.material = eyeMat
            white.localTranslation = Vector3f.ZERO

            val pupil = Geometry("${name}-pupil", Box(0.04f, 0.04f, 0.011f))
            pupil.material = pupilMat
            pupil.localTranslation = Vector3f(0f, 0f, 0.005f)

            eyeNode.attachChild(white)
            eyeNode.attachChild(pupil)
            eyeNode.localTranslation = Vector3f(x, 0.05f, 0.36f)
            return eyeNode
        }

        val leftEye = eye("eye-left", -0.12f)
        val rightEye = eye("eye-right", 0.12f)
        val mouth = Geometry("mouth", Box(0.18f, 0.03f, 0.01f)).apply {
            material = mouthMat
            localTranslation = Vector3f(0f, -0.12f, 0.355f)
        }

        parent.attachChild(leftEye)
        parent.attachChild(rightEye)
        parent.attachChild(mouth)
    }

    private fun pickGroundTarget(): Vector3f? {
        val results = CollisionResults()
        val click2d = inputManager.cursorPosition.clone()
        val origin = cam.getWorldCoordinates(click2d, 0f)
        val dest = cam.getWorldCoordinates(click2d, 1f)
        val direction = dest.subtract(origin).normalizeLocal()
        val ray = Ray(origin, direction)
        floorGeom.collideWith(ray, results)
        val hit = results.closestCollision ?: return null
        val point = hit.contactPoint
        val clamped = clampToFloor(Vector3f(point.x, groundY, point.z))
        return Vector3f(clamped.x, groundY, clamped.z)
    }

    private fun updateOverlays(tpf: Float) {
        // Notifications
        notifications.forEach { it.ttl -= tpf }
        notifications.removeIf { it.ttl <= 0f }
        notificationsText.text = notifications.joinToString("\n") { it.text }

        val headWorld = playerHeadNode.worldTranslation.clone().addLocal(0f, 0.5f, 0f)
        val headScreen = cam.getScreenCoordinates(headWorld)
        val pos = playerNode.localTranslation
        playerPosText.text = "x=%.1f z=%.1f".format(pos.x, pos.z)
        playerPosText.localTranslation = Vector3f(headScreen.x - playerPosText.lineWidth / 2, headScreen.y + 25f, 0f)

        val swordWorld = swordNode.worldTranslation.clone().addLocal(0f, 0.3f, 0f)
        val swordScreen = cam.getScreenCoordinates(swordWorld)
        swordDmgText.text = "DMG $swordDamageValue"
        swordDmgText.localTranslation = Vector3f(swordScreen.x - swordDmgText.lineWidth / 2, swordScreen.y + 15f, 0f)

        val feetWorld = playerNode.worldTranslation.clone().addLocal(0f, 0.2f, 0f)
        val feetScreen = cam.getScreenCoordinates(feetWorld)
        val barX = feetScreen.x - 30f
        val barY = feetScreen.y - 35f
        healthBg.localTranslation = Vector3f(barX, barY, 0f)
        val hpPct = (hp / maxHp).coerceIn(0f, 1f)
        healthFill.localTranslation = Vector3f(barX, barY, 0f)
        healthFill.localScale = Vector3f(hpPct, 1f, 1f)

        // Damage flash
        if (hitFlashTimer > 0f) {
            hitFlashTimer -= tpf
            val flash = if (((hitFlashTimer * 20f) % 2f) > 1f) 1f else 0f
            val bodyMat = playerBody.material.clone()
            bodyMat.setColor("Color", ColorRGBA(1f, 0.2f + 0.8f * flash, 0.2f + 0.8f * flash, 1f))
            playerBody.material = bodyMat
            val headMat = playerHead.material.clone()
            headMat.setColor("Color", ColorRGBA(1f, 0.4f + 0.6f * flash, 0.4f + 0.6f * flash, 1f))
            playerHead.material = headMat
            hurtSound?.playInstance()
        }
    }

    private val actionListener = ActionListener { name, isPressed, _ ->
        when (name) {
            "attack" -> if (isPressed) {
                if (attackCooldownTimer > 0f) return@ActionListener
                val facing = facingDir.normalize()
                if (wsClient.isOpen) {
                    val payload = mapOf("type" to "attack", "data" to mapOf("fx" to facing.x, "fz" to facing.z))
                    wsClient.send(mapper.writeValueAsString(payload))
                }
                attackTimer = swordSwingDuration
                attackCooldownTimer = swordSwingDuration + attackCooldownDuration
            }
            "move_forward" -> moveForward = isPressed
            "move_backward" -> moveBackward = isPressed
            "move_left" -> moveLeft = isPressed
            "move_right" -> moveRight = isPressed
            "move_click" -> if (!isPressed) {
                pickGroundTarget()?.let {
                    moveTarget = it
                    updatePathVisual()
                }
            }
        }
    }

    private fun addMonster(payload: MonsterPayload) {
        val geom = Geometry("monster-${payload.id}", Box(0.6f, 0.6f, 0.6f))
        val mat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
        mat.setColor("Color", ColorRGBA.Red)
        geom.material = mat
        geom.localTranslation = Vector3f(payload.x, 0.6f, payload.z)
        monsterRoot.attachChild(geom)
        monsters[payload.id] = MonsterState(payload.id, payload.hp, payload.name, geom, payload.x, payload.z)
    }

    private fun updateMonster(payload: MonsterPayload) {
        monsters[payload.id]?.let {
            it.hp = payload.hp
            updateMonsterColor(it)
            it.targetX = payload.x
            it.targetZ = payload.z
        }
    }

    private fun removeMonster(id: Int) {
        monsters[id]?.let {
            monsterRoot.detachChild(it.geom)
        }
        monsters.remove(id)
    }

    private fun updateMonsterColor(state: MonsterState) {
        val hpRatio = state.hp.coerceAtLeast(0) / 30f
        val mat = state.geom.material.clone()
        mat.setColor("Color", ColorRGBA(1f, hpRatio, hpRatio, 1f))
        state.geom.material = mat
    }

    private fun smoothMonsters(tpf: Float) {
        val lerpSpeed = 6f
        monsters.values.forEach { m ->
            val geom = m.geom
            val current = geom.localTranslation
            val target = Vector3f(m.targetX, current.y, m.targetZ)
            val delta = target.subtract(current)
            val dist = delta.length()
            if (dist > 0.001f) {
                val step = lerpSpeed * tpf
                val newPos = if (step >= dist) target else current.add(delta.normalize().mult(step))
                geom.localTranslation = newPos
            }
        }
    }

    private fun buildAttackViz(): Node {
        val node = Node("attack-viz")
        val mat = Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md").also {
            it.setColor("Color", ColorRGBA(1f, 0.8f, 0.2f, 0.5f))
            it.additionalRenderState.blendMode = RenderState.BlendMode.Alpha
        }
        val range = 2.2f
        val segments = 12
        for (i in 0..segments) {
            val angle = FastMath.PI * (i.toFloat() / segments - 0.5f) // -90 to 90 deg
            val x = FastMath.cos(angle) * range
            val z = FastMath.sin(angle) * range
            val line = Geometry("attack-line-$i", Line(Vector3f.ZERO, Vector3f(x, 0f, z)))
            line.material = mat
            node.attachChild(line)
        }
        return node
    }

    private fun updateAttackViz(show: Boolean) {
        if (show) {
            attackViz.cullHint = Spatial.CullHint.Inherit
            val pos = playerNode.worldTranslation.clone().addLocal(0f, 0.2f, 0f)
            attackViz.localTranslation = pos
            val rot = Quaternion().fromAngles(0f, FastMath.atan2(facingDir.x, facingDir.z), 0f)
            attackViz.localRotation = rot
        } else {
            attackViz.cullHint = Spatial.CullHint.Always
        }
    }
}

fun main(args: Array<String>) {
    val name = if (args.isNotEmpty()) args[0] else "Player"
    val app = GameClientApp(name)
    val settings = AppSettings(true)
    settings.width = 1280
    settings.height = 960
    settings.title = "Soulbound MMORPG"
    settings.isVSync = true
    app.setShowSettings(false)
    app.setSettings(settings)
    app.start()
}
