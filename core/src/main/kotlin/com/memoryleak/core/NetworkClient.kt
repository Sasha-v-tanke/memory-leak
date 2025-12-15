package com.memoryleak.core

import com.memoryleak.shared.model.GameEntity
import com.memoryleak.shared.network.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class NetworkClient(private val host: String = "127.0.0.1") {
    private val client = HttpClient {
        install(WebSockets)
    }
    
    // Thread-safe store for latest state
    val entities = ConcurrentHashMap<String, GameEntity>()
    val players = ConcurrentHashMap<String, com.memoryleak.shared.model.PlayerState>()

    var myId: String? = null
    var winnerId: String? = null
    
    private var session: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect() {
        scope.launch {
            try {
                client.webSocket(method = HttpMethod.Get, host = host, port = 8080, path = "/game") {
                    session = this
                    println("Connected to server")
                    
                    while(true) {
                        val frame = incoming.receive()
                        if (frame is Frame.Text) {
                            handleMessage(frame.readText())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val packet = Json.decodeFromString<Packet>(text)
            
            when(packet) {
                is LoginPacket -> { /* Handled by join flow usually, or ignore */ }
                is JoinAckPacket -> {
                    myId = packet.playerId
                    println("My ID is $myId")
                }
                is StateUpdatePacket -> {
                    // Update local state
                    // Simple full sync: remove missing, update existing
                    val currentIds = packet.entities.map { it.id }.toSet()
                    
                    // Update/Add
                    packet.entities.forEach { entity ->
                        entities[entity.id] = entity
                    }
                    
                    // Update Players
                    players.clear()
                    packet.players.forEach { player ->
                        players[player.id] = player
                    }
                    
                    // Remove old (optional for MVP, might flicker)
                    entities.keys.filter { !currentIds.contains(it) }.forEach { 
                        entities.remove(it)
                    }
                }
                is GameOverPacket -> {
                    winnerId = packet.winnerId
                    println("Game Over! Winner: ${winnerId ?: "No winner"}")
                }
                else -> {
                    println("Received unknown packet type: ${packet::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            println("msg error: ${e.message}")
        }
    }

    fun sendCommand(cmd: CommandPacket) {
        scope.launch {
            val json = Json.encodeToString<Packet>(cmd) // use Packet as base for polymorphism
            session?.send(Frame.Text(json))
        }
    }
    
    fun dispose() {
        client.close()
        scope.cancel()
    }
}
