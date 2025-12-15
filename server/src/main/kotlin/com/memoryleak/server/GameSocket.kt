package com.memoryleak.server

import com.memoryleak.server.game.GameRoom
import com.memoryleak.shared.network.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.channels.consumeEach
import java.util.UUID

val gameRoom = GameRoom()

fun Route.gameSocket() {
    gameRoom.start() // Ensure loop is running

    webSocket("/game") {
        val sessionId = UUID.randomUUID().toString()
        println("Client connected: $sessionId")
        
        try {
            val player = gameRoom.join(sessionId, this)
            // Send Join Ack
            val joinPacket = JoinAckPacket(sessionId, 800f, 600f)
            send(Frame.Text(Json.encodeToString<Packet>(joinPacket)))
            
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        // Decode as base Packet to handle polymorphic "type" field correctly
                        val packet = Json.decodeFromString<Packet>(text)
                        
                        if (packet is CommandPacket) {
                            gameRoom.handleCommand(sessionId, packet)
                        }
                    } catch (e: Exception) {
                        println("Parse error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error: ${e.localizedMessage}")
        } finally {
            gameRoom.removePlayer(sessionId)
            println("Client disconnected: $sessionId")
        }
    }
}

