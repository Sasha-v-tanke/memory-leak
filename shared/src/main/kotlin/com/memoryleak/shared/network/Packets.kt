package com.memoryleak.shared.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.memoryleak.shared.model.*

@Serializable
sealed class Packet

@Serializable
@SerialName("login")
data class LoginPacket(val name: String) : Packet()

@Serializable
@SerialName("join_ack")
data class JoinAckPacket(val playerId: String, val mapWidth: Float, val mapHeight: Float) : Packet()

@Serializable
@SerialName("state_update")
data class StateUpdatePacket(
    val entities: List<GameEntity>, 
    val players: List<PlayerState>,
    val serverTime: Long
) : Packet()

@Serializable
@SerialName("command")
data class CommandPacket(
    val commandType: CommandType, 
    val entityId: String? = null, 
    val targetX: Float = 0f, 
    val targetY: Float = 0f,
    val cardId: String? = null  // For PLAY_CARD command
) : Packet()

@Serializable
enum class CommandType {
    MOVE, ATTACK, BUILD, CAPTURE, PLAY_CARD
}

@Serializable
@SerialName("game_over")
data class GameOverPacket(
    val winnerId: String
) : Packet()

