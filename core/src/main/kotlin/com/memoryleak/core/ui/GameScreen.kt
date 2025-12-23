package com.memoryleak.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.memoryleak.core.MemoryLeakApp
import com.memoryleak.shared.model.*
import com.memoryleak.shared.network.CommandPacket
import com.memoryleak.shared.network.CommandType

/**
 * Main game screen with battlefield rendering.
 */
class GameScreen(private val app: MemoryLeakApp) : Screen {
    
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var camera: OrthographicCamera
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var labelFont: BitmapFont
    
    private val uiMatrix = Matrix4()
    
    // Selection state
    private var selectedEntityId: String? = null
    private var selectedCardId: String? = null
    private var isPlacingCard: Boolean = false
    private var isSelectingTarget: Boolean = false
    private var pendingCardType: CardType? = null
    
    // Colors for player identification
    private val myColor = Color(0.2f, 0.8f, 0.2f, 1f)  // Green
    private val enemyColor = Color(0.8f, 0.2f, 0.2f, 1f)  // Red
    private val neutralColor = Color(0.8f, 0.8f, 0.2f, 1f)  // Yellow
    
    override fun show() {
        camera = OrthographicCamera(800f, 600f)
        shapeRenderer = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        labelFont = BitmapFont()
        
        font.color = Color.WHITE
        labelFont.data.setScale(0.7f)
        
        // Center camera on map
        camera.position.set(app.mapWidth / 2, app.mapHeight / 2, 0f)
        camera.update()
        
        // Setup network callbacks
        app.networkClient.onGameOver = { winnerId ->
            Gdx.app.postRunnable {
                app.onGameOver(winnerId)
            }
        }
        
        app.networkClient.onOpponentDisconnected = { youWin ->
            Gdx.app.postRunnable {
                app.onOpponentDisconnected(youWin)
            }
        }
        
        setupInput()
    }
    
    private fun setupInput() {
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val network = app.networkClient
                
                // Check if clicking on cards first (screen space)
                val myPlayer = network.players[network.myId]
                if (myPlayer != null && myPlayer.hand.isNotEmpty()) {
                    val cardWidth = 150f
                    val cardHeight = 80f
                    val cardGap = 10f
                    val cardsStartX = (800f - (cardWidth + cardGap) * myPlayer.hand.size) / 2f
                    val cardsY = 10f
                    
                    val uiX = screenX * (800f / Gdx.graphics.width.toFloat())
                    val uiY = (Gdx.graphics.height - screenY) * (600f / Gdx.graphics.height.toFloat())
                    
                    myPlayer.hand.forEachIndexed { index, card ->
                        val cardX = cardsStartX + index * (cardWidth + cardGap)
                        
                        if (uiX >= cardX && uiX <= cardX + cardWidth &&
                            uiY >= cardsY && uiY <= cardsY + cardHeight) {
                            val canAfford = myPlayer.memory >= card.memoryCost && myPlayer.cpu >= card.cpuCost
                            if (canAfford) {
                                selectedCardId = card.id
                                pendingCardType = card.type
                                isPlacingCard = true
                                isSelectingTarget = false
                                selectedEntityId = null
                                println("Selected card: ${card.type}")
                                return true
                            } else {
                                println("Can't afford this card!")
                                return true
                            }
                        }
                    }
                }
                
                // If placing card, deploy it
                if (isPlacingCard && selectedCardId != null) {
                    val worldPos = camera.unproject(Vector3(screenX.toFloat(), screenY.toFloat(), 0f))
                    
                    // Check if we need target selection for this card
                    if (isUnitCard(pendingCardType) && !isSelectingTarget) {
                        // Unit card - need to select target entity after position
                        isSelectingTarget = true
                        network.sendCommand(CommandPacket(
                            commandType = CommandType.PLAY_CARD,
                            cardId = selectedCardId,
                            targetX = worldPos.x,
                            targetY = worldPos.y,
                            targetEntityId = null
                        ))
                        selectedCardId = null
                        isPlacingCard = false
                        return true
                    } else {
                        // Building or direct placement
                        network.sendCommand(CommandPacket(
                            commandType = CommandType.PLAY_CARD,
                            cardId = selectedCardId,
                            targetX = worldPos.x,
                            targetY = worldPos.y
                        ))
                        selectedCardId = null
                        isPlacingCard = false
                        isSelectingTarget = false
                        pendingCardType = null
                        return true
                    }
                }
                
                // Regular entity click
                val worldPos = camera.unproject(Vector3(screenX.toFloat(), screenY.toFloat(), 0f))
                
                val clickedEntity = network.entities.values.find { entity ->
                    val dx = entity.x - worldPos.x
                    val dy = entity.y - worldPos.y
                    (dx * dx + dy * dy) < 25 * 25
                }
                
                if (clickedEntity != null) {
                    if (clickedEntity.ownerId == network.myId) {
                        selectedEntityId = clickedEntity.id
                        selectedCardId = null
                        isPlacingCard = false
                        println("Selected: ${clickedEntity.id}")
                    }
                } else if (selectedEntityId != null) {
                    // Move command
                    println("Move to ${worldPos.x}, ${worldPos.y}")
                    network.sendCommand(CommandPacket(
                        commandType = CommandType.MOVE,
                        entityId = selectedEntityId,
                        targetX = worldPos.x,
                        targetY = worldPos.y
                    ))
                }
                
                return true
            }
            
            override fun keyUp(keycode: Int): Boolean {
                if (keycode == Input.Keys.ESCAPE) {
                    selectedCardId = null
                    isPlacingCard = false
                    isSelectingTarget = false
                    pendingCardType = null
                }
                return true
            }
        }
    }
    
    private fun isUnitCard(cardType: CardType?): Boolean {
        return cardType?.isUnitCard() == true
    }
    
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.12f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        
        handleCameraMovement(delta)
        shapeRenderer.projectionMatrix = camera.combined
        
        // Draw Grid
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(1f, 1f, 1f, 0.1f)
        for (i in 0..app.mapWidth.toInt() step 50) {
            shapeRenderer.line(i.toFloat(), 0f, i.toFloat(), app.mapHeight)
        }
        for (i in 0..app.mapHeight.toInt() step 50) {
            shapeRenderer.line(0f, i.toFloat(), app.mapWidth, i.toFloat())
        }
        // Center line (symmetry indicator)
        shapeRenderer.color = Color(1f, 1f, 1f, 0.3f)
        shapeRenderer.line(app.mapWidth / 2, 0f, app.mapWidth / 2, app.mapHeight)
        shapeRenderer.end()
        
        // Draw Entities
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        
        val network = app.networkClient
        network.entities.values.forEach { entity ->
            val isMyUnit = entity.ownerId == network.myId
            val isNeutral = entity.ownerId == "0"
            val baseColor = when {
                isNeutral -> neutralColor
                isMyUnit -> myColor
                else -> enemyColor
            }
            
            drawEntity(entity, baseColor, isMyUnit)
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
        
        // World-space labels
        batch.projectionMatrix = camera.combined
        batch.begin()
        network.entities.values.forEach { entity ->
            val label = when (entity.type) {
                EntityType.INSTANCE -> "BASE"
                EntityType.FACTORY -> "FACTORY"
                EntityType.RESOURCE_NODE -> when (entity.resourceType) {
                    ResourceType.MEMORY -> "MEM"
                    ResourceType.CPU -> "CPU"
                    null -> "???"
                }
                EntityType.UNIT -> entity.unitType?.name?.take(6) ?: "UNIT"
            }
            labelFont.color = Color.WHITE
            labelFont.draw(batch, label, entity.x - 15, entity.y - 25)
        }
        batch.end()
        
        // Spawn radius visualization
        if (isPlacingCard) {
            val myBase = network.entities.values.find { it.ownerId == network.myId && it.type == EntityType.INSTANCE }
            if (myBase != null) {
                Gdx.gl.glEnable(GL20.GL_BLEND)
                shapeRenderer.projectionMatrix = camera.combined
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                
                val mousePos = camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f))
                val dx = mousePos.x - myBase.x
                val dy = mousePos.y - myBase.y
                val distSq = dx * dx + dy * dy
                val maxDist = 200f
                
                shapeRenderer.color = if (distSq <= maxDist * maxDist) Color.GREEN else Color.RED
                shapeRenderer.circle(myBase.x, myBase.y, maxDist)
                shapeRenderer.end()
                Gdx.gl.glDisable(GL20.GL_BLEND)
            }
        }
        
        // UI Layer
        drawUI()
    }
    
    private fun drawEntity(entity: GameEntity, baseColor: Color, isMyUnit: Boolean) {
        val darkerColor = baseColor.cpy().mul(0.7f)
        val lighterColor = baseColor.cpy().lerp(Color.WHITE, 0.3f)
        
        when (entity.type) {
            EntityType.INSTANCE -> {
                shapeRenderer.color = baseColor
                shapeRenderer.rect(entity.x - 25, entity.y - 25, 50f, 50f)
                shapeRenderer.color = darkerColor
                shapeRenderer.rect(entity.x - 15, entity.y - 15, 30f, 30f)
            }
            EntityType.RESOURCE_NODE -> {
                val nodeColor = if (entity.ownerId == "0") neutralColor else baseColor
                shapeRenderer.color = nodeColor
                shapeRenderer.triangle(
                    entity.x, entity.y + 15,
                    entity.x - 15, entity.y,
                    entity.x + 15, entity.y
                )
                shapeRenderer.triangle(
                    entity.x, entity.y - 15,
                    entity.x - 15, entity.y,
                    entity.x + 15, entity.y
                )
            }
            EntityType.FACTORY -> {
                shapeRenderer.color = baseColor
                shapeRenderer.triangle(
                    entity.x, entity.y + 20,
                    entity.x - 20, entity.y - 15,
                    entity.x + 20, entity.y - 15
                )
            }
            EntityType.UNIT -> {
                drawUnit(entity, baseColor, isMyUnit)
            }
        }
        
        // Selection highlight
        if (entity.id == selectedEntityId) {
            shapeRenderer.end()
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.color = Color.WHITE
            shapeRenderer.circle(entity.x, entity.y, 28f)
            shapeRenderer.end()
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        }
        
        // HP Bar
        if (entity.type != EntityType.RESOURCE_NODE) {
            val hpPct = entity.hp.toFloat() / entity.maxHp.toFloat()
            val barWidth = 40f
            val barHeight = 4f
            val yOffset = 30f
            
            shapeRenderer.color = Color(0.2f, 0f, 0f, 0.8f)
            shapeRenderer.rect(entity.x - barWidth / 2, entity.y + yOffset, barWidth, barHeight)
            
            val hpColor = if (isMyUnit) Color(0.2f, 1f, 0.2f, 0.8f) else Color(1f, 0.2f, 0.2f, 0.8f)
            shapeRenderer.color = hpColor
            shapeRenderer.rect(entity.x - barWidth / 2, entity.y + yOffset, barWidth * hpPct, barHeight)
        }
        
        // Attack line
        if (entity.attackingTargetId != null) {
            val target = app.networkClient.entities[entity.attackingTargetId]
            if (target != null) {
                shapeRenderer.end()
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                shapeRenderer.color = Color(1f, 0.3f, 0.3f, 0.6f)
                shapeRenderer.line(entity.x, entity.y, target.x, target.y)
                shapeRenderer.end()
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            }
        }
    }
    
    private fun drawUnit(entity: GameEntity, baseColor: Color, isMyUnit: Boolean) {
        val unitType = entity.unitType ?: return
        
        // Use base color but vary shape based on unit type category
        shapeRenderer.color = baseColor
        
        when (unitType) {
            // Basic - circles
            UnitType.SCOUT, UnitType.BASIC_PROCESS, UnitType.ALLOCATOR, UnitType.CACHE_RUNNER -> {
                shapeRenderer.circle(entity.x, entity.y, 8f)
            }
            // Tanks - squares
            UnitType.TANK, UnitType.ENCAPSULATION_SHIELD, UnitType.BUFFER, UnitType.TRANSACTION_GUARD -> {
                shapeRenderer.rect(entity.x - 12, entity.y - 12, 24f, 24f)
            }
            // Ranged - triangles
            UnitType.RANGED, UnitType.COROUTINE_ARCHER, UnitType.LAMBDA_SNIPER -> {
                shapeRenderer.triangle(
                    entity.x, entity.y + 10,
                    entity.x - 10, entity.y - 10,
                    entity.x + 10, entity.y - 10
                )
            }
            // Support - diamonds
            UnitType.HEALER, UnitType.RESTFUL_HEALER, UnitType.HIGHER_ORDER_COMMANDER -> {
                shapeRenderer.triangle(entity.x, entity.y + 10, entity.x - 8, entity.y, entity.x + 8, entity.y)
                shapeRenderer.triangle(entity.x, entity.y - 10, entity.x - 8, entity.y, entity.x + 8, entity.y)
            }
            // Special units - mixed shapes with inner detail
            else -> {
                shapeRenderer.circle(entity.x, entity.y, 10f)
                shapeRenderer.color = baseColor.cpy().mul(0.6f)
                shapeRenderer.circle(entity.x, entity.y, 5f)
            }
        }
    }
    
    private fun drawUI() {
        uiMatrix.setToOrtho2D(0f, 0f, 800f, 600f)
        batch.projectionMatrix = uiMatrix
        
        val network = app.networkClient
        
        // Draw card hand
        val myPlayer = network.players[network.myId]
        if (myPlayer != null && myPlayer.hand.isNotEmpty()) {
            val cardWidth = 150f
            val cardHeight = 80f
            val cardGap = 10f
            val cardsStartX = (800f - (cardWidth + cardGap) * myPlayer.hand.size) / 2f
            val cardsY = 10f
            
            Gdx.gl.glEnable(GL20.GL_BLEND)
            shapeRenderer.projectionMatrix = uiMatrix
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            
            myPlayer.hand.forEachIndexed { index, card ->
                val cardX = cardsStartX + index * (cardWidth + cardGap)
                val canAfford = myPlayer.memory >= card.memoryCost && myPlayer.cpu >= card.cpuCost
                val isSelected = selectedCardId == card.id
                val onCooldown = myPlayer.globalCooldown > 0
                
                shapeRenderer.color = when {
                    isSelected -> Color(0.2f, 0.8f, 1f, 0.9f)
                    onCooldown -> Color(0.3f, 0.3f, 0.3f, 0.5f)
                    canAfford -> Color(0.3f, 0.3f, 0.4f, 0.9f)
                    else -> Color(0.2f, 0.2f, 0.2f, 0.6f)
                }
                shapeRenderer.rect(cardX, cardsY, cardWidth, cardHeight)
            }
            shapeRenderer.end()
            
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            myPlayer.hand.forEachIndexed { index, card ->
                val cardX = cardsStartX + index * (cardWidth + cardGap)
                shapeRenderer.color = if (selectedCardId == card.id) Color.WHITE else Color.GRAY
                shapeRenderer.rect(cardX, cardsY, cardWidth, cardHeight)
            }
            shapeRenderer.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)
            
            batch.begin()
            myPlayer.hand.forEachIndexed { index, card ->
                val cardX = cardsStartX + index * (cardWidth + cardGap)
                val cardName = card.type.name.replace("SPAWN_", "").replace("BUILD_", "").take(8)
                
                font.color = Color.WHITE
                font.draw(batch, cardName, cardX + 10, cardsY + cardHeight - 10)
                
                font.color = if (myPlayer.memory >= card.memoryCost) Color.GREEN else Color.RED
                font.draw(batch, "${card.memoryCost}M", cardX + 10, cardsY + 35)
                
                font.color = if (myPlayer.cpu >= card.cpuCost) Color.GREEN else Color.RED
                font.draw(batch, "${card.cpuCost}C", cardX + 80, cardsY + 35)
            }
            batch.end()
        }
        
        // Resource display
        batch.begin()
        var yOffset = 580f
        font.color = Color.WHITE
        font.draw(batch, "FPS: ${Gdx.graphics.framesPerSecond}", 20f, yOffset)
        
        network.players.values.forEach { player ->
            yOffset -= 25f
            val isMe = player.id == network.myId
            font.color = if (isMe) Color.GREEN else Color.RED
            val label = if (isMe) "You" else "Enemy"
            font.draw(batch, "$label: MEM ${player.memory} | CPU ${player.cpu}", 20f, yOffset)
        }
        
        // Instructions
        font.color = Color.LIGHT_GRAY
        font.draw(batch, "Click card -> Click map to deploy | ESC=Cancel | WASD=Camera", 20f, 110f)
        
        batch.end()
    }
    
    private fun handleCameraMovement(delta: Float) {
        val cameraSpeed = 300f * delta
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.translate(-cameraSpeed, 0f)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.translate(cameraSpeed, 0f)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.translate(0f, cameraSpeed)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.translate(0f, -cameraSpeed)
        }
        
        // Clamp camera
        camera.position.x = camera.position.x.coerceIn(0f, app.mapWidth)
        camera.position.y = camera.position.y.coerceIn(0f, app.mapHeight)
        camera.update()
    }
    
    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    
    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        font.dispose()
        labelFont.dispose()
    }
}
