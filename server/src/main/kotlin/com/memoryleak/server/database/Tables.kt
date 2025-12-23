package com.memoryleak.server.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Database table definitions for Memory Leak game.
 * 
 * Tables:
 * - game_sessions: Active and completed game sessions
 * - match_results: Final results of completed matches
 * - player_stats: Aggregated player statistics
 */

/**
 * Table for storing game sessions.
 * Each row represents a match between two players.
 */
object GameSessionsTable : UUIDTable("game_sessions") {
    val player1Id = varchar("player1_id", 64)
    val player2Id = varchar("player2_id", 64).nullable()
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time").nullable()
    val winnerId = varchar("winner_id", 64).nullable()
    val status = varchar("status", 32).default("waiting") // waiting, active, completed, abandoned
    val mapSeed = integer("map_seed").default(0)
    
    // Game state snapshot (JSON serialized)
    val gameStateSnapshot = text("game_state_snapshot").nullable()
}

/**
 * Table for storing detailed match results.
 */
object MatchResultsTable : UUIDTable("match_results") {
    val sessionId = reference("session_id", GameSessionsTable)
    val playerId = varchar("player_id", 64)
    val isWinner = bool("is_winner")
    
    // End game statistics
    val finalMemory = integer("final_memory").default(0)
    val finalCpu = integer("final_cpu").default(0)
    val unitsCreated = integer("units_created").default(0)
    val unitsLost = integer("units_lost").default(0)
    val unitsKilled = integer("units_killed").default(0)
    val factoriesBuilt = integer("factories_built").default(0)
    val resourcesCaptured = integer("resources_captured").default(0)
    val cardsPlayed = integer("cards_played").default(0)
    val gameDurationSeconds = integer("game_duration_seconds").default(0)
    
    val recordedAt = timestamp("recorded_at")
}

/**
 * Table for storing aggregated player statistics.
 */
object PlayerStatsTable : UUIDTable("player_stats") {
    val playerId = varchar("player_id", 64).uniqueIndex()
    val playerName = varchar("player_name", 128)
    
    // Overall stats
    val totalGames = integer("total_games").default(0)
    val wins = integer("wins").default(0)
    val losses = integer("losses").default(0)
    
    // Aggregate stats
    val totalUnitsCreated = integer("total_units_created").default(0)
    val totalUnitsKilled = integer("total_units_killed").default(0)
    val totalFactoriesBuilt = integer("total_factories_built").default(0)
    val totalCardsPlayed = integer("total_cards_played").default(0)
    val totalPlayTimeSeconds = integer("total_play_time_seconds").default(0)
    
    // Timestamps
    val firstPlayedAt = timestamp("first_played_at")
    val lastPlayedAt = timestamp("last_played_at")
}
