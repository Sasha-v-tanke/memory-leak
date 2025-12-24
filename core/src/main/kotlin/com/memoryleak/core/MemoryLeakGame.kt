package com.memoryleak.core

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3
import com.memoryleak.shared.model.EntityType
import com.memoryleak.shared.network.CommandPacket
import com.memoryleak.shared.network.CommandType
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.memoryleak.shared.model.ResourceType
import com.memoryleak.shared.model.CardType
import com.memoryleak.shared.model.UnitType

class MemoryLeakGame : ApplicationAdapter() {
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var camera: OrthographicCamera
    private lateinit var network: NetworkClient
    // UI
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var labelFont: BitmapFont
    
    // Selection state
    private var selectedEntityId: String? = null
    private var selectedCardId: String? = null
    private var isPlacingCard: Boolean = false
    
    private val uiMatrix = com.badlogic.gdx.math.Matrix4()

    override fun create() {
        camera = OrthographicCamera(800f, 600f)
        shapeRenderer = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        labelFont = BitmapFont()
        
        font.color = Color.WHITE
        labelFont.data.setScale(0.7f)
        
        camera.position.set(400f, 300f, 0f)
        camera.update()

        network = NetworkClient()
        network.connect()
        
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                // Check if clicking on cards first (screen space)
                val myPlayer = network.players[network.myId]
                if (myPlayer != null && myPlayer.hand.isNotEmpty()) {
                    val cardWidth = 150f
                    val cardHeight = 80f
                    val cardGap = 10f
                    val cardsStartX = (800f - (cardWidth + cardGap) * myPlayer.hand.size) / 2f
                    val cardsY = 10f
                    
                    // Convert screen coordinates to UI logical coordinates (800x600)
                    // This handles window resizing and ensures clicks match the drawn UI
                    val uiX = screenX * (800f / Gdx.graphics.width.toFloat())
                    // Invert Y (screen is top-down, UI is bottom-up)
                    val uiY = (Gdx.graphics.height - screenY) * (600f / Gdx.graphics.height.toFloat())
                    
                    myPlayer.hand.forEachIndexed { index, card ->
                        val cardX = cardsStartX + index * (cardWidth + cardGap)
                        
                        if (uiX >= cardX && uiX <= cardX + cardWidth &&
                            uiY >= cardsY && uiY <= cardsY + cardHeight) {
                            // Clicked on card!
                            val canAfford = myPlayer.memory >= card.memoryCost && myPlayer.cpu >= card.cpuCost
                            if (canAfford) {
                                selectedCardId = card.id
                                isPlacingCard = true
                                selectedEntityId = null  // Deselect entity
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
                    network.sendCommand(CommandPacket(
                        commandType = CommandType.PLAY_CARD,
                        cardId = selectedCardId,
                        targetX = worldPos.x,
                        targetY = worldPos.y
                    ))
                    println("Placing card at ${worldPos.x}, ${worldPos.y}")
                    selectedCardId = null
                    isPlacingCard = false
                    return true
                }
                
                // Regular entity click
                val worldPos = camera.unproject(Vector3(screenX.toFloat(), screenY.toFloat(), 0f))
                
                val clickedEntity = network.entities.values.find { entity ->
                    val dx = entity.x - worldPos.x
                    val dy = entity.y - worldPos.y
                    (dx*dx + dy*dy) < 20*20
                }

                if (clickedEntity != null) {
                    if (clickedEntity.ownerId != "0") {
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
                if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                    // Cancel card placement
                    selectedCardId = null
                    isPlacingCard = false
                }
                return true
            }

        }
    }

    private fun handleCameraMovement() {
        val cameraSpeed = 5f
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.A)) {
            camera.translate(-cameraSpeed, 0f)
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.D)) {
            camera.translate(cameraSpeed, 0f)
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.W)) {
            camera.translate(0f, cameraSpeed)
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.S)) {
            camera.translate(0f, -cameraSpeed)
        }
        camera.update()
    }

    private fun handleInput() {
        // This function can be expanded for other continuous input handling
    }

    override fun render() {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        
        handleCameraMovement()
        handleInput()
        shapeRenderer.projectionMatrix = camera.combined
        
        // Draw Background
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.12f, 1f) // Darker, slightly blue background
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        
        // Draw Grid (Subtler)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(1f, 1f, 1f, 0.1f) // Very faint white
        for(i in 0..800 step 50) {
            shapeRenderer.line(i.toFloat(), 0f, i.toFloat(), 600f)
        }
        for(i in 0..600 step 50) {
            shapeRenderer.line(0f, i.toFloat(), 800f, i.toFloat())
        }
        shapeRenderer.end()

        // Draw Entities
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        
        network.entities.values.forEach { entity ->
            // Custom Colors & Shapes
            when(entity.type) {
                EntityType.INSTANCE -> {
                    shapeRenderer.color = Color(0.2f, 0.8f, 1f, 1f) // Cyan-ish
                    shapeRenderer.rect(entity.x - 20, entity.y - 20, 40f, 40f) // Square Base
                    // Inner core
                    shapeRenderer.color = Color(0f, 0.4f, 0.8f, 1f)
                    shapeRenderer.rect(entity.x - 10, entity.y - 10, 20f, 20f)
                }
                EntityType.RESOURCE_NODE -> {
                    // Color depends on owner
                    if (entity.ownerId == "0") {
                        shapeRenderer.color = Color(1f, 0.8f, 0f, 1f) // Gold (neutral)
                    } else if (entity.ownerId == network.myId) {
                        shapeRenderer.color = Color(0.2f, 1f, 0.2f, 1f) // Green (friendly)
                    } else {
                        shapeRenderer.color = Color(1f, 0.2f, 0.2f, 1f) // Red (enemy)
                    }
                    
                    // Diamond shape
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
                    shapeRenderer.color = Color(0.8f, 0.2f, 1f, 1f) // Purple
                    // Triangle
                    shapeRenderer.triangle(
                        entity.x, entity.y + 20,
                        entity.x - 20, entity.y - 15,
                        entity.x + 20, entity.y - 15
                    )
                }
                EntityType.UNIT -> {
                    // Color and shape based on unit type category
                    val unitType = entity.unitType
                    when {
                        // Legacy units
                        unitType == UnitType.SCOUT -> {
                            shapeRenderer.color = Color(0.5f, 1f, 0.5f, 1f)  // Light green
                            shapeRenderer.circle(entity.x, entity.y, 8f)
                        }
                        unitType == UnitType.TANK -> {
                            shapeRenderer.color = Color(0.3f, 0.6f, 0.3f, 1f)  // Dark green
                            shapeRenderer.rect(entity.x - 12, entity.y - 12, 24f, 24f)
                        }
                        unitType == UnitType.RANGED -> {
                            shapeRenderer.color = Color(0.6f, 0.8f, 1f, 1f)  // Light blue
                            shapeRenderer.triangle(
                                entity.x, entity.y + 10,
                                entity.x - 10, entity.y - 10,
                                entity.x + 10, entity.y - 10
                            )
                        }
                        unitType == UnitType.HEALER || unitType == UnitType.RESTFUL_HEALER -> {
                            shapeRenderer.color = Color(1f, 0.8f, 1f, 1f)  // Pink
                            shapeRenderer.circle(entity.x, entity.y, 10f)
                            shapeRenderer.color = Color(0.8f, 0f, 0.8f, 1f)
                            shapeRenderer.circle(entity.x, entity.y, 3f)
                        }
                        
                        // Basic Processes - Gray tones
                        unitType == UnitType.ALLOCATOR || unitType == UnitType.GARBAGE_COLLECTOR || unitType == UnitType.BASIC_PROCESS -> {
                            shapeRenderer.color = Color(0.6f, 0.6f, 0.6f, 1f)  // Gray
                            shapeRenderer.circle(entity.x, entity.y, 9f)
                            if (unitType == UnitType.ALLOCATOR) {
                                shapeRenderer.color = Color(1f, 0.9f, 0f, 1f)  // Gold center (memory)
                                shapeRenderer.circle(entity.x, entity.y, 4f)
                            }
                        }
                        
                        // OOP Units - Multi-colored, layered
                        unitType == UnitType.INHERITANCE_DRONE -> {
                            shapeRenderer.color = Color(1f, 0.5f, 0f, 1f)  // Orange
                            shapeRenderer.circle(entity.x, entity.y, 11f)
                            shapeRenderer.color = Color(1f, 0.7f, 0.2f, 1f)
                            shapeRenderer.circle(entity.x, entity.y, 7f)
                            shapeRenderer.color = Color(1f, 0.9f, 0.4f, 1f)
                            shapeRenderer.circle(entity.x, entity.y, 3f)
                        }
                        unitType == UnitType.POLYMORPH_WARRIOR -> {
                            shapeRenderer.color = Color(0.8f, 0.2f, 1f, 1f)  // Purple
                            shapeRenderer.rect(entity.x - 10, entity.y - 10, 20f, 20f)  // Square morphs
                        }
                        unitType == UnitType.ENCAPSULATION_SHIELD -> {
                            shapeRenderer.color = Color(0.2f, 0.4f, 1f, 1f)  // Blue
                            shapeRenderer.rect(entity.x - 12, entity.y - 12, 24f, 24f)
                            // Inner protected core
                            shapeRenderer.color = Color(0.4f, 0.6f, 1f, 1f)
                            shapeRenderer.rect(entity.x - 6, entity.y - 6, 12f, 12f)
                        }
                        unitType == UnitType.ABSTRACTION_AGENT -> {
                            shapeRenderer.color = Color(0.5f, 0.5f, 1f, 0.5f)  // Translucent blue
                            shapeRenderer.circle(entity.x, entity.y, 10f)
                        }
                        
                        // Reflection & Metaprogramming - Cyan/Teal
                        unitType == UnitType.REFLECTION_SPY -> {
                            shapeRenderer.color = Color(0f, 1f, 1f, 1f)  // Cyan
                            shapeRenderer.circle(entity.x, entity.y, 8f)
                            // Radar lines
                            shapeRenderer.end()
                            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                            shapeRenderer.color = Color(0f, 1f, 1f, 0.6f)
                            shapeRenderer.line(entity.x - 15, entity.y, entity.x + 15, entity.y)
                            shapeRenderer.line(entity.x, entity.y - 15, entity.x, entity.y + 15)
                            shapeRenderer.end()
                            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                        }
                        unitType == UnitType.CODE_INJECTOR -> {
                            shapeRenderer.color = Color(1f, 0f, 1f, 1f)  // Magenta
                            shapeRenderer.triangle(
                                entity.x, entity.y + 11,
                                entity.x - 11, entity.y - 6,
                                entity.x + 11, entity.y - 6
                            )
                        }
                        unitType == UnitType.DYNAMIC_DISPATCHER -> {
                            shapeRenderer.color = Color(0f, 0.8f, 0.8f, 1f)  // Teal
                            shapeRenderer.circle(entity.x, entity.y, 11f)
                            // Pulsating rings
                            shapeRenderer.color = Color(0f, 0.8f, 0.8f, 0.4f)
                            shapeRenderer.circle(entity.x, entity.y, 16f)
                        }
                        
                        // Async & Parallelism - Yellow/Orange
                        unitType == UnitType.COROUTINE_ARCHER -> {
                            shapeRenderer.color = Color(1f, 0.8f, 0f, 1f)  // Gold
                            shapeRenderer.triangle(
                                entity.x, entity.y + 10,
                                entity.x - 8, entity.y - 8,
                                entity.x + 8, entity.y - 8
                            )
                        }
                        unitType == UnitType.PROMISE_KNIGHT -> {
                            shapeRenderer.color = Color(1f, 0.6f, 0f, 1f)  // Orange
                            shapeRenderer.rect(entity.x - 11, entity.y - 11, 22f, 22f)
                        }
                        unitType == UnitType.DEADLOCK_TRAP -> {
                            shapeRenderer.color = Color(1f, 0f, 0f, 0.8f)  // Red (dangerous)
                            shapeRenderer.circle(entity.x, entity.y, 7f)
                            // X mark
                            shapeRenderer.end()
                            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                            shapeRenderer.line(entity.x - 10, entity.y - 10, entity.x + 10, entity.y + 10)
                            shapeRenderer.line(entity.x - 10, entity.y + 10, entity.x + 10, entity.y - 10)
                            shapeRenderer.end()
                            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                        }
                        
                        // Functional Programming - Sharp geometric
                        unitType == UnitType.LAMBDA_SNIPER -> {
                            shapeRenderer.color = Color(1f, 1f, 0f, 1f)  // Bright yellow
                            // Lambda symbol approximation (triangle pointing right)
                            shapeRenderer.triangle(
                                entity.x - 8, entity.y + 12,
                                entity.x - 8, entity.y - 12,
                                entity.x + 10, entity.y
                            )
                        }
                        unitType == UnitType.RECURSIVE_BOMB -> {
                            shapeRenderer.color = Color(1f, 0.2f, 0f, 1f)  // Red-orange
                            shapeRenderer.circle(entity.x, entity.y, 9f)
                            shapeRenderer.color = Color(0.8f, 0f, 0f, 1f)
                            shapeRenderer.circle(entity.x, entity.y, 5f)
                        }
                        unitType == UnitType.HIGHER_ORDER_COMMANDER -> {
                            shapeRenderer.color = Color(1f, 0.8f, 0.2f, 1f)  // Golden
                            shapeRenderer.rect(entity.x - 13, entity.y - 13, 26f, 26f)
                            shapeRenderer.color = Color(1f, 1f, 0.5f, 1f)
                            shapeRenderer.rect(entity.x - 8, entity.y - 8, 16f, 16f)
                        }
                        
                        // Network & Communication - Antenna shapes
                        unitType == UnitType.API_GATEWAY -> {
                            shapeRenderer.color = Color(0.3f, 1f, 0.5f, 1f)  // Green
                            shapeRenderer.rect(entity.x - 10, entity.y - 10, 20f, 20f)
                            // Antenna lines
                            shapeRenderer.end()
                            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                            shapeRenderer.line(entity.x, entity.y + 10, entity.x, entity.y + 18)
                            shapeRenderer.circle(entity.x, entity.y + 20, 3f)
                            shapeRenderer.end()
                            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                        }
                        unitType == UnitType.WEBSOCKET_SCOUT -> {
                            shapeRenderer.color = Color(0.2f, 1f, 0.2f, 1f)  // Bright green
                            shapeRenderer.circle(entity.x, entity.y, 8f)
                            // Signal waves
                            shapeRenderer.end()
                            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                            shapeRenderer.color = Color(0.2f, 1f, 0.2f, 0.4f)
                            shapeRenderer.circle(entity.x, entity.y, 14f)
                            shapeRenderer.circle(entity.x, entity.y, 20f)
                            shapeRenderer.end()
                            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                        }
                        
                        // Storage Units - Box shapes
                        unitType == UnitType.CACHE_RUNNER -> {
                            shapeRenderer.color = Color(0.9f, 0.9f, 0.9f, 1f)  // Silver (fast)
                            shapeRenderer.rect(entity.x - 6, entity.y - 6, 12f, 12f)
                        }
                        unitType == UnitType.INDEXER -> {
                            shapeRenderer.color = Color(0.7f, 0.5f, 0.3f, 1f)  // Brown
                            shapeRenderer.rect(entity.x - 9, entity.y - 9, 18f, 18f)
                            shapeRenderer.color = Color(0.9f, 0.7f, 0.5f, 1f)
                            shapeRenderer.rect(entity.x - 5, entity.y - 5, 10f, 10f)
                        }
                        unitType == UnitType.TRANSACTION_GUARD -> {
                            shapeRenderer.color = Color(0.5f, 0.5f, 0.8f, 1f)  // Blue-gray
                            shapeRenderer.rect(entity.x - 11, entity.y - 11, 22f, 22f)
                        }
                        
                        else -> {
                            // Default for unknown units
                            shapeRenderer.color = Color(0.3f, 1f, 0.3f, 1f)
                            shapeRenderer.circle(entity.x, entity.y, 10f)
                        }
                    }
                }
            }
            
            // Highlight selection Ring
            if (entity.id == selectedEntityId) {
                shapeRenderer.end() // Switch to Line
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                shapeRenderer.color = Color.WHITE
                shapeRenderer.circle(entity.x, entity.y, 25f)
                shapeRenderer.end() // Switch back to Filled
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            }
            
            // HP Bar (Modern)
            if (entity.type != EntityType.RESOURCE_NODE) {
                val hpPct = entity.hp.toFloat() / entity.maxHp.toFloat()
                val barWidth = 40f
                val barHeight = 4f
                val yOffset = 30f
                
                // Background
                shapeRenderer.color = Color(0.2f, 0f, 0f, 0.8f)
                shapeRenderer.rect(entity.x - barWidth/2, entity.y + yOffset, barWidth, barHeight)
                
                // Health
                shapeRenderer.color = Color(0.2f, 1f, 0.2f, 0.8f)
                shapeRenderer.rect(entity.x - barWidth/2, entity.y + yOffset, barWidth * hpPct, barHeight)
            }
            
            // Draw Laser
            if (entity.attackingTargetId != null) {
                val target = network.entities[entity.attackingTargetId!!]
                if (target != null) {
                    shapeRenderer.end()
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                    shapeRenderer.color = Color(1f, 0f, 0f, 0.6f) // Red laser
                    shapeRenderer.line(entity.x, entity.y, target.x, target.y)
                    shapeRenderer.end()
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                }
            }
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
        
        // === WORLD-SPACE TEXT (Entity Labels) ===
        batch.projectionMatrix = camera.combined
        batch.begin()
        
        network.entities.values.forEach { entity ->
            val label = when(entity.type) {
                EntityType.INSTANCE -> "BASE"
                EntityType.FACTORY -> "FACTORY"
                EntityType.RESOURCE_NODE -> when(entity.resourceType) {
                    ResourceType.MEMORY -> "MEM"
                    ResourceType.CPU -> "CPU"
                    null -> "???"
                }
                EntityType.UNIT -> "UNIT"
            }
            labelFont.color = Color.WHITE
            labelFont.draw(batch, label, entity.x - 15, entity.y - 25)
        }
        
        batch.end()
        
        // === SCREEN-SPACE UI ===
        uiMatrix.setToOrtho2D(0f, 0f, 800f, 600f)
        batch.projectionMatrix = uiMatrix
        batch.begin()
        
        // ... (Game Over check, Entity info, Resources, Instructions)
        
        // GAME OVER CHECK
        if (network.winnerId != null) {
            val isWin = network.winnerId == network.myId
            val message = if (isWin) "VICTORY!" else "DEFEAT"
            val color = if (isWin) Color.GREEN else Color.RED
            
            font.color = color
            font.data.setScale(3f)
            font.draw(batch, message, 300f, 350f)
            font.data.setScale(1f)
            font.color = Color.WHITE
            font.draw(batch, "Restart the server to play again", 280f, 250f)
            batch.end()
            return
        }
        
        // Selected entity info panel
        if (selectedEntityId != null) {
            val selected = network.entities[selectedEntityId!!]
            if (selected != null) {
                var infoY = 550f
                font.color = Color.CYAN
                font.draw(batch, "=== SELECTED ===", 600f, infoY)
                infoY -= 20
                font.color = Color.WHITE
                font.draw(batch, "Type: ${selected.type}", 600f, infoY)
                infoY -= 20
                font.draw(batch, "HP: ${selected.hp}/${selected.maxHp}", 600f, infoY)
                infoY -= 20
                val ownerName = network.players[selected.ownerId]?.name ?: "Neutral"
                font.draw(batch, "Owner: $ownerName", 600f, infoY)
            }
        }
        
        batch.end()
        
        // === SPAWN RADIUS VISUALIZATION ===
        if (isPlacingCard) {
            val myBase = network.entities.values.find { it.ownerId == network.myId && it.type == EntityType.INSTANCE }
            if (myBase != null) {
                Gdx.gl.glEnable(GL20.GL_BLEND)
                shapeRenderer.projectionMatrix = camera.combined
                shapeRenderer.transformMatrix.idt()
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                
                val mousePos = camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f))
                val dx = mousePos.x - myBase.x
                val dy = mousePos.y - myBase.y
                val distSq = dx*dx + dy*dy
                val maxDist = 200f // Match server logic
                
                if (distSq <= maxDist * maxDist) {
                    shapeRenderer.color = Color.GREEN
                } else {
                    shapeRenderer.color = Color.RED
                }
                
                shapeRenderer.circle(myBase.x, myBase.y, maxDist)
                shapeRenderer.end()
                Gdx.gl.glDisable(GL20.GL_BLEND)
            }
        }
        
        uiMatrix.setToOrtho2D(0f, 0f, 800f, 600f)
        batch.projectionMatrix = uiMatrix
        batch.begin()
        var yOffset = 580f
        font.color = Color.WHITE
        font.draw(batch, "FPS: ${Gdx.graphics.framesPerSecond}", 20f, yOffset)
        network.players.values.forEach { player ->
             yOffset -= 25f
             val info = "${player.name} [MEM: ${player.memory} | CPU: ${player.cpu}]"
             font.color = Color.BLACK
             font.draw(batch, info, 22f, yOffset - 2)
             font.color = Color.WHITE
             font.draw(batch, info, 20f, yOffset)
        }
        
        if (network.players.size < 2) {
             yOffset -= 40f
             font.color = Color.YELLOW
             font.draw(batch, "WAITING FOR OPPONENT...", 20f, yOffset)
             font.draw(batch, "(Run the client again to simulate Player 2)", 20f, yOffset - 20f)
             font.color = Color.WHITE
        }

        // Instructions (Moved higher to avoid overlap)
        yOffset = 140f  
        font.color = Color.LIGHT_GRAY
        font.draw(batch, "Controls: Click Card > Click Map to Deploy | ESC=Cancel | WASD=Camera", 20f, yOffset)
        
        batch.end()
        
        // === CARD HAND UI (draw with ShapeRenderer + new batch) ===
        val myId = network.myId
        if (myId != null) {
            val myPlayer = network.players[myId]
            if (myPlayer != null && myPlayer.hand.isNotEmpty()) {
                val cardWidth = 150f
                val cardHeight = 80f
                val cardGap = 10f
                val cardsStartX = (800f - (cardWidth + cardGap) * myPlayer.hand.size) / 2f
                val cardsY = 10f
                
                // Draw card backgrounds
                Gdx.gl.glEnable(GL20.GL_BLEND)
                shapeRenderer.projectionMatrix = uiMatrix
                shapeRenderer.transformMatrix.idt() // Force identity transform
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                
                myPlayer.hand.forEachIndexed { index, card ->
                    val cardX = cardsStartX + index * (cardWidth + cardGap)
                    val canAfford = myPlayer.memory >= card.memoryCost && myPlayer.cpu >= card.cpuCost
                    val isSelected = selectedCardId == card.id
                    val onCooldown = myPlayer.globalCooldown > 0
                    
                     shapeRenderer.color = when {
                        isSelected -> Color(0.2f, 0.8f, 1f, 0.9f)
                        onCooldown -> Color(0.3f, 0.3f, 0.3f, 0.5f) // Gray/Dimmed if cooldown
                        canAfford -> Color(0.3f, 0.3f, 0.4f, 0.9f)
                        else -> Color(0.2f, 0.2f, 0.2f, 0.6f)
                    }
                    shapeRenderer.rect(cardX, cardsY, cardWidth, cardHeight)
                    
                    // Cooldown Progress Overlay
                    if (onCooldown) {
                        shapeRenderer.color = Color(0f, 0f, 0f, 0.5f)
                        val cooldownRatio = (myPlayer.globalCooldown / 1.5f).coerceIn(0f, 1f)
                        shapeRenderer.rect(cardX, cardsY, cardWidth, cardHeight * cooldownRatio)
                    }
                }
                shapeRenderer.end()
                
                // Card borders
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
                myPlayer.hand.forEachIndexed { index, card ->
                    val cardX = cardsStartX + index * (cardWidth + cardGap)
                    shapeRenderer.color = if (selectedCardId == card.id) Color.WHITE else Color(0.5f, 0.5f, 0.5f, 1f)
                    shapeRenderer.rect(cardX, cardsY, cardWidth, cardHeight)
                }
                shapeRenderer.end()
                Gdx.gl.glDisable(GL20.GL_BLEND)
                
                // Card text
                batch.projectionMatrix = uiMatrix
                batch.begin()
                myPlayer.hand.forEachIndexed { index, card ->
                    val cardX = cardsStartX + index * (cardWidth + cardGap)
                    
                    val cardName = when(card.type) {
                        CardType.SPAWN_SCOUT -> "SCOUT"
                        CardType.SPAWN_TANK -> "TANK"
                        CardType.SPAWN_RANGED -> "RANGED"
                        CardType.SPAWN_HEALER -> "HEALER"
                        CardType.BUILD_FACTORY -> "FACTORY"
                        CardType.BUILD_COMPILER_FACTORY -> "COMPILER"
                        CardType.BUILD_INTERPRETER_FACTORY -> "INTERP"
                        CardType.BUILD_INHERITANCE_FACTORY -> "INH.FAC"
                        
                        // Basic Processes
                        CardType.SPAWN_ALLOCATOR -> "ALLOC"
                        CardType.SPAWN_GARBAGE_COLLECTOR -> "GC"
                        CardType.SPAWN_BASIC_PROCESS -> "PROC"
                        
                        // OOP
                        CardType.SPAWN_INHERITANCE_DRONE -> "INHERIT"
                        CardType.SPAWN_POLYMORPH_WARRIOR -> "POLY"
                        CardType.SPAWN_ENCAPSULATION_SHIELD -> "SHIELD"
                        CardType.SPAWN_ABSTRACTION_AGENT -> "ABSTR"
                        
                        // Reflection
                        CardType.SPAWN_REFLECTION_SPY -> "SPY"
                        CardType.SPAWN_CODE_INJECTOR -> "INJECT"
                        CardType.SPAWN_DYNAMIC_DISPATCHER -> "DISP"
                        
                        // Async
                        CardType.SPAWN_COROUTINE_ARCHER -> "COROUT"
                        CardType.SPAWN_PROMISE_KNIGHT -> "PROM"
                        CardType.SPAWN_DEADLOCK_TRAP -> "TRAP"
                        
                        // Functional
                        CardType.SPAWN_LAMBDA_SNIPER -> "LAMBDA"
                        CardType.SPAWN_RECURSIVE_BOMB -> "REC.B"
                        CardType.SPAWN_HIGHER_ORDER_COMMANDER -> "H.O.C"
                        
                        // Network
                        CardType.SPAWN_API_GATEWAY -> "API"
                        CardType.SPAWN_WEBSOCKET_SCOUT -> "WS"
                        CardType.SPAWN_RESTFUL_HEALER -> "REST"
                        
                        // Storage
                        CardType.SPAWN_CACHE_RUNNER -> "CACHE"
                        CardType.SPAWN_INDEXER -> "INDEX"
                        CardType.SPAWN_TRANSACTION_GUARD -> "TRANS"
                        
                        // Memory
                        CardType.SPAWN_POINTER -> "POINTER"
                        CardType.SPAWN_BUFFER -> "BUFFER"
                        
                        // Safety
                        CardType.SPAWN_ASSERT -> "ASSERT"
                        CardType.SPAWN_STATIC_CAST -> "S.CAST"
                        CardType.SPAWN_DYNAMIC_CAST -> "D.CAST"
                        
                        // Concurrency
                        CardType.SPAWN_MUTEX_GUARDIAN -> "MUTEX"
                        CardType.SPAWN_SEMAPHORE_CONTROLLER -> "SEMA"
                        CardType.SPAWN_THREAD_POOL -> "T.POOL"
                        
                        // Capture & Resource
                        CardType.SPAWN_MEMORY_MINER -> "M.MINE"
                        CardType.SPAWN_CPU_HARVESTER -> "C.HARV"
                        CardType.SPAWN_RESOURCE_CLONER -> "CLONE"
                        CardType.SPAWN_NODE_DEFENDER -> "N.DEF"
                        
                        CardType.UPGRADE_INHERITANCE -> "UPGRADE"
                    }
                    
                    font.color = Color.WHITE
                    font.draw(batch, cardName, cardX + 10, cardsY + cardHeight - 10)
                    
                    font.color = if (myPlayer.memory >= card.memoryCost) Color.GREEN else Color.RED
                    font.draw(batch, "${card.memoryCost}M", cardX + 10, cardsY + 35)
                    
                    font.color = if (myPlayer.cpu >= card.cpuCost) Color.GREEN else Color.RED
                    font.draw(batch, "${card.cpuCost}C", cardX + 80, cardsY + 35)
                }
                batch.end()
            }
        }
    }

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
        font.dispose()
        network.dispose()
    }
}
