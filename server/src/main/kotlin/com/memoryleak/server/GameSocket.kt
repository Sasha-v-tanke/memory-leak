package com.memoryleak.server

import com.memoryleak.server.database.AuthRepository
import com.memoryleak.server.database.DatabaseConfig
import com.memoryleak.server.database.GameRepository
import com.memoryleak.server.database.PlayerStatsTable
import com.memoryleak.server.game.GameRoom
import com.memoryleak.server.game.MatchmakingManager
import com.memoryleak.shared.network.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.exposed.sql.select
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Player session data
data class PlayerSession(
    val id: String,
    val socket: WebSocketSession,
    var username: String = "",
    var isAuthenticated: Boolean = false,
    var currentGameRoom: GameRoom? = null
)

// Global player sessions
val playerSessions = ConcurrentHashMap<String, PlayerSession>()

// Matchmaking manager
val matchmaking = MatchmakingManager()

fun Route.gameSocket() {
    webSocket("/game") {
        val sessionId = UUID.randomUUID().toString()
        val playerSession = PlayerSession(sessionId, this)
        playerSessions[sessionId] = playerSession
        
        println("Client connected: $sessionId")
        
        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        val packet = Json.decodeFromString<Packet>(text)
                        handlePacket(sessionId, packet, playerSession)
                    } catch (e: Exception) {
                        println("Parse error: ${e.message}")
                        sendError(this, 3001, "Invalid packet format")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error: ${e.localizedMessage}")
        } finally {
            // Cleanup
            playerSession.currentGameRoom?.removePlayer(sessionId)
            matchmaking.removeFromQueue(sessionId)
            playerSessions.remove(sessionId)
            println("Client disconnected: $sessionId")
        }
    }
}

private suspend fun handlePacket(sessionId: String, packet: Packet, session: PlayerSession) {
    when (packet) {
        // Authentication
        is LoginPacket -> handleLogin(sessionId, packet, session)
        is RegisterPacket -> handleRegister(sessionId, packet, session)
        
        // Matchmaking
        is FindMatchPacket -> handleFindMatch(sessionId, packet, session)
        is CancelMatchPacket -> handleCancelMatch(sessionId, session)
        
        // Game commands
        is CommandPacket -> handleCommand(sessionId, packet, session)
        is LeaveGamePacket -> handleLeaveGame(sessionId, packet, session)
        
        // Stats
        is GetStatsPacket -> handleGetStats(sessionId, session)
        
        // Cards
        is GetAllCardsPacket -> handleGetAllCards(sessionId, session)
        
        // Deck management
        is SaveDeckPacket -> handleSaveDeck(sessionId, packet, session)
        is LoadDecksPacket -> handleLoadDecks(sessionId, session)
        
        else -> {
            println("Unhandled packet type: ${packet::class.simpleName}")
        }
    }
}

private suspend fun handleLogin(sessionId: String, packet: LoginPacket, session: PlayerSession) {
    if (packet.username.length < 3) {
        sendPacket(session.socket, AuthResponsePacket(
            success = false,
            message = "Username must be at least 3 characters"
        ))
        return
    }
    
    // Use database authentication if available
    if (DatabaseConfig.isConnected()) {
        val authResult = AuthRepository.login(packet.username, packet.password)
        if (!authResult.success) {
            sendPacket(session.socket, AuthResponsePacket(
                success = false,
                message = authResult.message
            ))
            return
        }
        
        session.username = authResult.nickname ?: packet.username
        session.isAuthenticated = true
        
        // Get player stats from database
        val stats = getPlayerStatsFromDb(authResult.playerId!!)
        
        sendPacket(session.socket, AuthResponsePacket(
            success = true,
            playerId = authResult.playerId,
            message = "Login successful",
            playerStats = stats
        ))
        
        println("Player logged in: ${session.username} (${authResult.playerId})")
    } else {
        // Fallback: Simple login without DB (for development)
        session.username = packet.username
        session.isAuthenticated = true
        
        val stats = PlayerStatsData()
        
        sendPacket(session.socket, AuthResponsePacket(
            success = true,
            playerId = sessionId,
            message = "Login successful (no database)",
            playerStats = stats
        ))
        
        println("Player logged in (no DB): ${packet.username} ($sessionId)")
    }
}

private suspend fun handleRegister(sessionId: String, packet: RegisterPacket, session: PlayerSession) {
    if (packet.username.length < 3) {
        sendPacket(session.socket, AuthResponsePacket(
            success = false,
            message = "Username must be at least 3 characters"
        ))
        return
    }
    
    // Use database registration if available
    if (DatabaseConfig.isConnected()) {
        if (packet.password.length < 4) {
            sendPacket(session.socket, AuthResponsePacket(
                success = false,
                message = "Password must be at least 4 characters"
            ))
            return
        }
        
        // Use username as nickname if not provided separately
        val nickname = packet.username
        val authResult = AuthRepository.register(packet.username, packet.password, nickname)
        
        if (!authResult.success) {
            sendPacket(session.socket, AuthResponsePacket(
                success = false,
                message = authResult.message
            ))
            return
        }
        
        session.username = authResult.nickname ?: packet.username
        session.isAuthenticated = true
        
        val stats = PlayerStatsData()
        
        sendPacket(session.socket, AuthResponsePacket(
            success = true,
            playerId = authResult.playerId,
            message = "Registration successful",
            playerStats = stats
        ))
        
        println("Player registered: ${session.username} (${authResult.playerId})")
    } else {
        // Fallback: Simple registration without DB (for development)
        session.username = packet.username
        session.isAuthenticated = true
        
        val stats = PlayerStatsData()
        
        sendPacket(session.socket, AuthResponsePacket(
            success = true,
            playerId = sessionId,
            message = "Registration successful (no database)",
            playerStats = stats
        ))
        
        println("Player registered (no DB): ${packet.username} ($sessionId)")
    }
}

private fun getPlayerStatsFromDb(playerId: String): PlayerStatsData {
    if (!DatabaseConfig.isConnected()) return PlayerStatsData()
    
    return try {
        org.jetbrains.exposed.sql.transactions.transaction {
            val row = PlayerStatsTable
                .select { PlayerStatsTable.playerId eq playerId }
                .singleOrNull()
            
            if (row != null) {
                PlayerStatsData(
                    totalGames = row[PlayerStatsTable.totalGames],
                    wins = row[PlayerStatsTable.wins],
                    losses = row[PlayerStatsTable.losses],
                    totalUnitsCreated = row[PlayerStatsTable.totalUnitsCreated],
                    totalUnitsKilled = row[PlayerStatsTable.totalUnitsKilled],
                    totalFactoriesBuilt = row[PlayerStatsTable.totalFactoriesBuilt],
                    totalCardsPlayed = row[PlayerStatsTable.totalCardsPlayed],
                    totalPlayTimeSeconds = row[PlayerStatsTable.totalPlayTimeSeconds]
                )
            } else {
                PlayerStatsData()
            }
        }
    } catch (e: Exception) {
        println("[GameSocket] Failed to get player stats: ${e.message}")
        PlayerStatsData()
    }
}

private suspend fun handleFindMatch(sessionId: String, packet: FindMatchPacket, session: PlayerSession) {
    if (!session.isAuthenticated) {
        sendPacket(session.socket, ErrorPacket(1003, "Not authenticated"))
        return
    }
    
    if (session.currentGameRoom != null) {
        sendPacket(session.socket, ErrorPacket(2002, "Already in a game"))
        return
    }
    
    // Add to matchmaking queue
    matchmaking.addToQueue(sessionId, session.username, packet.selectedDeck, session.socket)
    
    sendPacket(session.socket, MatchmakingStatusPacket(inQueue = true, queuePosition = 1))
    
    // Try to match players
    matchmaking.tryMatch { player1, player2 ->
        // Create game room
        val gameRoom = GameRoom()
        gameRoom.start()
        
        // Join both players
        val p1Session = playerSessions[player1.id]
        val p2Session = playerSessions[player2.id]
        
        if (p1Session != null && p2Session != null) {
            p1Session.currentGameRoom = gameRoom
            p2Session.currentGameRoom = gameRoom
            
            gameRoom.join(player1.id, player1.socket, player1.selectedDeck)
            gameRoom.join(player2.id, player2.socket, player2.selectedDeck)
            
            // Notify players
            sendPacket(player1.socket, MatchFoundPacket(
                sessionId = gameRoom.sessionId,
                opponentName = player2.username,
                mapWidth = gameRoom.mapWidth,
                mapHeight = gameRoom.mapHeight,
                isPlayer1 = true
            ))
            
            sendPacket(player2.socket, MatchFoundPacket(
                sessionId = gameRoom.sessionId,
                opponentName = player1.username,
                mapWidth = gameRoom.mapWidth,
                mapHeight = gameRoom.mapHeight,
                isPlayer1 = false
            ))
            
            println("Match created: ${player1.username} vs ${player2.username}")
        }
    }
}

private suspend fun handleCancelMatch(sessionId: String, session: PlayerSession) {
    matchmaking.removeFromQueue(sessionId)
    sendPacket(session.socket, MatchmakingStatusPacket(inQueue = false))
}

private suspend fun handleCommand(sessionId: String, packet: CommandPacket, session: PlayerSession) {
    val gameRoom = session.currentGameRoom
    if (gameRoom == null) {
        sendPacket(session.socket, ErrorPacket(3001, "Not in a game"))
        return
    }
    
    gameRoom.handleCommand(sessionId, packet)
}

private suspend fun handleGetStats(sessionId: String, session: PlayerSession) {
    val stats = PlayerStatsData()  // Would load from DB in full implementation
    sendPacket(session.socket, StatsResponsePacket(stats))
}

private suspend fun handleGetAllCards(sessionId: String, session: PlayerSession) {
    val cards = com.memoryleak.shared.model.CardType.values().mapNotNull { cardType ->
        com.memoryleak.shared.model.UnitStatsData.getCardDefinition(cardType)
    }
    sendPacket(session.socket, AllCardsResponsePacket(cards))
}

private suspend fun handleLeaveGame(sessionId: String, packet: LeaveGamePacket, session: PlayerSession) {
    val gameRoom = session.currentGameRoom
    
    if (gameRoom == null) {
        // Not in a game, just confirm
        sendPacket(session.socket, GameLeftPacket(success = true, message = "Not in a game"))
        return
    }
    
    if (packet.surrender) {
        // Player surrendered - notify opponent they won
        gameRoom.handleSurrender(sessionId)
    }
    
    // Remove player from game room
    gameRoom.removePlayer(sessionId)
    
    // Clear the game room reference
    session.currentGameRoom = null
    
    sendPacket(session.socket, GameLeftPacket(success = true, message = if (packet.surrender) "You surrendered" else "Left game"))
    println("Player $sessionId left game (surrender: ${packet.surrender})")
}

private suspend fun handleSaveDeck(sessionId: String, packet: SaveDeckPacket, session: PlayerSession) {
    if (!session.isAuthenticated) {
        sendPacket(session.socket, ErrorPacket(1003, "Not authenticated"))
        return
    }
    
    // Save deck to database and check result
    if (DatabaseConfig.isConnected()) {
        val success = GameRepository.saveDeck(sessionId, packet.deckName, packet.cardTypes)
        if (!success) {
            sendPacket(session.socket, ErrorPacket(4001, "Failed to save deck"))
            return
        }
    } else {
        sendPacket(session.socket, ErrorPacket(4002, "Database not available"))
        return
    }
    
    // Send updated deck list
    sendPacket(session.socket, DecksResponsePacket(
        decks = GameRepository.getPlayerDecks(sessionId)
    ))
    println("Player $sessionId saved deck: ${packet.deckName}")
}

private suspend fun handleLoadDecks(sessionId: String, session: PlayerSession) {
    if (!session.isAuthenticated) {
        sendPacket(session.socket, ErrorPacket(1003, "Not authenticated"))
        return
    }
    
    val decks = if (DatabaseConfig.isConnected()) GameRepository.getPlayerDecks(sessionId) else emptyList()
    sendPacket(session.socket, DecksResponsePacket(decks = decks))
}

private suspend fun sendPacket(socket: WebSocketSession, packet: Packet) {
    try {
        val json = Json.encodeToString<Packet>(packet)
        socket.send(Frame.Text(json))
    } catch (e: Exception) {
        println("Failed to send packet: ${e.message}")
    }
}

private suspend fun sendError(socket: WebSocketSession, code: Int, message: String) {
    sendPacket(socket, ErrorPacket(code, message))
}

