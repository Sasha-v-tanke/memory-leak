package com.memoryleak.shared.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.memoryleak.shared.model.*

@Serializable
sealed class Packet

// ============================================================================
// AUTHENTICATION PACKETS
// ============================================================================

@Serializable
@SerialName("login")
data class LoginPacket(
    val username: String,
    val password: String = ""  // Simple auth for now
) : Packet()

@Serializable
@SerialName("register")
data class RegisterPacket(
    val username: String,
    val password: String = ""
) : Packet()

@Serializable
@SerialName("auth_response")
data class AuthResponsePacket(
    val success: Boolean,
    val playerId: String? = null,
    val message: String,
    val playerStats: PlayerStatsData? = null
) : Packet()

// ============================================================================
// MATCHMAKING PACKETS
// ============================================================================

@Serializable
@SerialName("find_match")
data class FindMatchPacket(
    val selectedDeck: List<String> = emptyList()  // Card type names
) : Packet()

@Serializable
@SerialName("cancel_match")
class CancelMatchPacket : Packet()

@Serializable
@SerialName("match_found")
data class MatchFoundPacket(
    val sessionId: String,
    val opponentName: String,
    val mapWidth: Float,
    val mapHeight: Float,
    val isPlayer1: Boolean
) : Packet()

@Serializable
@SerialName("matchmaking_status")
data class MatchmakingStatusPacket(
    val inQueue: Boolean,
    val queuePosition: Int = 0,
    val estimatedWait: Int = 0  // seconds
) : Packet()

// ============================================================================
// GAME SESSION PACKETS
// ============================================================================

@Serializable
@SerialName("join_ack")
data class JoinAckPacket(
    val playerId: String, 
    val mapWidth: Float, 
    val mapHeight: Float
) : Packet()

@Serializable
@SerialName("state_update")
data class StateUpdatePacket(
    val entities: List<GameEntity>, 
    val players: List<PlayerState>,
    val factories: List<FactoryState> = emptyList(),
    val serverTime: Long
) : Packet()

@Serializable
@SerialName("command")
data class CommandPacket(
    val commandType: CommandType, 
    val entityId: String? = null, 
    val targetX: Float = 0f, 
    val targetY: Float = 0f,
    val cardId: String? = null,  // For PLAY_CARD command
    val targetEntityId: String? = null  // Target entity for unit to pursue
) : Packet()

@Serializable
enum class CommandType {
    MOVE, ATTACK, BUILD, CAPTURE, PLAY_CARD, BUILD_FACTORY
}

@Serializable
@SerialName("game_over")
data class GameOverPacket(
    val winnerId: String,
    val gameStats: GameEndStats? = null
) : Packet()

@Serializable
@SerialName("opponent_disconnected")
data class OpponentDisconnectedPacket(
    val message: String = "Opponent disconnected",
    val youWin: Boolean = true
) : Packet()

// ============================================================================
// STATISTICS PACKETS
// ============================================================================

@Serializable
@SerialName("get_stats")
class GetStatsPacket : Packet()

@Serializable
@SerialName("stats_response")
data class StatsResponsePacket(
    val stats: PlayerStatsData
) : Packet()

// ============================================================================
// DECK MANAGEMENT PACKETS
// ============================================================================

@Serializable
@SerialName("get_all_cards")
class GetAllCardsPacket : Packet()

@Serializable
@SerialName("all_cards_response")
data class AllCardsResponsePacket(
    val cards: List<CardDefinition>
) : Packet()

@Serializable
@SerialName("save_deck")
data class SaveDeckPacket(
    val deckName: String,
    val cardTypes: List<String>  // CardType enum names
) : Packet()

@Serializable
@SerialName("load_decks")
class LoadDecksPacket : Packet()

@Serializable
@SerialName("decks_response")
data class DecksResponsePacket(
    val decks: List<SavedDeck>
) : Packet()

// ============================================================================
// ERROR PACKET
// ============================================================================

@Serializable
@SerialName("error")
data class ErrorPacket(
    val code: Int,
    val message: String
) : Packet()

// ============================================================================
// DATA CLASSES FOR PACKETS
// ============================================================================

@Serializable
data class PlayerStatsData(
    val totalGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val totalUnitsCreated: Int = 0,
    val totalUnitsKilled: Int = 0,
    val totalFactoriesBuilt: Int = 0,
    val totalCardsPlayed: Int = 0,
    val totalPlayTimeSeconds: Int = 0
)

@Serializable
data class GameEndStats(
    val gameDurationSeconds: Int,
    val yourUnitsCreated: Int,
    val yourUnitsKilled: Int,
    val enemyUnitsKilled: Int,
    val factoriesBuilt: Int,
    val cardsPlayed: Int
)

@Serializable
data class CardDefinition(
    val type: CardType,
    val name: String,
    val description: String,
    val memoryCost: Int,
    val cpuCost: Int,
    val category: String,
    val productionTime: Float = 3f  // seconds
)

@Serializable
data class SavedDeck(
    val id: String,
    val name: String,
    val cardTypes: List<String>
)

@Serializable
data class FactoryState(
    val id: String,
    val ownerId: String,
    val factoryType: FactoryType,
    val productionQueue: List<QueuedUnit>,
    val x: Float,
    val y: Float
)

@Serializable
data class QueuedUnit(
    val unitType: UnitType,
    val remainingTime: Float,  // seconds
    val targetX: Float,
    val targetY: Float,
    val targetEntityId: String? = null
)

@Serializable
enum class FactoryType {
    STANDARD,       // Balanced production
    COMPILER,       // Slower but stronger units
    INTERPRETER,    // Faster but weaker units  
    INHERITANCE     // Combines units for upgrades
}

