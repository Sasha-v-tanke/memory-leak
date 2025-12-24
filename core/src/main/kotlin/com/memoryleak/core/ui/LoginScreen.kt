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
 * Login screen for player authentication.
 * Simple username input with login/register buttons.
 */
class LoginScreen(private val app: MemoryLeakApp) : Screen {
    
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var titleFont: BitmapFont
    private lateinit var shapeRenderer: ShapeRenderer
    
    private val uiMatrix = Matrix4()
    
    private var username = ""
    private var password = ""
    private var activeField = 0  // 0 = username, 1 = password
    private var statusMessage = ""
    private var statusColor = Color.WHITE
    private var isConnecting = false
    private var connectionAttempted = false
    
    // UI constants
    private val screenWidth = 800f
    private val screenHeight = 600f
    private val inputBoxWidth = 300f
    private val inputBoxHeight = 40f
    private val buttonWidth = 140f
    private val buttonHeight = 45f
    
    override fun show() {
        batch = SpriteBatch()
        font = BitmapFont()
        titleFont = BitmapFont()
        shapeRenderer = ShapeRenderer()
        
        titleFont.data.setScale(2f)
        font.data.setScale(1.2f)
        
        // Setup network callbacks
        app.networkClient.onAuthResponse = { success, message, stats ->
            Gdx.app.postRunnable {
                if (success) {
                    statusMessage = "Login successful!"
                    statusColor = Color.GREEN
                    app.onLoginSuccess(app.networkClient.myId ?: "", username, stats)
                } else {
                    statusMessage = message
                    statusColor = Color.RED
                    isConnecting = false
                }
            }
        }
        
        app.networkClient.onError = { error ->
            Gdx.app.postRunnable {
                statusMessage = error
                statusColor = Color.RED
                isConnecting = false
            }
        }
        
        // Connect to server
        if (!app.networkClient.isConnected) {
            statusMessage = "Connecting to server..."
            statusColor = Color.YELLOW
            app.networkClient.connect()
        }
    }
    
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        
        handleInput()
        
        uiMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        
        // Draw UI elements
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = uiMatrix
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        
        // Background gradient effect
        shapeRenderer.color = Color(0.15f, 0.15f, 0.2f, 1f)
        shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)
        
        // Username input box background
        val inputX = (screenWidth - inputBoxWidth) / 2
        val usernameInputY = screenHeight / 2 + 30
        val passwordInputY = screenHeight / 2 - 30
        
        // Username field
        shapeRenderer.color = if (activeField == 0) Color(0.25f, 0.25f, 0.35f, 1f) else Color(0.2f, 0.2f, 0.25f, 1f)
        shapeRenderer.rect(inputX, usernameInputY, inputBoxWidth, inputBoxHeight)
        
        // Password field
        shapeRenderer.color = if (activeField == 1) Color(0.25f, 0.25f, 0.35f, 1f) else Color(0.2f, 0.2f, 0.25f, 1f)
        shapeRenderer.rect(inputX, passwordInputY, inputBoxWidth, inputBoxHeight)
        
        // Login button
        val loginX = (screenWidth - buttonWidth * 2 - 20) / 2
        val buttonY = passwordInputY - 70
        val hoverLogin = isMouseOver(loginX, buttonY, buttonWidth, buttonHeight)
        shapeRenderer.color = if (hoverLogin) Color(0.3f, 0.6f, 0.3f, 1f) else Color(0.2f, 0.5f, 0.2f, 1f)
        shapeRenderer.rect(loginX, buttonY, buttonWidth, buttonHeight)
        
        // Register button
        val registerX = loginX + buttonWidth + 20
        val hoverRegister = isMouseOver(registerX, buttonY, buttonWidth, buttonHeight)
        shapeRenderer.color = if (hoverRegister) Color(0.3f, 0.3f, 0.6f, 1f) else Color(0.2f, 0.2f, 0.5f, 1f)
        shapeRenderer.rect(registerX, buttonY, buttonWidth, buttonHeight)
        
        shapeRenderer.end()
        
        // Draw borders
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (activeField == 0) Color.CYAN else Color.GRAY
        shapeRenderer.rect(inputX, usernameInputY, inputBoxWidth, inputBoxHeight)
        shapeRenderer.color = if (activeField == 1) Color.CYAN else Color.GRAY
        shapeRenderer.rect(inputX, passwordInputY, inputBoxWidth, inputBoxHeight)
        shapeRenderer.color = Color.GRAY
        shapeRenderer.rect(loginX, buttonY, buttonWidth, buttonHeight)
        shapeRenderer.rect(registerX, buttonY, buttonWidth, buttonHeight)
        shapeRenderer.end()
        
        Gdx.gl.glDisable(GL20.GL_BLEND)
        
        // Draw text
        batch.projectionMatrix = uiMatrix
        batch.begin()
        
        // Title
        titleFont.color = Color.CYAN
        titleFont.draw(batch, "MEMORY LEAK", (screenWidth - 200) / 2, screenHeight - 100)
        
        // Subtitle
        font.color = Color.LIGHT_GRAY
        font.draw(batch, "Programming Battle Arena", (screenWidth - 180) / 2, screenHeight - 150)
        
        // Username label
        font.color = Color.WHITE
        font.draw(batch, "Username:", inputX, usernameInputY + inputBoxHeight + 25)
        
        // Username text
        font.color = Color.WHITE
        val usernameCursor = if (activeField == 0 && (System.currentTimeMillis() / 500) % 2 == 0L) "_" else ""
        val usernameDisplay = if (username.isEmpty() && activeField == 0) "_" else username + usernameCursor
        font.draw(batch, usernameDisplay, inputX + 10, usernameInputY + inputBoxHeight - 10)
        
        // Password label
        font.color = Color.WHITE
        font.draw(batch, "Password:", inputX, passwordInputY + inputBoxHeight + 25)
        
        // Password text (masked)
        font.color = Color.WHITE
        val passwordCursor = if (activeField == 1 && (System.currentTimeMillis() / 500) % 2 == 0L) "_" else ""
        val passwordDisplay = if (password.isEmpty() && activeField == 1) "_" else "*".repeat(password.length) + passwordCursor
        font.draw(batch, passwordDisplay, inputX + 10, passwordInputY + inputBoxHeight - 10)
        
        // Button labels
        font.color = Color.WHITE
        font.draw(batch, "Login", loginX + 45, buttonY + 30)
        font.draw(batch, "Register", registerX + 35, buttonY + 30)
        
        // Status message
        font.color = statusColor
        font.draw(batch, statusMessage, (screenWidth - statusMessage.length * 8) / 2, buttonY - 40)
        
        // Connection status
        font.color = if (app.networkClient.isConnected) Color.GREEN else Color.YELLOW
        val connStatus = if (app.networkClient.isConnected) "Connected" else "Connecting..."
        font.draw(batch, connStatus, 20f, 30f)
        
        // Instructions
        font.color = Color.DARK_GRAY
        font.draw(batch, "TAB to switch fields | Enter username and password", (screenWidth - 380) / 2, 80f)
        
        batch.end()
    }
    
    private fun handleInput() {
        // Handle text input
        for (i in 0 until 256) {
            if (Gdx.input.isKeyJustPressed(i)) {
                when (i) {
                    com.badlogic.gdx.Input.Keys.TAB -> {
                        // Switch between fields
                        activeField = (activeField + 1) % 2
                    }
                    com.badlogic.gdx.Input.Keys.BACKSPACE -> {
                        if (activeField == 0 && username.isNotEmpty()) {
                            username = username.dropLast(1)
                        } else if (activeField == 1 && password.isNotEmpty()) {
                            password = password.dropLast(1)
                        }
                    }
                    com.badlogic.gdx.Input.Keys.ENTER -> {
                        if (activeField == 0) {
                            activeField = 1  // Move to password field
                        } else {
                            tryLogin()
                        }
                    }
                    in com.badlogic.gdx.Input.Keys.A..com.badlogic.gdx.Input.Keys.Z -> {
                        val char = ('A' + (i - com.badlogic.gdx.Input.Keys.A))
                        val finalChar = if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT) ||
                            Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT)) char else char.lowercaseChar()
                        
                        if (activeField == 0 && username.length < 20) {
                            username += finalChar
                        } else if (activeField == 1 && password.length < 32) {
                            password += finalChar
                        }
                    }
                    in com.badlogic.gdx.Input.Keys.NUM_0..com.badlogic.gdx.Input.Keys.NUM_9 -> {
                        val char = '0' + (i - com.badlogic.gdx.Input.Keys.NUM_0)
                        if (activeField == 0 && username.length < 20) {
                            username += char
                        } else if (activeField == 1 && password.length < 32) {
                            password += char
                        }
                    }
                }
            }
        }
        
        // Handle mouse click
        if (Gdx.input.justTouched()) {
            val inputX = (screenWidth - inputBoxWidth) / 2
            val usernameInputY = screenHeight / 2 + 30
            val passwordInputY = screenHeight / 2 - 30
            val loginX = (screenWidth - buttonWidth * 2 - 20) / 2
            val buttonY = passwordInputY - 70
            val registerX = loginX + buttonWidth + 20
            
            // Check input field clicks
            if (isMouseOver(inputX, usernameInputY, inputBoxWidth, inputBoxHeight)) {
                activeField = 0
            } else if (isMouseOver(inputX, passwordInputY, inputBoxWidth, inputBoxHeight)) {
                activeField = 1
            } else if (isMouseOver(loginX, buttonY, buttonWidth, buttonHeight)) {
                tryLogin()
            } else if (isMouseOver(registerX, buttonY, buttonWidth, buttonHeight)) {
                tryRegister()
            }
        }
    }
    
    private fun isMouseOver(x: Float, y: Float, width: Float, height: Float): Boolean {
        val mouseX = Gdx.input.x * (screenWidth / Gdx.graphics.width)
        val mouseY = (Gdx.graphics.height - Gdx.input.y) * (screenHeight / Gdx.graphics.height)
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
    }
    
    private fun tryLogin() {
        if (username.length < 3) {
            statusMessage = "Username must be at least 3 characters"
            statusColor = Color.RED
            return
        }
        
        if (!app.networkClient.isConnected) {
            statusMessage = "Not connected to server"
            statusColor = Color.RED
            return
        }
        
        isConnecting = true
        statusMessage = "Logging in..."
        statusColor = Color.YELLOW
        app.networkClient.login(username, password)
    }
    
    private fun tryRegister() {
        if (username.length < 3) {
            statusMessage = "Username must be at least 3 characters"
            statusColor = Color.RED
            return
        }
        
        if (password.length < 4) {
            statusMessage = "Password must be at least 4 characters"
            statusColor = Color.RED
            return
        }
        
        if (!app.networkClient.isConnected) {
            statusMessage = "Not connected to server"
            statusColor = Color.RED
            return
        }
        
        isConnecting = true
        statusMessage = "Registering..."
        statusColor = Color.YELLOW
        app.networkClient.register(username, password)
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
