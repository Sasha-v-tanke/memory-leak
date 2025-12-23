package com.memoryleak.core

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.memoryleak.core.network.GameNetworkClient
import com.memoryleak.core.ui.LoginScreen
import com.memoryleak.core.ui.MainMenuScreen
import com.memoryleak.core.ui.DeckSelectionScreen
import com.memoryleak.core.ui.GameScreen
import com.memoryleak.core.ui.GameOverScreen
import com.memoryleak.shared.network.PlayerStatsData

/**
 * Main game class that manages screens and global state.
 * Uses LibGDX's Game class for screen management.
 */
class MemoryLeakApp : Game() {
    
    // Network client shared across screens
    lateinit var networkClient: GameNetworkClient
        private set
    
    // Player state
    var playerId: String? = null
    var playerName: String = "Player"
    var playerStats: PlayerStatsData? = null
    var selectedDeck: List<String> = emptyList()
    
    // Current session info
    var sessionId: String? = null
    var opponentName: String? = null
    var isPlayer1: Boolean = true
    var mapWidth: Float = 1600f
    var mapHeight: Float = 800f
    
    override fun create() {
        networkClient = GameNetworkClient(this)
        showLoginScreen()
    }
    
    fun showLoginScreen() {
        setScreen(LoginScreen(this))
    }
    
    fun showMainMenu() {
        setScreen(MainMenuScreen(this))
    }
    
    fun showDeckSelection() {
        setScreen(DeckSelectionScreen(this))
    }
    
    fun showGame() {
        setScreen(GameScreen(this))
    }
    
    fun showGameOver(winnerId: String, youWin: Boolean) {
        setScreen(GameOverScreen(this, winnerId, youWin))
    }
    
    fun onLoginSuccess(playerId: String, name: String, stats: PlayerStatsData?) {
        this.playerId = playerId
        this.playerName = name
        this.playerStats = stats
        showMainMenu()
    }
    
    fun onMatchFound(sessionId: String, opponentName: String, isPlayer1: Boolean, mapWidth: Float, mapHeight: Float) {
        this.sessionId = sessionId
        this.opponentName = opponentName
        this.isPlayer1 = isPlayer1
        this.mapWidth = mapWidth
        this.mapHeight = mapHeight
        showGame()
    }
    
    fun onOpponentDisconnected(youWin: Boolean) {
        showGameOver(if (youWin) playerId ?: "" else "", youWin)
    }
    
    fun onGameOver(winnerId: String) {
        val youWin = winnerId == playerId
        showGameOver(winnerId, youWin)
    }
    
    override fun dispose() {
        networkClient.dispose()
        super.dispose()
    }
}
