package com.memoryleak.server.game

import io.ktor.websocket.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages matchmaking queue and player matching.
 */
class MatchmakingManager {
    
    data class QueuedPlayer(
        val id: String,
        val username: String,
        val selectedDeck: List<String>,
        val socket: WebSocketSession,
        val queueTime: Long = System.currentTimeMillis()
    )
    
    private val queue = ConcurrentLinkedQueue<QueuedPlayer>()
    
    fun addToQueue(id: String, username: String, selectedDeck: List<String>, socket: WebSocketSession) {
        // Remove if already in queue
        queue.removeIf { it.id == id }
        queue.add(QueuedPlayer(id, username, selectedDeck, socket))
        println("[Matchmaking] Player $username added to queue. Queue size: ${queue.size}")
    }
    
    fun removeFromQueue(id: String) {
        queue.removeIf { it.id == id }
        println("[Matchmaking] Player $id removed from queue. Queue size: ${queue.size}")
    }
    
    suspend fun tryMatch(onMatch: suspend (QueuedPlayer, QueuedPlayer) -> Unit) {
        if (queue.size >= 2) {
            val player1 = queue.poll()
            val player2 = queue.poll()
            
            if (player1 != null && player2 != null) {
                println("[Matchmaking] Matching ${player1.username} with ${player2.username}")
                onMatch(player1, player2)
            } else {
                // Put back if we couldn't get both
                player1?.let { queue.add(it) }
                player2?.let { queue.add(it) }
            }
        }
    }
    
    fun getQueueSize(): Int = queue.size
    
    fun getQueuePosition(id: String): Int {
        var position = 1
        for (player in queue) {
            if (player.id == id) return position
            position++
        }
        return -1
    }
}
