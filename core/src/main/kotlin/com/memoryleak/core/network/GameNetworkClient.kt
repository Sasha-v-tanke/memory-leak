package com.memoryleak.core.network

import com.memoryleak.core.MemoryLeakApp
import com.memoryleak.shared.model.GameEntity
import com.memoryleak.shared.model.PlayerState
import com.memoryleak.shared.network.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Network client for game communication.
 * Handles authentication, matchmaking, and game state updates.
 */
class GameNetworkClient(private val app: MemoryLeakApp, private val host: String = "127.0.0.1") {
    private val client = HttpClient {
        install(WebSockets)
    }
    
    // Thread-safe stores for latest state
    val entities = ConcurrentHashMap<String, GameEntity>()
    val players = ConcurrentHashMap<String, PlayerState>()
    val factories = ConcurrentHashMap<String, FactoryState>()

    var myId: String? = null
    var winnerId: String? = null
    var isConnected: Boolean = false
        private set
    var isAuthenticated: Boolean = false
        private set
    var isInMatchmaking: Boolean = false
        private set
    var matchmakingStatus: String = ""
    
    // Callbacks for UI updates
    var onAuthResponse: ((Boolean, String, PlayerStatsData?) -> Unit)? = null
    var onMatchFound: ((MatchFoundPacket) -> Unit)? = null
    var onMatchmakingStatus: ((String) -> Unit)? = null
    var onGameOver: ((String) -> Unit)? = null
    var onOpponentDisconnected: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStateUpdate: (() -> Unit)? = null
    
    private var session: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect() {
        scope.launch {
            try {
                client.webSocket(method = HttpMethod.Get, host = host, port = 8080, path = "/game") {
                    session = this
                    isConnected = true
                    println("Connected to server")
                    
                    while(true) {
                        val frame = incoming.receive()
                        if (frame is Frame.Text) {
                            handleMessage(frame.readText())
                        }
                    }
                }
            } catch (e: Exception) {
                isConnected = false
                println("Connection error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val packet = Json.decodeFromString<Packet>(text)
            
            when(packet) {
                is AuthResponsePacket -> {
                    isAuthenticated = packet.success
                    if (packet.success && packet.playerId != null) {
                        myId = packet.playerId
                    }
                    onAuthResponse?.invoke(packet.success, packet.message, packet.playerStats)
                }
                
                is JoinAckPacket -> {
                    myId = packet.playerId
                    println("My ID is $myId")
                }
                
                is MatchFoundPacket -> {
                    isInMatchmaking = false
                    myId = if (packet.isPlayer1) "player1" else "player2"
                    onMatchFound?.invoke(packet)
                }
                
                is MatchmakingStatusPacket -> {
                    isInMatchmaking = packet.inQueue
                    matchmakingStatus = if (packet.inQueue) "In queue (position ${packet.queuePosition})" else ""
                    onMatchmakingStatus?.invoke(matchmakingStatus)
                }
                
                is StateUpdatePacket -> {
                    // Update local state
                    val currentIds = packet.entities.map { it.id }.toSet()
                    
                    // Update/Add entities
                    packet.entities.forEach { entity ->
                        entities[entity.id] = entity
                    }
                    
                    // Update Players
                    players.clear()
                    packet.players.forEach { player ->
                        players[player.id] = player
                    }
                    
                    // Update Factories
                    factories.clear()
                    packet.factories.forEach { factory ->
                        factories[factory.id] = factory
                    }
                    
                    // Remove old entities
                    entities.keys.filter { !currentIds.contains(it) }.forEach { 
                        entities.remove(it)
                    }
                    
                    onStateUpdate?.invoke()
                }
                
                is GameOverPacket -> {
                    winnerId = packet.winnerId
                    println("Game Over! Winner: ${winnerId ?: "No winner"}")
                    onGameOver?.invoke(packet.winnerId)
                }
                
                is OpponentDisconnectedPacket -> {
                    println("Opponent disconnected: ${packet.message}")
                    onOpponentDisconnected?.invoke(packet.youWin)
                }
                
                is ErrorPacket -> {
                    println("Server error: ${packet.message}")
                    onError?.invoke(packet.message)
                }
                
                is AllCardsResponsePacket -> {
                    // Handle card list for deck building
                    println("Received ${packet.cards.size} card definitions")
                }
                
                is StatsResponsePacket -> {
                    app.playerStats = packet.stats
                }
                
                else -> {
                    println("Received packet type: ${packet::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            println("Message parse error: ${e.message}")
        }
    }

    // Authentication
    fun login(username: String, password: String = "") {
        scope.launch {
            val packet = LoginPacket(username, password)
            val json = Json.encodeToString<Packet>(packet)
            session?.send(Frame.Text(json))
        }
    }
    
    fun register(username: String, password: String = "") {
        scope.launch {
            val packet = RegisterPacket(username, password)
            val json = Json.encodeToString<Packet>(packet)
            session?.send(Frame.Text(json))
        }
    }
    
    // Matchmaking
    fun findMatch(selectedDeck: List<String>) {
        scope.launch {
            isInMatchmaking = true
            val packet = FindMatchPacket(selectedDeck)
            val json = Json.encodeToString<Packet>(packet)
            session?.send(Frame.Text(json))
        }
    }
    
    fun cancelMatchmaking() {
        scope.launch {
            isInMatchmaking = false
            val packet = CancelMatchPacket()
            val json = Json.encodeToString<Packet>(packet)
            session?.send(Frame.Text(json))
        }
    }
    
    // Game commands
    fun sendCommand(cmd: CommandPacket) {
        scope.launch {
            val json = Json.encodeToString<Packet>(cmd)
            session?.send(Frame.Text(json))
        }
    }
    
    // Stats
    fun requestStats() {
        scope.launch {
            val packet = GetStatsPacket()
            val json = Json.encodeToString<Packet>(packet)
            session?.send(Frame.Text(json))
        }
    }
    
    // Deck management
    fun requestAllCards() {
        scope.launch {
            val packet = GetAllCardsPacket()
            val json = Json.encodeToString<Packet>(packet)
            session?.send(Frame.Text(json))
        }
    }
    
    fun clearGameState() {
        entities.clear()
        players.clear()
        factories.clear()
        winnerId = null
    }
    
    fun dispose() {
        client.close()
        scope.cancel()
    }
}
