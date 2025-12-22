package com.memoryleak.server.database

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

/**
 * Repository for game session operations.
 * Handles persistence of game sessions and match results.
 */
object GameRepository {
    
    /**
     * Create a new game session.
     * @param player1Id First player ID
     * @return Session ID
     */
    fun createSession(player1Id: String): UUID? {
        if (!DatabaseConfig.isConnected()) return null
        
        return try {
            transaction {
                GameSessionsTable.insertAndGetId {
                    it[GameSessionsTable.player1Id] = player1Id
                    it[startTime] = Instant.now()
                    it[status] = "waiting"
                    it[mapSeed] = (0..Int.MAX_VALUE).random()
                }.value
            }
        } catch (e: Exception) {
            println("[GameRepository] Failed to create session: ${e.message}")
            null
        }
    }
    
    /**
     * Add second player to a waiting session.
     * @param sessionId Session ID
     * @param player2Id Second player ID
     * @return true if successful
     */
    fun joinSession(sessionId: UUID, player2Id: String): Boolean {
        if (!DatabaseConfig.isConnected()) return false
        
        return try {
            transaction {
                GameSessionsTable.update({ GameSessionsTable.id eq sessionId }) {
                    it[GameSessionsTable.player2Id] = player2Id
                    it[status] = "active"
                }
            }
            true
        } catch (e: Exception) {
            println("[GameRepository] Failed to join session: ${e.message}")
            false
        }
    }
    
    /**
     * Complete a game session.
     * @param sessionId Session ID
     * @param winnerId Winner player ID
     */
    fun completeSession(sessionId: UUID, winnerId: String) {
        if (!DatabaseConfig.isConnected()) return
        
        try {
            transaction {
                GameSessionsTable.update({ GameSessionsTable.id eq sessionId }) {
                    it[GameSessionsTable.winnerId] = winnerId
                    it[endTime] = Instant.now()
                    it[status] = "completed"
                }
            }
        } catch (e: Exception) {
            println("[GameRepository] Failed to complete session: ${e.message}")
        }
    }
    
    /**
     * Save game state snapshot for potential recovery.
     * @param sessionId Session ID
     * @param stateJson Serialized game state
     */
    fun saveGameState(sessionId: UUID, stateJson: String) {
        if (!DatabaseConfig.isConnected()) return
        
        try {
            transaction {
                GameSessionsTable.update({ GameSessionsTable.id eq sessionId }) {
                    it[gameStateSnapshot] = stateJson
                }
            }
        } catch (e: Exception) {
            println("[GameRepository] Failed to save game state: ${e.message}")
        }
    }
    
    /**
     * Record detailed match result for a player.
     */
    fun recordMatchResult(
        sessionId: UUID,
        playerId: String,
        isWinner: Boolean,
        finalMemory: Int,
        finalCpu: Int,
        unitsCreated: Int,
        unitsLost: Int,
        unitsKilled: Int,
        factoriesBuilt: Int,
        resourcesCaptured: Int,
        cardsPlayed: Int,
        gameDurationSeconds: Int
    ) {
        if (!DatabaseConfig.isConnected()) return
        
        try {
            transaction {
                MatchResultsTable.insert {
                    it[MatchResultsTable.sessionId] = sessionId
                    it[MatchResultsTable.playerId] = playerId
                    it[MatchResultsTable.isWinner] = isWinner
                    it[MatchResultsTable.finalMemory] = finalMemory
                    it[MatchResultsTable.finalCpu] = finalCpu
                    it[MatchResultsTable.unitsCreated] = unitsCreated
                    it[MatchResultsTable.unitsLost] = unitsLost
                    it[MatchResultsTable.unitsKilled] = unitsKilled
                    it[MatchResultsTable.factoriesBuilt] = factoriesBuilt
                    it[MatchResultsTable.resourcesCaptured] = resourcesCaptured
                    it[MatchResultsTable.cardsPlayed] = cardsPlayed
                    it[MatchResultsTable.gameDurationSeconds] = gameDurationSeconds
                    it[recordedAt] = Instant.now()
                }
            }
        } catch (e: Exception) {
            println("[GameRepository] Failed to record match result: ${e.message}")
        }
    }
    
    /**
     * Get or create player stats entry.
     */
    fun getOrCreatePlayerStats(playerId: String, playerName: String): UUID? {
        if (!DatabaseConfig.isConnected()) return null
        
        return try {
            transaction {
                val existing = PlayerStatsTable
                    .select { PlayerStatsTable.playerId eq playerId }
                    .singleOrNull()
                
                if (existing != null) {
                    existing[PlayerStatsTable.id].value
                } else {
                    PlayerStatsTable.insertAndGetId {
                        it[PlayerStatsTable.playerId] = playerId
                        it[PlayerStatsTable.playerName] = playerName
                        it[firstPlayedAt] = Instant.now()
                        it[lastPlayedAt] = Instant.now()
                    }.value
                }
            }
        } catch (e: Exception) {
            println("[GameRepository] Failed to get/create player stats: ${e.message}")
            null
        }
    }
    
    /**
     * Update player stats after a match.
     */
    fun updatePlayerStats(
        playerId: String,
        won: Boolean,
        unitsCreated: Int,
        unitsKilled: Int,
        factoriesBuilt: Int,
        cardsPlayed: Int,
        playTimeSeconds: Int
    ) {
        if (!DatabaseConfig.isConnected()) return
        
        try {
            transaction {
                // First get current stats
                val current = PlayerStatsTable
                    .select { PlayerStatsTable.playerId eq playerId }
                    .singleOrNull() ?: return@transaction
                
                // Then update with incremented values
                PlayerStatsTable.update({ PlayerStatsTable.playerId eq playerId }) {
                    it[totalGames] = current[totalGames] + 1
                    it[wins] = current[wins] + if (won) 1 else 0
                    it[losses] = current[losses] + if (won) 0 else 1
                    it[totalUnitsCreated] = current[totalUnitsCreated] + unitsCreated
                    it[totalUnitsKilled] = current[totalUnitsKilled] + unitsKilled
                    it[totalFactoriesBuilt] = current[totalFactoriesBuilt] + factoriesBuilt
                    it[totalCardsPlayed] = current[totalCardsPlayed] + cardsPlayed
                    it[totalPlayTimeSeconds] = current[totalPlayTimeSeconds] + playTimeSeconds
                    it[lastPlayedAt] = Instant.now()
                }
            }
        } catch (e: Exception) {
            println("[GameRepository] Failed to update player stats: ${e.message}")
        }
    }
    
    /**
     * Mark session as abandoned when player disconnects.
     */
    fun abandonSession(sessionId: UUID, disconnectedPlayerId: String) {
        if (!DatabaseConfig.isConnected()) return
        
        try {
            transaction {
                val session = GameSessionsTable
                    .select { GameSessionsTable.id eq sessionId }
                    .singleOrNull() ?: return@transaction
                
                // Determine winner (the player who didn't disconnect)
                val p1Id: String = session[GameSessionsTable.player1Id]
                val p2Id: String? = session[GameSessionsTable.player2Id]
                val winnerId: String? = when (disconnectedPlayerId) {
                    p1Id -> p2Id
                    p2Id -> p1Id
                    else -> null
                }
                
                GameSessionsTable.update({ GameSessionsTable.id eq sessionId }) {
                    it[GameSessionsTable.winnerId] = winnerId
                    it[endTime] = Instant.now()
                    it[status] = "abandoned"
                }
            }
        } catch (e: Exception) {
            println("[GameRepository] Failed to abandon session: ${e.message}")
        }
    }
    
    /**
     * Get recent match history for a player.
     */
    fun getPlayerMatchHistory(playerId: String, limit: Int = 10): List<MatchHistoryEntry> {
        if (!DatabaseConfig.isConnected()) return emptyList()
        
        return try {
            transaction {
                MatchResultsTable
                    .select { MatchResultsTable.playerId eq playerId }
                    .orderBy(MatchResultsTable.recordedAt, SortOrder.DESC)
                    .limit(limit)
                    .map { row: ResultRow ->
                        MatchHistoryEntry(
                            sessionId = row[MatchResultsTable.sessionId].value.toString(),
                            isWinner = row[MatchResultsTable.isWinner],
                            unitsKilled = row[MatchResultsTable.unitsKilled],
                            gameDurationSeconds = row[MatchResultsTable.gameDurationSeconds],
                            recordedAt = row[MatchResultsTable.recordedAt].toEpochMilli()
                        )
                    }
            }
        } catch (e: Exception) {
            println("[GameRepository] Failed to get match history: ${e.message}")
            emptyList()
        }
    }
}

/**
 * Simple match history entry for API responses.
 */
@Serializable
data class MatchHistoryEntry(
    val sessionId: String,
    val isWinner: Boolean,
    val unitsKilled: Int,
    val gameDurationSeconds: Int,
    val recordedAt: Long
)
