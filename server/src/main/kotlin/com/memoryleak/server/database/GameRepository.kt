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
    
    /**
     * Convenience method to save deck (delegates to DeckRepository).
     */
    fun saveDeck(playerId: String, deckName: String, cardTypes: List<String>): Boolean {
        return DeckRepository.saveDeck(playerId, deckName, cardTypes)
    }
    
    /**
     * Convenience method to get player decks (delegates to DeckRepository).
     */
    fun getPlayerDecks(playerId: String): List<com.memoryleak.shared.network.SavedDeck> {
        return DeckRepository.getPlayerDecks(playerId)
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

/**
 * Authentication result.
 */
data class AuthResult(
    val success: Boolean,
    val playerId: String? = null,
    val nickname: String? = null,
    val message: String
)

/**
 * Repository for authentication operations.
 */
object AuthRepository {
    
    /**
     * Hash a password using SHA-256 with a salt.
     * Salt is prepended to the hash result: "salt:hash"
     */
    private fun hashPassword(password: String, salt: String = generateSalt()): String {
        val saltedPassword = "$salt$password"
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(saltedPassword.toByteArray())
        val hash = bytes.joinToString("") { "%02x".format(it) }
        return "$salt:$hash"
    }
    
    /**
     * Generate a random salt for password hashing.
     */
    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verify a password against a stored hash (salt:hash format).
     */
    private fun verifyPassword(password: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        return if (parts.size == 2) {
            val salt = parts[0]
            val expectedHash = hashPassword(password, salt)
            expectedHash == storedHash
        } else {
            // Legacy hash without salt - compare directly
            val bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(password.toByteArray())
            val hash = bytes.joinToString("") { "%02x".format(it) }
            hash == storedHash
        }
    }
    
    /**
     * Register a new user account.
     */
    fun register(username: String, password: String, nickname: String): AuthResult {
        if (!DatabaseConfig.isConnected()) {
            return AuthResult(false, message = "Database not available")
        }
        
        if (username.length < 3) {
            return AuthResult(false, message = "Username must be at least 3 characters")
        }
        
        if (password.length < 4) {
            return AuthResult(false, message = "Password must be at least 4 characters")
        }
        
        if (nickname.length < 2) {
            return AuthResult(false, message = "Nickname must be at least 2 characters")
        }
        
        return try {
            transaction {
                // Check if username already exists
                val existing = PlayerAccountsTable
                    .select { PlayerAccountsTable.username eq username }
                    .singleOrNull()
                
                if (existing != null) {
                    return@transaction AuthResult(false, message = "Username already taken")
                }
                
                // Create new account with salted password hash
                val id = PlayerAccountsTable.insertAndGetId {
                    it[PlayerAccountsTable.username] = username
                    it[PlayerAccountsTable.nickname] = nickname
                    it[PlayerAccountsTable.passwordHash] = hashPassword(password)
                    it[createdAt] = Instant.now()
                    it[lastLoginAt] = Instant.now()
                }.value
                
                AuthResult(
                    success = true,
                    playerId = id.toString(),
                    nickname = nickname,
                    message = "Registration successful"
                )
            }
        } catch (e: Exception) {
            println("[AuthRepository] Registration failed: ${e.message}")
            AuthResult(false, message = "Registration failed: ${e.message}")
        }
    }
    
    /**
     * Login with username and password.
     */
    fun login(username: String, password: String): AuthResult {
        if (!DatabaseConfig.isConnected()) {
            return AuthResult(false, message = "Database not available")
        }
        
        return try {
            transaction {
                val account = PlayerAccountsTable
                    .select { PlayerAccountsTable.username eq username }
                    .singleOrNull()
                
                if (account == null) {
                    return@transaction AuthResult(false, message = "User not found")
                }
                
                val storedHash = account[PlayerAccountsTable.passwordHash]
                if (!verifyPassword(password, storedHash)) {
                    return@transaction AuthResult(false, message = "Invalid password")
                }
                
                // Update last login time
                PlayerAccountsTable.update({ PlayerAccountsTable.username eq username }) {
                    it[lastLoginAt] = Instant.now()
                }
                
                AuthResult(
                    success = true,
                    playerId = account[PlayerAccountsTable.id].value.toString(),
                    nickname = account[PlayerAccountsTable.nickname],
                    message = "Login successful"
                )
            }
        } catch (e: Exception) {
            println("[AuthRepository] Login failed: ${e.message}")
            AuthResult(false, message = "Login failed: ${e.message}")
        }
    }
    
    /**
     * Get user's nickname by player ID.
     */
    fun getNickname(playerId: String): String? {
        if (!DatabaseConfig.isConnected()) return null
        
        return try {
            transaction {
                val uuid = UUID.fromString(playerId)
                PlayerAccountsTable
                    .select { PlayerAccountsTable.id eq uuid }
                    .singleOrNull()
                    ?.get(PlayerAccountsTable.nickname)
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Repository for deck operations.
 */
object DeckRepository {
    
    /**
     * Save a deck for a player.
     */
    fun saveDeck(playerId: String, deckName: String, cardTypes: List<String>): Boolean {
        if (!DatabaseConfig.isConnected()) return false
        
        return try {
            transaction {
                // Check if deck with same name exists
                val existing = PlayerDecksTable
                    .select { (PlayerDecksTable.playerId eq playerId) and (PlayerDecksTable.deckName eq deckName) }
                    .singleOrNull()
                
                val cardTypesStr = cardTypes.joinToString(",")
                
                if (existing != null) {
                    // Update existing deck
                    PlayerDecksTable.update({ 
                        (PlayerDecksTable.playerId eq playerId) and (PlayerDecksTable.deckName eq deckName) 
                    }) {
                        it[PlayerDecksTable.cardTypes] = cardTypesStr
                        it[updatedAt] = Instant.now()
                    }
                } else {
                    // Create new deck
                    PlayerDecksTable.insert {
                        it[PlayerDecksTable.playerId] = playerId
                        it[PlayerDecksTable.deckName] = deckName
                        it[PlayerDecksTable.cardTypes] = cardTypesStr
                        it[createdAt] = Instant.now()
                        it[updatedAt] = Instant.now()
                    }
                }
            }
            true
        } catch (e: Exception) {
            println("[DeckRepository] Failed to save deck: ${e.message}")
            false
        }
    }
    
    /**
     * Get all decks for a player.
     */
    fun getPlayerDecks(playerId: String): List<com.memoryleak.shared.network.SavedDeck> {
        if (!DatabaseConfig.isConnected()) return emptyList()
        
        return try {
            transaction {
                PlayerDecksTable
                    .select { PlayerDecksTable.playerId eq playerId }
                    .map { row ->
                        com.memoryleak.shared.network.SavedDeck(
                            id = row[PlayerDecksTable.id].value.toString(),
                            name = row[PlayerDecksTable.deckName],
                            cardTypes = row[PlayerDecksTable.cardTypes].split(",").filter { it.isNotBlank() }
                        )
                    }
            }
        } catch (e: Exception) {
            println("[DeckRepository] Failed to get decks: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Delete a deck.
     */
    fun deleteDeck(playerId: String, deckId: String): Boolean {
        if (!DatabaseConfig.isConnected()) return false
        
        return try {
            transaction {
                val uuid = UUID.fromString(deckId)
                PlayerDecksTable.deleteWhere { 
                    (PlayerDecksTable.id eq uuid) and (PlayerDecksTable.playerId eq playerId)
                }
            }
            true
        } catch (e: Exception) {
            println("[DeckRepository] Failed to delete deck: ${e.message}")
            false
        }
    }
}
