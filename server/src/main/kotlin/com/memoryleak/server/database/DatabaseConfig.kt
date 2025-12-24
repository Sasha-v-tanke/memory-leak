package com.memoryleak.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Database configuration and initialization.
 * Manages PostgreSQL connection pool using HikariCP.
 */
object DatabaseConfig {
    
    private var dataSource: HikariDataSource? = null
    
    /**
     * Initialize database connection.
     * Creates tables if they don't exist.
     * 
     * Environment variables:
     * - DB_HOST: Database host (default: localhost)
     * - DB_PORT: Database port (default: 5432)
     * - DB_NAME: Database name (default: memoryleak)
     * - DB_USER: Database user (default: postgres)
     * - DB_PASSWORD: Database password (default: postgres)
     * - DB_ENABLED: Enable database persistence (default: false)
     */
    fun init() {
        val enabled = System.getenv("DB_ENABLED")?.toBoolean() ?: false
        
        if (!enabled) {
            println("[Database] Database persistence is disabled. Set DB_ENABLED=true to enable.")
            return
        }
        
        val host = System.getenv("DB_HOST") ?: "localhost"
        val port = System.getenv("DB_PORT")?.toIntOrNull() ?: 5432
        val dbName = System.getenv("DB_NAME") ?: "memoryleak"
        val user = System.getenv("DB_USER") ?: "postgres"
        val password = System.getenv("DB_PASSWORD") ?: "postgres"
        
        val jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
        
        try {
            val config = HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = user
                this.password = password
                this.driverClassName = "org.postgresql.Driver"
                this.maximumPoolSize = 10
                this.minimumIdle = 2
                this.idleTimeout = 60000
                this.connectionTimeout = 10000
                this.maxLifetime = 1800000
                this.isAutoCommit = false
                this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                this.validate()
            }
            
            dataSource = HikariDataSource(config)
            Database.connect(dataSource!!)
            
            // Create tables
            transaction {
                SchemaUtils.create(
                    PlayerAccountsTable,
                    PlayerDecksTable,
                    GameSessionsTable,
                    MatchResultsTable,
                    PlayerStatsTable
                )
            }
            
            println("[Database] Connected to PostgreSQL at $jdbcUrl")
        } catch (e: Exception) {
            println("[Database] Failed to connect to PostgreSQL: ${e.message}")
            println("[Database] Game will continue without persistence.")
        }
    }
    
    /**
     * Check if database is connected and available.
     */
    fun isConnected(): Boolean = dataSource?.isClosed == false
    
    /**
     * Close database connections gracefully.
     */
    fun close() {
        dataSource?.close()
        println("[Database] Connection pool closed.")
    }
}
