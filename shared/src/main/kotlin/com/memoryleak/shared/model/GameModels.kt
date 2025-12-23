package com.memoryleak.shared.model

import kotlinx.serialization.Serializable

enum class EntityType {
    INSTANCE, FACTORY, UNIT, RESOURCE_NODE
}

enum class AIState {
    IDLE,           // No target, looking for one
    MOVING_TO_TARGET,  // Moving towards enemy
    ATTACKING,      // In range, attacking
    USING_ABILITY,  // Executing special ability
    RETREATING      // Low HP, moving away
}

enum class ResourceType {
    MEMORY, CPU
}

@Serializable
data class GameEntity(
    val id: String,
    val type: EntityType,
    var x: Float,
    var y: Float,
    var ownerId: String, // "0" for neutral
    var hp: Int,
    val maxHp: Int,
    // Specific fields using nullable props for MVP simplicity
    var resourceType: ResourceType? = null,
    var resourceAmount: Int = 0,
    var attackingTargetId: String? = null,
    // Movement
    var targetX: Float? = null,
    var targetY: Float? = null,
    var speed: Float = 100f,  // pixels per second
    var unitType: UnitType? = null,  // For typed units
    var lastAttackTime: Long = 0L,    // For attack cooldown
    // AI State for autonomous units
    var aiState: AIState = AIState.IDLE,
    var targetEnemyId: String? = null,  // Current AI target
    var lastAbilityTime: Long = 0L,     // For ability cooldown
    var abilityData: String = "",        // JSON for unit-specific state
    var inheritedStats: String = "",     // For InheritanceDrone (stores absorbed stats)
    // Factory specific
    var factoryType: com.memoryleak.shared.network.FactoryType? = null
)

@Serializable
data class PlayerState(
    val id: String,
    val name: String,
    var memory: Int = 0,
    var cpu: Int = 0,
    var deck: MutableList<Card> = mutableListOf(),
    var hand: MutableList<Card> = mutableListOf(),
    var discardPile: MutableList<Card> = mutableListOf(),
    var globalCooldown: Float = 0f, // Seconds until next card can be played
    var selectedDeckTypes: MutableList<String> = mutableListOf()  // Selected deck card types
)

@Serializable
data class GameState(
    val entities: MutableMap<String, GameEntity>,
    val players: MutableMap<String, PlayerState>,
    var serverTime: Long = 0
)

// Production queue item for factories
@Serializable
data class ProductionQueueItem(
    val unitType: UnitType,
    var remainingTime: Float,  // Seconds until completion
    val targetX: Float,
    val targetY: Float,
    val targetEntityId: String? = null  // Target for the unit to pursue after spawn
)
