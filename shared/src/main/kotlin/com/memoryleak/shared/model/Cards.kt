package com.memoryleak.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardType {
    // Legacy cards
    SPAWN_SCOUT,
    SPAWN_TANK,
    SPAWN_RANGED,
    SPAWN_HEALER,
    BUILD_FACTORY,
    
    // Basic Process cards
    SPAWN_ALLOCATOR,
    SPAWN_GARBAGE_COLLECTOR,
    SPAWN_BASIC_PROCESS,
    
    // OOP cards
    SPAWN_INHERITANCE_DRONE,
    SPAWN_POLYMORPH_WARRIOR,
    SPAWN_ENCAPSULATION_SHIELD,
    SPAWN_ABSTRACTION_AGENT,
    
    // Reflection & Metaprogramming cards
    SPAWN_REFLECTION_SPY,
    SPAWN_CODE_INJECTOR,
    SPAWN_DYNAMIC_DISPATCHER,
    
    // Async & Parallelism cards
    SPAWN_COROUTINE_ARCHER,
    SPAWN_PROMISE_KNIGHT,
    SPAWN_DEADLOCK_TRAP,
    
    // Functional Programming cards
    SPAWN_LAMBDA_SNIPER,
    SPAWN_RECURSIVE_BOMB,
    SPAWN_HIGHER_ORDER_COMMANDER,
    
    // Network & Communication cards
    SPAWN_API_GATEWAY,
    SPAWN_WEBSOCKET_SCOUT,
    SPAWN_RESTFUL_HEALER,
    
    // Storage cards
    SPAWN_CACHE_RUNNER,
    SPAWN_INDEXER,
    SPAWN_TRANSACTION_GUARD
}

@Serializable
enum class UnitType {
    // Legacy types (keeping for backward compatibility)
    SCOUT,
    TANK,
    RANGED,
    HEALER,
    
    // Basic Processes
    ALLOCATOR,          // Captures resource nodes, generates Memory passively
    GARBAGE_COLLECTOR,  // Cleans up dead units, returns resources
    BASIC_PROCESS,      // Cheap infantry unit
    
    // OOP Units
    INHERITANCE_DRONE,      // Absorbs stats from dead allies (inheritance)
    POLYMORPH_WARRIOR,      // Changes attack type based on enemy (polymorphism)
    ENCAPSULATION_SHIELD,   // Creates protective barrier (encapsulation)
    ABSTRACTION_AGENT,      // Hides allies from detection (abstraction)
    
    // Reflection & Metaprogramming
    REFLECTION_SPY,     // Scans enemy units, reveals stats
    CODE_INJECTOR,      // Injects bugs into enemy factories
    DYNAMIC_DISPATCHER, // Boosts nearby ally attack speed
    
    // Async & Parallelism
    COROUTINE_ARCHER,   // Fires async arrows that ignore armor
    PROMISE_KNIGHT,     // Deals delayed damage on death
    DEADLOCK_TRAP,      // Immobilizes clustered enemies
    
    // Functional Programming
    LAMBDA_SNIPER,          // One-shot pure function, high damage
    RECURSIVE_BOMB,         // Splits into smaller bombs on death
    HIGHER_ORDER_COMMANDER, // Buffs other units (higher-order function)
    
    // Network & Communication
    API_GATEWAY,        // Extends ally attack range (routing)
    WEBSOCKET_SCOUT,    // Continuously reveals enemy positions
    RESTFUL_HEALER,     // GET=diagnose, POST=heal, PUT=buff, DELETE=cleanse
    
    // Storage Units
    CACHE_RUNNER,       // Very fast, low HP, captures cache nodes
    INDEXER,            // Marks targets for bonus damage
    TRANSACTION_GUARD   // Reverts node capture on death (rollback)
}

@Serializable
data class Card(
    val id: String,
    val type: CardType,
    val memoryCost: Int,
    val cpuCost: Int,
    var cooldownRemaining: Float = 0f  // seconds
)

@Serializable
data class UnitStats(
    val type: UnitType,
    val maxHp: Int,
    val speed: Float,
    val damage: Int,
    val attackRange: Float,
    val attackSpeed: Float  // attacks per second
)

object UnitStatsData {
    // Legacy units (backward compatibility)
    val SCOUT = UnitStats(
        type = UnitType.SCOUT,
        maxHp = 30,
        speed = 150f,
        damage = 5,
        attackRange = 50f,
        attackSpeed = 1.5f
    )
    
    val TANK = UnitStats(
        type = UnitType.TANK,
        maxHp = 150,
        speed = 50f,
        damage = 10,
        attackRange = 50f,
        attackSpeed = 0.8f
    )
    
    val RANGED = UnitStats(
        type = UnitType.RANGED,
        maxHp = 40,
        speed = 100f,
        damage = 15,
        attackRange = 120f,
        attackSpeed = 1.0f
    )
    
    val HEALER = UnitStats(
        type = UnitType.HEALER,
        maxHp = 50,
        speed = 80f,
        damage = 0,
        attackRange = 80f,
        attackSpeed = 2.0f
    )
    
    // === BASIC PROCESSES ===
    val ALLOCATOR = UnitStats(
        type = UnitType.ALLOCATOR,
        maxHp = 40,
        speed = 60f,
        damage = 2,
        attackRange = 30f,
        attackSpeed = 0.5f  // Weak attacker, focuses on capturing
    )
    
    val GARBAGE_COLLECTOR = UnitStats(
        type = UnitType.GARBAGE_COLLECTOR,
        maxHp = 60,
        speed = 70f,
        damage = 8,
        attackRange = 60f,
        attackSpeed = 1.0f
    )
    
    val BASIC_PROCESS = UnitStats(
        type = UnitType.BASIC_PROCESS,
        maxHp = 35,
        speed = 90f,
        damage = 6,
        attackRange = 45f,
        attackSpeed = 1.2f
    )
    
    // === OOP UNITS ===
    val INHERITANCE_DRONE = UnitStats(
        type = UnitType.INHERITANCE_DRONE,
        maxHp = 45,
        speed = 85f,
        damage = 7,
        attackRange = 50f,
        attackSpeed = 1.0f  // Ability: Absorbs stats from dead allies
    )
    
    val POLYMORPH_WARRIOR = UnitStats(
        type = UnitType.POLYMORPH_WARRIOR,
        maxHp = 80,
        speed = 75f,
        damage = 12,  // Changes based on enemy type
        attackRange = 55f,
        attackSpeed = 1.1f
    )
    
    val ENCAPSULATION_SHIELD = UnitStats(
        type = UnitType.ENCAPSULATION_SHIELD,
        maxHp = 100,
        speed = 40f,
        damage = 3,
        attackRange = 40f,
        attackSpeed = 0.5f  // Tank role, creates shield for allies
    )
    
    val ABSTRACTION_AGENT = UnitStats(
        type = UnitType.ABSTRACTION_AGENT,
        maxHp = 35,
        speed = 110f,
        damage = 4,
        attackRange = 50f,
        attackSpeed = 0.8f  // Support: hides allies
    )
    
    // === REFLECTION & METAPROGRAMMING ===
    val REFLECTION_SPY = UnitStats(
        type = UnitType.REFLECTION_SPY,
        maxHp = 25,
        speed = 130f,
        damage = 1,
        attackRange = 100f,
        attackSpeed = 0.3f  // Very weak combat, strong scouting
    )
    
    val CODE_INJECTOR = UnitStats(
        type = UnitType.CODE_INJECTOR,
        maxHp = 50,
        speed = 95f,
        damage = 10,
        attackRange = 70f,
        attackSpeed = 0.7f  // Special ability targets factories
    )
    
    val DYNAMIC_DISPATCHER = UnitStats(
        type = UnitType.DYNAMIC_DISPATCHER,
        maxHp = 55,
        speed = 80f,
        damage = 5,
        attackRange = 50f,
        attackSpeed = 1.5f  // Aura: boosts ally attack speed
    )
    
    // === ASYNC & PARALLELISM ===
    val COROUTINE_ARCHER = UnitStats(
        type = UnitType.COROUTINE_ARCHER,
        maxHp = 38,
        speed = 95f,
        damage = 18,  // High damage, ignores some armor
        attackRange = 130f,
        attackSpeed = 0.9f
    )
    
    val PROMISE_KNIGHT = UnitStats(
        type = UnitType.PROMISE_KNIGHT,
        maxHp = 90,
        speed = 65f,
        damage = 11,
        attackRange = 50f,
        attackSpeed = 1.0f  // On death: delayed AoE damage
    )
    
    val DEADLOCK_TRAP = UnitStats(
        type = UnitType.DEADLOCK_TRAP,
        maxHp = 20,
        speed = 120f,
        damage = 2,
        attackRange = 60f,
        attackSpeed = 0.5f  // Ability: immobilizes clustered enemies
    )
    
    // === FUNCTIONAL PROGRAMMING ===
    val LAMBDA_SNIPER = UnitStats(
        type = UnitType.LAMBDA_SNIPER,
        maxHp = 30,
        speed = 70f,
        damage = 50,  // One-shot ability
        attackRange = 150f,
        attackSpeed = 0.2f  // Very slow, but devastating
    )
    
    val RECURSIVE_BOMB = UnitStats(
        type = UnitType.RECURSIVE_BOMB,
        maxHp = 25,
        speed = 100f,
        damage = 8,
        attackRange = 40f,
        attackSpeed = 1.0f  // Splits into smaller bombs on death
    )
    
    val HIGHER_ORDER_COMMANDER = UnitStats(
        type = UnitType.HIGHER_ORDER_COMMANDER,
        maxHp = 70,
        speed = 60f,
        damage = 6,
        attackRange = 60f,
        attackSpeed = 0.8f  // Buffs nearby units
    )
    
    // === NETWORK & COMMUNICATION ===
    val API_GATEWAY = UnitStats(
        type = UnitType.API_GATEWAY,
        maxHp = 65,
        speed = 50f,
        damage = 4,
        attackRange = 70f,
        attackSpeed = 0.6f  // Extends ally attack range
    )
    
    val WEBSOCKET_SCOUT = UnitStats(
        type = UnitType.WEBSOCKET_SCOUT,
        maxHp = 28,
        speed = 140f,
        damage = 3,
        attackRange = 90f,
        attackSpeed = 0.7f  // Continuously reveals enemy positions
    )
    
    val RESTFUL_HEALER = UnitStats(
        type = UnitType.RESTFUL_HEALER,
        maxHp = 55,
        speed = 85f,
        damage = 0,
        attackRange = 90f,
        attackSpeed = 1.8f  // GET/POST/PUT/DELETE healing logic
    )
    
    // === STORAGE UNITS ===
    val CACHE_RUNNER = UnitStats(
        type = UnitType.CACHE_RUNNER,
        maxHp = 20,
        speed = 180f,  // Fastest unit
        damage = 4,
        attackRange = 35f,
        attackSpeed = 1.5f
    )
    
    val INDEXER = UnitStats(
        type = UnitType.INDEXER,
        maxHp = 42,
        speed = 75f,
        damage = 5,
        attackRange = 80f,
        attackSpeed = 0.9f  // Marks targets for bonus damage
    )
    
    val TRANSACTION_GUARD = UnitStats(
        type = UnitType.TRANSACTION_GUARD,
        maxHp = 75,
        speed = 55f,
        damage = 7,
        attackRange = 50f,
        attackSpeed = 0.8f  // Reverts capture on death
    )
    
    // Helper function to get stats by type
    fun getStats(type: UnitType): UnitStats {
        return when (type) {
            UnitType.SCOUT -> SCOUT
            UnitType.TANK -> TANK
            UnitType.RANGED -> RANGED
            UnitType.HEALER -> HEALER
            UnitType.ALLOCATOR -> ALLOCATOR
            UnitType.GARBAGE_COLLECTOR -> GARBAGE_COLLECTOR
            UnitType.BASIC_PROCESS -> BASIC_PROCESS
            UnitType.INHERITANCE_DRONE -> INHERITANCE_DRONE
            UnitType.POLYMORPH_WARRIOR -> POLYMORPH_WARRIOR
            UnitType.ENCAPSULATION_SHIELD -> ENCAPSULATION_SHIELD
            UnitType.ABSTRACTION_AGENT -> ABSTRACTION_AGENT
            UnitType.REFLECTION_SPY -> REFLECTION_SPY
            UnitType.CODE_INJECTOR -> CODE_INJECTOR
            UnitType.DYNAMIC_DISPATCHER -> DYNAMIC_DISPATCHER
            UnitType.COROUTINE_ARCHER -> COROUTINE_ARCHER
            UnitType.PROMISE_KNIGHT -> PROMISE_KNIGHT
            UnitType.DEADLOCK_TRAP -> DEADLOCK_TRAP
            UnitType.LAMBDA_SNIPER -> LAMBDA_SNIPER
            UnitType.RECURSIVE_BOMB -> RECURSIVE_BOMB
            UnitType.HIGHER_ORDER_COMMANDER -> HIGHER_ORDER_COMMANDER
            UnitType.API_GATEWAY -> API_GATEWAY
            UnitType.WEBSOCKET_SCOUT -> WEBSOCKET_SCOUT
            UnitType.RESTFUL_HEALER -> RESTFUL_HEALER
            UnitType.CACHE_RUNNER -> CACHE_RUNNER
            UnitType.INDEXER -> INDEXER
            UnitType.TRANSACTION_GUARD -> TRANSACTION_GUARD
        }
    }
}

