package com.memoryleak.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.memoryleak.core.MemoryLeakApp
import com.memoryleak.shared.model.CardType
import com.memoryleak.shared.model.UnitStatsData

/**
 * Deck selection screen where players choose 10 cards.
 */
class DeckSelectionScreen(private val app: MemoryLeakApp) : Screen {
    
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var smallFont: BitmapFont
    private lateinit var titleFont: BitmapFont
    private lateinit var shapeRenderer: ShapeRenderer
    
    private val uiMatrix = Matrix4()
    
    // All available cards (excluding factories which are always available)
    private val availableCards = CardType.values().filter { 
        !it.name.startsWith("BUILD_") && it != CardType.UPGRADE_INHERITANCE
    }
    
    // Selected cards (max 10)
    private val selectedCards = mutableListOf<CardType>()
    
    // Saved decks from server
    private var savedDecks = mutableListOf<com.memoryleak.shared.network.SavedDeck>()
    private var deckNameInput = "My Deck"
    private var isEnteringDeckName = false
    
    // Scroll position
    private var scrollY = 0f
    
    // UI constants
    private val screenWidth = 800f
    private val screenHeight = 600f
    private val cardWidth = 120f
    private val cardHeight = 100f
    private val cardGap = 10f
    private val cardsPerRow = 5
    
    // Categories for organization
    private val categories = mapOf(
        "Basic" to listOf(CardType.SPAWN_SCOUT, CardType.SPAWN_TANK, CardType.SPAWN_RANGED, CardType.SPAWN_HEALER),
        "Process" to listOf(CardType.SPAWN_ALLOCATOR, CardType.SPAWN_GARBAGE_COLLECTOR, CardType.SPAWN_BASIC_PROCESS),
        "OOP" to listOf(CardType.SPAWN_INHERITANCE_DRONE, CardType.SPAWN_POLYMORPH_WARRIOR, CardType.SPAWN_ENCAPSULATION_SHIELD, CardType.SPAWN_ABSTRACTION_AGENT),
        "Reflection" to listOf(CardType.SPAWN_REFLECTION_SPY, CardType.SPAWN_CODE_INJECTOR, CardType.SPAWN_DYNAMIC_DISPATCHER),
        "Async" to listOf(CardType.SPAWN_COROUTINE_ARCHER, CardType.SPAWN_PROMISE_KNIGHT, CardType.SPAWN_DEADLOCK_TRAP),
        "Functional" to listOf(CardType.SPAWN_LAMBDA_SNIPER, CardType.SPAWN_RECURSIVE_BOMB, CardType.SPAWN_HIGHER_ORDER_COMMANDER),
        "Network" to listOf(CardType.SPAWN_API_GATEWAY, CardType.SPAWN_WEBSOCKET_SCOUT, CardType.SPAWN_RESTFUL_HEALER),
        "Storage" to listOf(CardType.SPAWN_CACHE_RUNNER, CardType.SPAWN_INDEXER, CardType.SPAWN_TRANSACTION_GUARD),
        "Memory" to listOf(CardType.SPAWN_POINTER, CardType.SPAWN_BUFFER),
        "Safety" to listOf(CardType.SPAWN_ASSERT, CardType.SPAWN_STATIC_CAST, CardType.SPAWN_DYNAMIC_CAST),
        "Concurrency" to listOf(CardType.SPAWN_MUTEX_GUARDIAN, CardType.SPAWN_SEMAPHORE_CONTROLLER, CardType.SPAWN_THREAD_POOL)
    )
    
    override fun show() {
        batch = SpriteBatch()
        font = BitmapFont()
        smallFont = BitmapFont()
        titleFont = BitmapFont()
        shapeRenderer = ShapeRenderer()
        
        titleFont.data.setScale(1.5f)
        smallFont.data.setScale(0.8f)
        
        // Load previously selected deck from local state
        selectedCards.clear()
        app.selectedDeck.forEach { typeName ->
            try {
                selectedCards.add(CardType.valueOf(typeName))
            } catch (e: Exception) {
                // Ignore invalid card types
            }
        }
        
        // Set up callback for when decks are loaded from server
        app.networkClient.onDecksLoaded = { decks ->
            com.badlogic.gdx.Gdx.app.postRunnable {
                savedDecks.clear()
                savedDecks.addAll(decks)
                println("Loaded ${decks.size} saved decks from server")
            }
        }
        
        // Load decks from server
        app.networkClient.loadDecks()
    }
    
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        
        handleInput(delta)
        
        uiMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        
        // Draw background
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = uiMatrix
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.12f, 0.12f, 0.18f, 1f)
        shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.end()
        
        // Draw cards by category
        batch.projectionMatrix = uiMatrix
        batch.begin()
        titleFont.color = Color.CYAN
        titleFont.draw(batch, "SELECT YOUR DECK (${selectedCards.size}/10)", 50f, screenHeight - 20)
        batch.end()
        
        var yPos = screenHeight - 80 + scrollY
        
        categories.forEach { (categoryName, cards) ->
            // Category header
            batch.begin()
            font.color = Color.YELLOW
            font.draw(batch, categoryName, 50f, yPos)
            batch.end()
            
            yPos -= 25
            
            // Draw cards in this category
            var xPos = 50f
            var cardsInRow = 0
            
            cards.forEach { cardType ->
                if (cardsInRow >= cardsPerRow) {
                    xPos = 50f
                    yPos -= cardHeight + cardGap
                    cardsInRow = 0
                }
                
                drawCard(cardType, xPos, yPos - cardHeight, cardWidth, cardHeight)
                
                xPos += cardWidth + cardGap
                cardsInRow++
            }
            
            yPos -= cardHeight + cardGap + 20
        }
        
        // Draw selected deck panel
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.15f, 0.15f, 0.22f, 1f)
        shapeRenderer.rect(0f, 0f, screenWidth, 100f)
        shapeRenderer.end()
        
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.GRAY
        shapeRenderer.line(0f, 100f, screenWidth, 100f)
        shapeRenderer.end()
        
        // Draw selected cards
        var selX = 10f
        selectedCards.forEachIndexed { _, cardType ->
            drawMiniCard(cardType, selX, 25f, 55f, 55f, true)
            selX += 60f
        }
        
        // Draw saved decks dropdown area
        batch.begin()
        smallFont.color = Color.YELLOW
        smallFont.draw(batch, "Saved Decks:", 10f, 20f)
        batch.end()
        
        // Draw saved deck buttons (horizontal scroll area)
        var deckX = 100f
        savedDecks.take(5).forEach { deck ->
            drawDeckButton(deck, deckX, 3f, 80f, 18f)
            deckX += 85f
        }
        
        // Draw buttons
        drawButton(screenWidth - 110, 5f, 100f, 30f, "Use", Color(0.2f, 0.6f, 0.2f, 1f))
        drawButton(screenWidth - 110, 40f, 100f, 30f, "Save", Color(0.2f, 0.4f, 0.6f, 1f))
        drawButton(screenWidth - 110, 75f, 100f, 20f, "Back", Color(0.5f, 0.3f, 0.3f, 1f))
        
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }
    
    private fun drawDeckButton(deck: com.memoryleak.shared.network.SavedDeck, x: Float, y: Float, width: Float, height: Float) {
        val isHovered = isMouseOver(x, y, width, height)
        
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = if (isHovered) Color(0.35f, 0.35f, 0.45f, 0.9f) else Color(0.25f, 0.25f, 0.35f, 0.9f)
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.GRAY
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        
        batch.begin()
        smallFont.color = Color.WHITE
        smallFont.draw(batch, deck.name.take(10), x + 3, y + height - 3)
        batch.end()
    }
    
    private fun drawCard(cardType: CardType, x: Float, y: Float, width: Float, height: Float) {
        val isSelected = selectedCards.contains(cardType)
        val isHovered = isMouseOver(x, y, width, height)
        val definition = UnitStatsData.getCardDefinition(cardType)
        
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        
        // Card background
        shapeRenderer.color = when {
            isSelected -> Color(0.2f, 0.5f, 0.2f, 0.9f)
            isHovered -> Color(0.25f, 0.25f, 0.35f, 0.9f)
            else -> Color(0.18f, 0.18f, 0.25f, 0.9f)
        }
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (isSelected) Color.GREEN else Color.GRAY
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        
        // Card content
        batch.begin()
        
        // Card name
        smallFont.color = Color.WHITE
        val name = definition?.name ?: cardType.name.replace("SPAWN_", "").take(10)
        smallFont.draw(batch, name, x + 5, y + height - 5)
        
        // Cost
        if (definition != null) {
            smallFont.color = Color.YELLOW
            smallFont.draw(batch, "${definition.memoryCost}M", x + 5, y + height - 25)
            smallFont.color = Color.CYAN
            smallFont.draw(batch, "${definition.cpuCost}C", x + 55, y + height - 25)
        }
        
        // Category
        smallFont.color = Color.GRAY
        smallFont.draw(batch, definition?.category ?: "", x + 5, y + 15)
        
        batch.end()
    }
    
    private fun drawMiniCard(cardType: CardType, x: Float, y: Float, width: Float, height: Float, inDeck: Boolean) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.25f, 0.25f, 0.35f, 0.9f)
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.CYAN
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        
        batch.begin()
        smallFont.color = Color.WHITE
        val name = cardType.name.replace("SPAWN_", "").take(6)
        smallFont.draw(batch, name, x + 3, y + height - 5)
        
        if (inDeck) {
            smallFont.color = Color.RED
            smallFont.draw(batch, "X", x + width - 12, y + 15)
        }
        batch.end()
    }
    
    private fun drawButton(x: Float, y: Float, width: Float, height: Float, text: String, color: Color) {
        val isHovered = isMouseOver(x, y, width, height)
        
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = if (isHovered) color.cpy().add(0.1f, 0.1f, 0.1f, 0f) else color
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(x, y, width, height)
        shapeRenderer.end()
        
        batch.begin()
        font.color = Color.WHITE
        font.draw(batch, text, x + (width - text.length * 10) / 2, y + height / 2 + 8)
        batch.end()
    }
    
    private fun handleInput(delta: Float) {
        // Scroll with mouse wheel or keys
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.UP)) {
            scrollY -= 300 * delta
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.DOWN)) {
            scrollY += 300 * delta
        }
        
        // Handle clicks
        if (Gdx.input.justTouched()) {
            // Check button clicks - Use button
            if (isMouseOver(screenWidth - 110, 5f, 100f, 30f)) {
                useDeck()
                return
            }
            // Save button
            if (isMouseOver(screenWidth - 110, 40f, 100f, 30f)) {
                saveDeckToServer()
                return
            }
            // Back button
            if (isMouseOver(screenWidth - 110, 75f, 100f, 20f)) {
                app.showMainMenu()
                return
            }
            
            // Check saved deck selection
            var deckX = 100f
            savedDecks.take(5).forEach { deck ->
                if (isMouseOver(deckX, 3f, 80f, 18f)) {
                    loadDeckFromSaved(deck)
                    return
                }
                deckX += 85f
            }
            
            // Check selected card removal
            var selX = 10f
            selectedCards.toList().forEach { cardType ->
                if (isMouseOver(selX, 25f, 55f, 55f)) {
                    selectedCards.remove(cardType)
                    return
                }
                selX += 60f
            }
            
            // Check card selection
            var yPos = screenHeight - 80 + scrollY
            categories.forEach { (_, cards) ->
                yPos -= 25
                
                var xPos = 50f
                var cardsInRow = 0
                
                cards.forEach { cardType ->
                    if (cardsInRow >= cardsPerRow) {
                        xPos = 50f
                        yPos -= cardHeight + cardGap
                        cardsInRow = 0
                    }
                    
                    if (isMouseOver(xPos, yPos - cardHeight, cardWidth, cardHeight)) {
                        toggleCard(cardType)
                        return
                    }
                    
                    xPos += cardWidth + cardGap
                    cardsInRow++
                }
                
                yPos -= cardHeight + cardGap + 20
            }
        }
    }
    
    private fun toggleCard(cardType: CardType) {
        if (selectedCards.contains(cardType)) {
            selectedCards.remove(cardType)
        } else if (selectedCards.size < 10) {
            selectedCards.add(cardType)
        }
    }
    
    private fun useDeck() {
        // Use the selected deck locally and go back to main menu
        app.selectedDeck = selectedCards.map { it.name }
        app.showMainMenu()
    }
    
    private fun saveDeckToServer() {
        // Save the current deck to the server
        if (selectedCards.isNotEmpty()) {
            val deckName = "Deck ${savedDecks.size + 1}"
            app.networkClient.saveDeck(deckName, selectedCards.map { it.name })
            // Also update local selection
            app.selectedDeck = selectedCards.map { it.name }
        }
    }
    
    private fun loadDeckFromSaved(deck: com.memoryleak.shared.network.SavedDeck) {
        // Load a saved deck from the server
        selectedCards.clear()
        deck.cardTypes.forEach { typeName ->
            try {
                selectedCards.add(CardType.valueOf(typeName))
            } catch (e: Exception) {
                // Ignore invalid card types
            }
        }
    }
    
    private fun isMouseOver(x: Float, y: Float, width: Float, height: Float): Boolean {
        val mouseX = Gdx.input.x * (screenWidth / Gdx.graphics.width)
        val mouseY = (Gdx.graphics.height - Gdx.input.y) * (screenHeight / Gdx.graphics.height)
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }
    
    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    
    override fun dispose() {
        batch.dispose()
        font.dispose()
        smallFont.dispose()
        titleFont.dispose()
        shapeRenderer.dispose()
    }
}
