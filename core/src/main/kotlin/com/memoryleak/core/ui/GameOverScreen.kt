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
 * Game over screen showing result and return to menu button.
 */
class GameOverScreen(
    private val app: MemoryLeakApp,
    private val winnerId: String,
    private val youWin: Boolean
) : Screen {
    
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private lateinit var shapeRenderer: ShapeRenderer
    
    private val uiMatrix = Matrix4()
    
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
        
        titleFont.data.setScale(3f)
        font.data.setScale(1.2f)
        
        // Clear game state
        app.networkClient.clearGameState()
    }
    
    override fun render(delta: Float) {
        // Background color based on result
        if (youWin) {
            Gdx.gl.glClearColor(0.1f, 0.2f, 0.1f, 1f)
        } else {
            Gdx.gl.glClearColor(0.2f, 0.1f, 0.1f, 1f)
        }
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        
        handleInput()
        
        uiMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        
        // Draw decorative elements
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = uiMatrix
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        
        // Gradient overlay
        if (youWin) {
            shapeRenderer.color = Color(0.1f, 0.3f, 0.1f, 0.5f)
        } else {
            shapeRenderer.color = Color(0.3f, 0.1f, 0.1f, 0.5f)
        }
        shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)
        
        // Return button
        val buttonX = (screenWidth - buttonWidth) / 2
        val buttonY = screenHeight / 2 - 100
        val hoverButton = isMouseOver(buttonX, buttonY, buttonWidth, buttonHeight)
        shapeRenderer.color = if (hoverButton) Color(0.4f, 0.4f, 0.6f, 1f) else Color(0.3f, 0.3f, 0.5f, 1f)
        shapeRenderer.rect(buttonX, buttonY, buttonWidth, buttonHeight)
        
        shapeRenderer.end()
        
        // Button border
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rect(buttonX, buttonY, buttonWidth, buttonHeight)
        shapeRenderer.end()
        
        Gdx.gl.glDisable(GL20.GL_BLEND)
        
        // Draw text
        batch.projectionMatrix = uiMatrix
        batch.begin()
        
        // Result title
        titleFont.color = if (youWin) Color.GREEN else Color.RED
        val resultText = if (youWin) "VICTORY!" else "DEFEAT"
        val titleWidth = resultText.length * 35  // Approximate
        titleFont.draw(batch, resultText, (screenWidth - titleWidth) / 2, screenHeight - 150)
        
        // Subtitle
        font.color = Color.WHITE
        val subtitle = if (youWin) "You have conquered the enemy's memory!" else "Your memory has been corrupted..."
        font.draw(batch, subtitle, (screenWidth - subtitle.length * 8) / 2, screenHeight - 220)
        
        // Stats (if available)
        val stats = app.playerStats
        if (stats != null) {
            font.color = Color.LIGHT_GRAY
            font.draw(batch, "Total Wins: ${stats.wins}", (screenWidth - 100) / 2, screenHeight / 2 + 20)
            font.draw(batch, "Total Games: ${stats.totalGames}", (screenWidth - 100) / 2, screenHeight / 2 - 10)
        }
        
        // Button text
        font.color = Color.WHITE
        font.draw(batch, "Return to Menu", buttonX + 35, buttonY + 32)
        
        // Footer
        font.color = Color.DARK_GRAY
        font.draw(batch, "Press ENTER or click button to continue", (screenWidth - 300) / 2, 50f)
        
        batch.end()
    }
    
    private fun handleInput() {
        // Enter key
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER) ||
            Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            returnToMenu()
        }
        
        // Mouse click
        if (Gdx.input.justTouched()) {
            val buttonX = (screenWidth - buttonWidth) / 2
            val buttonY = screenHeight / 2 - 100
            
            if (isMouseOver(buttonX, buttonY, buttonWidth, buttonHeight)) {
                returnToMenu()
            }
        }
    }
    
    private fun isMouseOver(x: Float, y: Float, width: Float, height: Float): Boolean {
        val mouseX = Gdx.input.x * (screenWidth / Gdx.graphics.width)
        val mouseY = (Gdx.graphics.height - Gdx.input.y) * (screenHeight / Gdx.graphics.height)
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }
    
    private fun returnToMenu() {
        app.sessionId = null
        app.opponentName = null
        app.showMainMenu()
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
