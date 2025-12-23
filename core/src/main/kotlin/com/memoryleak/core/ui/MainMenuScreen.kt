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

/**
 * Main menu screen with matchmaking and stats display.
 */
class MainMenuScreen(private val app: MemoryLeakApp) : Screen {
    
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private lateinit var shapeRenderer: ShapeRenderer
    
    private val uiMatrix = Matrix4()
    
    private var isSearching = false
    private var statusMessage = ""
    private var dotCount = 0
    private var dotTimer = 0f
    
    // UI constants
    private val screenWidth = 800f
    private val screenHeight = 600f
    private val buttonWidth = 200f
    private val buttonHeight = 50f
    
    override fun show() {
        batch = SpriteBatch()
        font = BitmapFont()
        titleFont = BitmapFont()
        shapeRenderer = ShapeRenderer()
        
        titleFont.data.setScale(2f)
        font.data.setScale(1.2f)
        
        // Setup network callbacks
        app.networkClient.onMatchFound = { packet ->
            Gdx.app.postRunnable {
                isSearching = false
                app.onMatchFound(packet.sessionId, packet.opponentName, packet.isPlayer1, packet.mapWidth, packet.mapHeight)
            }
        }
        
        app.networkClient.onMatchmakingStatus = { status ->
            Gdx.app.postRunnable {
                statusMessage = status
            }
        }
        
        app.networkClient.onError = { error ->
            Gdx.app.postRunnable {
                statusMessage = "Error: $error"
                isSearching = false
            }
        }
    }
    
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        
        // Update dot animation
        if (isSearching) {
            dotTimer += delta
            if (dotTimer > 0.5f) {
                dotTimer = 0f
                dotCount = (dotCount + 1) % 4
            }
        }
        
        handleInput()
        
        uiMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        
        // Draw UI elements
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = uiMatrix
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        
        // Background
        shapeRenderer.color = Color(0.12f, 0.12f, 0.18f, 1f)
        shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)
        
        // Stats panel background
        shapeRenderer.color = Color(0.15f, 0.15f, 0.22f, 1f)
        shapeRenderer.rect(50f, 150f, 300f, 200f)
        
        // Play button
        val playX = (screenWidth - buttonWidth) / 2
        val playY = screenHeight / 2 - 20
        val hoverPlay = isMouseOver(playX, playY, buttonWidth, buttonHeight)
        if (isSearching) {
            shapeRenderer.color = Color(0.5f, 0.3f, 0.3f, 1f)
        } else {
            shapeRenderer.color = if (hoverPlay) Color(0.3f, 0.7f, 0.3f, 1f) else Color(0.2f, 0.6f, 0.2f, 1f)
        }
        shapeRenderer.rect(playX, playY, buttonWidth, buttonHeight)
        
        // Deck button
        val deckY = playY - 70
        val hoverDeck = isMouseOver(playX, deckY, buttonWidth, buttonHeight)
        shapeRenderer.color = if (hoverDeck) Color(0.3f, 0.3f, 0.7f, 1f) else Color(0.2f, 0.2f, 0.6f, 1f)
        shapeRenderer.rect(playX, deckY, buttonWidth, buttonHeight)
        
        // Logout button
        val logoutY = deckY - 70
        val hoverLogout = isMouseOver(playX, logoutY, buttonWidth, buttonHeight)
        shapeRenderer.color = if (hoverLogout) Color(0.5f, 0.3f, 0.3f, 1f) else Color(0.4f, 0.2f, 0.2f, 1f)
        shapeRenderer.rect(playX, logoutY, buttonWidth, buttonHeight)
        
        shapeRenderer.end()
        
        // Draw borders
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.GRAY
        shapeRenderer.rect(50f, 150f, 300f, 200f)
        shapeRenderer.rect(playX, playY, buttonWidth, buttonHeight)
        shapeRenderer.rect(playX, deckY, buttonWidth, buttonHeight)
        shapeRenderer.rect(playX, logoutY, buttonWidth, buttonHeight)
        shapeRenderer.end()
        
        Gdx.gl.glDisable(GL20.GL_BLEND)
        
        // Draw text
        batch.projectionMatrix = uiMatrix
        batch.begin()
        
        // Title
        titleFont.color = Color.CYAN
        titleFont.draw(batch, "MEMORY LEAK", (screenWidth - 200) / 2, screenHeight - 50)
        
        // Welcome message
        font.color = Color.WHITE
        font.draw(batch, "Welcome, ${app.playerName}!", (screenWidth - 150) / 2, screenHeight - 100)
        
        // Stats panel
        font.color = Color.CYAN
        font.draw(batch, "Your Statistics", 70f, 340f)
        
        val stats = app.playerStats
        font.color = Color.WHITE
        if (stats != null) {
            font.draw(batch, "Games: ${stats.totalGames}", 70f, 310f)
            font.draw(batch, "Wins: ${stats.wins}", 70f, 285f)
            font.draw(batch, "Losses: ${stats.losses}", 70f, 260f)
            val winRate = if (stats.totalGames > 0) (stats.wins * 100 / stats.totalGames) else 0
            font.draw(batch, "Win Rate: $winRate%", 70f, 235f)
            font.draw(batch, "Units Created: ${stats.totalUnitsCreated}", 70f, 210f)
            font.draw(batch, "Units Killed: ${stats.totalUnitsKilled}", 70f, 185f)
        } else {
            font.draw(batch, "No stats available", 70f, 280f)
        }
        
        // Button labels
        font.color = Color.WHITE
        val playText = if (isSearching) "Cancel" else "Find Match"
        font.draw(batch, playText, playX + (buttonWidth - playText.length * 10) / 2, playY + 32)
        font.draw(batch, "Select Deck", playX + 45, deckY + 32)
        font.draw(batch, "Logout", playX + 70, logoutY + 32)
        
        // Status message
        if (isSearching) {
            font.color = Color.YELLOW
            val dots = ".".repeat(dotCount)
            font.draw(batch, "Searching for opponent$dots", (screenWidth - 200) / 2, playY + 80)
        } else if (statusMessage.isNotEmpty()) {
            font.color = Color.LIGHT_GRAY
            font.draw(batch, statusMessage, (screenWidth - statusMessage.length * 8) / 2, playY + 80)
        }
        
        // Deck info
        font.color = Color.GRAY
        val deckStatus = if (app.selectedDeck.isEmpty()) "Using default deck" else "Custom deck selected (${app.selectedDeck.size} cards)"
        font.draw(batch, deckStatus, (screenWidth - deckStatus.length * 7) / 2, deckY - 30)
        
        // Connection status
        font.color = if (app.networkClient.isConnected) Color.GREEN else Color.RED
        font.draw(batch, if (app.networkClient.isConnected) "Online" else "Offline", screenWidth - 80, 30f)
        
        batch.end()
    }
    
    private fun handleInput() {
        if (Gdx.input.justTouched()) {
            val playX = (screenWidth - buttonWidth) / 2
            val playY = screenHeight / 2 - 20
            val deckY = playY - 70
            val logoutY = deckY - 70
            
            when {
                isMouseOver(playX, playY, buttonWidth, buttonHeight) -> {
                    if (isSearching) {
                        cancelSearch()
                    } else {
                        startSearch()
                    }
                }
                isMouseOver(playX, deckY, buttonWidth, buttonHeight) -> {
                    app.showDeckSelection()
                }
                isMouseOver(playX, logoutY, buttonWidth, buttonHeight) -> {
                    logout()
                }
            }
        }
    }
    
    private fun isMouseOver(x: Float, y: Float, width: Float, height: Float): Boolean {
        val mouseX = Gdx.input.x * (screenWidth / Gdx.graphics.width)
        val mouseY = (Gdx.graphics.height - Gdx.input.y) * (screenHeight / Gdx.graphics.height)
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }
    
    private fun startSearch() {
        if (!app.networkClient.isConnected) {
            statusMessage = "Not connected to server"
            return
        }
        
        isSearching = true
        statusMessage = ""
        app.networkClient.findMatch(app.selectedDeck)
    }
    
    private fun cancelSearch() {
        isSearching = false
        statusMessage = "Search cancelled"
        app.networkClient.cancelMatchmaking()
    }
    
    private fun logout() {
        app.playerId = null
        app.playerName = "Player"
        app.playerStats = null
        app.showLoginScreen()
    }
    
    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    
    override fun dispose() {
        batch.dispose()
        font.dispose()
        titleFont.dispose()
        shapeRenderer.dispose()
    }
}
