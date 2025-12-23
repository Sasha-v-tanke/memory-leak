package com.memoryleak.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardType {
    // Legacy cards
    SPAWN_SCOUT,
    SPAWN_TANK,
    SPAWN_RANGED,
    SPAWN_HEALER,
    
    // Factory cards (always available)
    BUILD_FACTORY,
    BUILD_COMPILER_FACTORY,
    BUILD_INTERPRETER_FACTORY,
    BUILD_INHERITANCE_FACTORY,
    
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
    SPAWN_TRANSACTION_GUARD,
    
    // Memory Management cards
    SPAWN_POINTER,
    SPAWN_BUFFER,
    
    // Safety & Type cards
    SPAWN_ASSERT,
    SPAWN_STATIC_CAST,
    SPAWN_DYNAMIC_CAST,
    
    // Concurrency cards
    SPAWN_MUTEX_GUARDIAN,
    SPAWN_SEMAPHORE_CONTROLLER,
    SPAWN_THREAD_POOL,
    
    // Inheritance card (for upgrade factories)
    UPGRADE_INHERITANCE
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
    TRANSACTION_GUARD,  // Reverts node capture on death (rollback)
    
    // Memory Management Units
    POINTER,            // Teleport striker - can jump to targets
    BUFFER,             // Damage absorber - protects allies
    
    // Safety & Type Units
    ASSERT,             // Executes low HP enemies instantly
    STATIC_CAST,        // Converts enemy buffs to debuffs
    DYNAMIC_CAST,       // Risky high damage (50% chance)
    
    // Concurrency Units
    MUTEX_GUARDIAN,     // Locks single enemy
    SEMAPHORE_CONTROLLER, // Limits attackers in area
    THREAD_POOL,        // Spawns worker units
    
    // Worker unit (spawned by Thread Pool)
    WORKER_THREAD
}

@Serializable
data class Card(
    val id: String,
    val type: CardType,
    val memoryCost: Int,
    val cpuCost: Int,
    var cooldownRemaining: Float = 0f,  // seconds
    val productionTime: Float = 3f       // seconds to produce
)

@Serializable
data class UnitStats(
    val type: UnitType,
    val maxHp: Int,
    val speed: Float,
    val damage: Int,
    val attackRange: Float,
    val attackSpeed: Float,  // attacks per second
    val productionTime: Float = 3f  // seconds to build
)

object UnitStatsData {
    // Legacy units (backward compatibility)
    val SCOUT = UnitStats(
        type = UnitType.SCOUT,
        maxHp = 30,
        speed = 150f,
        damage = 5,
        attackRange = 50f,
        attackSpeed = 1.5f,
        productionTime = 2f
    )
    
    val TANK = UnitStats(
        type = UnitType.TANK,
        maxHp = 150,
        speed = 50f,
        damage = 10,
        attackRange = 50f,
        attackSpeed = 0.8f,
        productionTime = 5f
    )
    
    val RANGED = UnitStats(
        type = UnitType.RANGED,
        maxHp = 40,
        speed = 100f,
        damage = 15,
        attackRange = 120f,
        attackSpeed = 1.0f,
        productionTime = 3f
    )
    
    val HEALER = UnitStats(
        type = UnitType.HEALER,
        maxHp = 50,
        speed = 80f,
        damage = 0,
        attackRange = 80f,
        attackSpeed = 2.0f,
        productionTime = 4f
    )
    
    // === BASIC PROCESSES ===
    val ALLOCATOR = UnitStats(
        type = UnitType.ALLOCATOR,
        maxHp = 40,
        speed = 60f,
        damage = 2,
        attackRange = 30f,
        attackSpeed = 0.5f,
        productionTime = 2f
    )
    
    val GARBAGE_COLLECTOR = UnitStats(
        type = UnitType.GARBAGE_COLLECTOR,
        maxHp = 60,
        speed = 70f,
        damage = 8,
        attackRange = 60f,
        attackSpeed = 1.0f,
        productionTime = 3f
    )
    
    val BASIC_PROCESS = UnitStats(
        type = UnitType.BASIC_PROCESS,
        maxHp = 35,
        speed = 90f,
        damage = 6,
        attackRange = 45f,
        attackSpeed = 1.2f,
        productionTime = 2f
    )
    
    // === OOP UNITS ===
    val INHERITANCE_DRONE = UnitStats(
        type = UnitType.INHERITANCE_DRONE,
        maxHp = 45,
        speed = 85f,
        damage = 7,
        attackRange = 50f,
        attackSpeed = 1.0f,
        productionTime = 3f
    )
    
    val POLYMORPH_WARRIOR = UnitStats(
        type = UnitType.POLYMORPH_WARRIOR,
        maxHp = 80,
        speed = 75f,
        damage = 12,
        attackRange = 55f,
        attackSpeed = 1.1f,
        productionTime = 4f
    )
    
    val ENCAPSULATION_SHIELD = UnitStats(
        type = UnitType.ENCAPSULATION_SHIELD,
        maxHp = 100,
        speed = 40f,
        damage = 3,
        attackRange = 40f,
        attackSpeed = 0.5f,
        productionTime = 5f
    )
    
    val ABSTRACTION_AGENT = UnitStats(
        type = UnitType.ABSTRACTION_AGENT,
        maxHp = 35,
        speed = 110f,
        damage = 4,
        attackRange = 50f,
        attackSpeed = 0.8f,
        productionTime = 3f
    )
    
    // === REFLECTION & METAPROGRAMMING ===
    val REFLECTION_SPY = UnitStats(
        type = UnitType.REFLECTION_SPY,
        maxHp = 25,
        speed = 130f,
        damage = 1,
        attackRange = 100f,
        attackSpeed = 0.3f,
        productionTime = 2f
    )
    
    val CODE_INJECTOR = UnitStats(
        type = UnitType.CODE_INJECTOR,
        maxHp = 50,
        speed = 95f,
        damage = 10,
        attackRange = 70f,
        attackSpeed = 0.7f,
        productionTime = 4f
    )
    
    val DYNAMIC_DISPATCHER = UnitStats(
        type = UnitType.DYNAMIC_DISPATCHER,
        maxHp = 55,
        speed = 80f,
        damage = 5,
        attackRange = 50f,
        attackSpeed = 1.5f,
        productionTime = 3f
    )
    
    // === ASYNC & PARALLELISM ===
    val COROUTINE_ARCHER = UnitStats(
        type = UnitType.COROUTINE_ARCHER,
        maxHp = 38,
        speed = 95f,
        damage = 18,
        attackRange = 130f,
        attackSpeed = 0.9f,
        productionTime = 4f
    )
    
    val PROMISE_KNIGHT = UnitStats(
        type = UnitType.PROMISE_KNIGHT,
        maxHp = 90,
        speed = 65f,
        damage = 11,
        attackRange = 50f,
        attackSpeed = 1.0f,
        productionTime = 5f
    )
    
    val DEADLOCK_TRAP = UnitStats(
        type = UnitType.DEADLOCK_TRAP,
        maxHp = 20,
        speed = 120f,
        damage = 2,
        attackRange = 60f,
        attackSpeed = 0.5f,
        productionTime = 2f
    )
    
    // === FUNCTIONAL PROGRAMMING ===
    val LAMBDA_SNIPER = UnitStats(
        type = UnitType.LAMBDA_SNIPER,
        maxHp = 30,
        speed = 70f,
        damage = 50,
        attackRange = 150f,
        attackSpeed = 0.2f,
        productionTime = 6f
    )
    
    val RECURSIVE_BOMB = UnitStats(
        type = UnitType.RECURSIVE_BOMB,
        maxHp = 25,
        speed = 100f,
        damage = 8,
        attackRange = 40f,
        attackSpeed = 1.0f,
        productionTime = 3f
    )
    
    val HIGHER_ORDER_COMMANDER = UnitStats(
        type = UnitType.HIGHER_ORDER_COMMANDER,
        maxHp = 70,
        speed = 60f,
        damage = 6,
        attackRange = 60f,
        attackSpeed = 0.8f,
        productionTime = 5f
    )
    
    // === NETWORK & COMMUNICATION ===
    val API_GATEWAY = UnitStats(
        type = UnitType.API_GATEWAY,
        maxHp = 65,
        speed = 50f,
        damage = 4,
        attackRange = 70f,
        attackSpeed = 0.6f,
        productionTime = 4f
    )
    
    val WEBSOCKET_SCOUT = UnitStats(
        type = UnitType.WEBSOCKET_SCOUT,
        maxHp = 28,
        speed = 140f,
        damage = 3,
        attackRange = 90f,
        attackSpeed = 0.7f,
        productionTime = 2f
    )
    
    val RESTFUL_HEALER = UnitStats(
        type = UnitType.RESTFUL_HEALER,
        maxHp = 55,
        speed = 85f,
        damage = 0,
        attackRange = 90f,
        attackSpeed = 1.8f,
        productionTime = 4f
    )
    
    // === STORAGE UNITS ===
    val CACHE_RUNNER = UnitStats(
        type = UnitType.CACHE_RUNNER,
        maxHp = 20,
        speed = 180f,
        damage = 4,
        attackRange = 35f,
        attackSpeed = 1.5f,
        productionTime = 1f
    )
    
    val INDEXER = UnitStats(
        type = UnitType.INDEXER,
        maxHp = 42,
        speed = 75f,
        damage = 5,
        attackRange = 80f,
        attackSpeed = 0.9f,
        productionTime = 3f
    )
    
    val TRANSACTION_GUARD = UnitStats(
        type = UnitType.TRANSACTION_GUARD,
        maxHp = 75,
        speed = 55f,
        damage = 7,
        attackRange = 50f,
        attackSpeed = 0.8f,
        productionTime = 4f
    )
    
    // === MEMORY MANAGEMENT ===
    val POINTER = UnitStats(
        type = UnitType.POINTER,
        maxHp = 35,
        speed = 80f,
        damage = 15,
        attackRange = 40f,
        attackSpeed = 1.0f,
        productionTime = 3f
    )
    
    val BUFFER = UnitStats(
        type = UnitType.BUFFER,
        maxHp = 80,
        speed = 45f,
        damage = 0,
        attackRange = 30f,
        attackSpeed = 0f,
        productionTime = 4f
    )
    
    // === SAFETY & TYPE ===
    val ASSERT = UnitStats(
        type = UnitType.ASSERT,
        maxHp = 40,
        speed = 100f,
        damage = 3,
        attackRange = 60f,
        attackSpeed = 1.2f,
        productionTime = 2f
    )
    
    val STATIC_CAST = UnitStats(
        type = UnitType.STATIC_CAST,
        maxHp = 50,
        speed = 70f,
        damage = 8,
        attackRange = 50f,
        attackSpeed = 0.9f,
        productionTime = 3f
    )
    
    val DYNAMIC_CAST = UnitStats(
        type = UnitType.DYNAMIC_CAST,
        maxHp = 45,
        speed = 85f,
        damage = 20,
        attackRange = 55f,
        attackSpeed = 0.8f,
        productionTime = 3f
    )
    
    // === CONCURRENCY ===
    val MUTEX_GUARDIAN = UnitStats(
        type = UnitType.MUTEX_GUARDIAN,
        maxHp = 60,
        speed = 60f,
        damage = 5,
        attackRange = 70f,
        attackSpeed = 0.7f,
        productionTime = 4f
    )
    
    val SEMAPHORE_CONTROLLER = UnitStats(
        type = UnitType.SEMAPHORE_CONTROLLER,
        maxHp = 55,
        speed = 55f,
        damage = 4,
        attackRange = 80f,
        attackSpeed = 0.6f,
        productionTime = 4f
    )
    
    val THREAD_POOL = UnitStats(
        type = UnitType.THREAD_POOL,
        maxHp = 70,
        speed = 30f,
        damage = 0,
        attackRange = 0f,
        attackSpeed = 0f,
        productionTime = 6f
    )
    
    val WORKER_THREAD = UnitStats(
        type = UnitType.WORKER_THREAD,
        maxHp = 15,
        speed = 120f,
        damage = 4,
        attackRange = 35f,
        attackSpeed = 1.5f,
        productionTime = 0f  // Spawned by Thread Pool
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
            UnitType.POINTER -> POINTER
            UnitType.BUFFER -> BUFFER
            UnitType.ASSERT -> ASSERT
            UnitType.STATIC_CAST -> STATIC_CAST
            UnitType.DYNAMIC_CAST -> DYNAMIC_CAST
            UnitType.MUTEX_GUARDIAN -> MUTEX_GUARDIAN
            UnitType.SEMAPHORE_CONTROLLER -> SEMAPHORE_CONTROLLER
            UnitType.THREAD_POOL -> THREAD_POOL
            UnitType.WORKER_THREAD -> WORKER_THREAD
        }
    }
    
    // Get card definition for UI display
    fun getCardDefinition(cardType: CardType): com.memoryleak.shared.network.CardDefinition? {
        return when (cardType) {
            // Factory cards
            CardType.BUILD_FACTORY -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Factory", "Build a standard factory", 100, 0, "Factory", 0f
            )
            CardType.BUILD_COMPILER_FACTORY -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Compiler", "Slow but stronger units", 150, 50, "Factory", 0f
            )
            CardType.BUILD_INTERPRETER_FACTORY -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Interpreter", "Fast but weaker units", 80, 30, "Factory", 0f
            )
            CardType.BUILD_INHERITANCE_FACTORY -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Inheritance", "Combine units for upgrades", 200, 100, "Factory", 0f
            )
            
            // Basic units
            CardType.SPAWN_SCOUT -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Scout", "Fast reconnaissance unit", 30, 20, "Basic", SCOUT.productionTime
            )
            CardType.SPAWN_TANK -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Tank", "Heavy armored unit", 80, 40, "Basic", TANK.productionTime
            )
            CardType.SPAWN_RANGED -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Ranged", "Long range attacker", 50, 60, "Basic", RANGED.productionTime
            )
            CardType.SPAWN_HEALER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Healer", "Heals nearby allies", 60, 50, "Basic", HEALER.productionTime
            )
            
            // Process units
            CardType.SPAWN_ALLOCATOR -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Allocator", "Captures nodes, generates memory", 40, 30, "Process", ALLOCATOR.productionTime
            )
            CardType.SPAWN_GARBAGE_COLLECTOR -> com.memoryleak.shared.network.CardDefinition(
                cardType, "GC", "Returns resources on kills", 50, 40, "Process", GARBAGE_COLLECTOR.productionTime
            )
            CardType.SPAWN_BASIC_PROCESS -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Process", "Cheap basic infantry", 35, 25, "Process", BASIC_PROCESS.productionTime
            )
            
            // OOP units
            CardType.SPAWN_INHERITANCE_DRONE -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Inherit", "Absorbs dead ally stats", 60, 45, "OOP", INHERITANCE_DRONE.productionTime
            )
            CardType.SPAWN_POLYMORPH_WARRIOR -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Polymorph", "Adapts damage to target", 85, 60, "OOP", POLYMORPH_WARRIOR.productionTime
            )
            CardType.SPAWN_ENCAPSULATION_SHIELD -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Shield", "Protects nearby allies", 100, 50, "OOP", ENCAPSULATION_SHIELD.productionTime
            )
            CardType.SPAWN_ABSTRACTION_AGENT -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Abstract", "Hides allies from targeting", 45, 35, "OOP", ABSTRACTION_AGENT.productionTime
            )
            
            // Reflection units
            CardType.SPAWN_REFLECTION_SPY -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Spy", "Reveals enemy stats", 30, 25, "Reflection", REFLECTION_SPY.productionTime
            )
            CardType.SPAWN_CODE_INJECTOR -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Injector", "Damages enemy factories", 70, 55, "Reflection", CODE_INJECTOR.productionTime
            )
            CardType.SPAWN_DYNAMIC_DISPATCHER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Dispatch", "Boosts ally attack speed", 65, 50, "Reflection", DYNAMIC_DISPATCHER.productionTime
            )
            
            // Async units
            CardType.SPAWN_COROUTINE_ARCHER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Coroutine", "High damage, ignores armor", 75, 65, "Async", COROUTINE_ARCHER.productionTime
            )
            CardType.SPAWN_PROMISE_KNIGHT -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Promise", "AoE damage on death", 90, 70, "Async", PROMISE_KNIGHT.productionTime
            )
            CardType.SPAWN_DEADLOCK_TRAP -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Deadlock", "Freezes clustered enemies", 40, 40, "Async", DEADLOCK_TRAP.productionTime
            )
            
            // Functional units
            CardType.SPAWN_LAMBDA_SNIPER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Lambda", "One-shot assassin", 100, 80, "Functional", LAMBDA_SNIPER.productionTime
            )
            CardType.SPAWN_RECURSIVE_BOMB -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Recursive", "Splits on death", 55, 50, "Functional", RECURSIVE_BOMB.productionTime
            )
            CardType.SPAWN_HIGHER_ORDER_COMMANDER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "H.O.C.", "Buffs all nearby allies", 80, 65, "Functional", HIGHER_ORDER_COMMANDER.productionTime
            )
            
            // Network units
            CardType.SPAWN_API_GATEWAY -> com.memoryleak.shared.network.CardDefinition(
                cardType, "API", "Extends ally range", 70, 55, "Network", API_GATEWAY.productionTime
            )
            CardType.SPAWN_WEBSOCKET_SCOUT -> com.memoryleak.shared.network.CardDefinition(
                cardType, "WebSocket", "Reveals all enemies", 35, 30, "Network", WEBSOCKET_SCOUT.productionTime
            )
            CardType.SPAWN_RESTFUL_HEALER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "RESTful", "Full support healer", 70, 60, "Network", RESTFUL_HEALER.productionTime
            )
            
            // Storage units
            CardType.SPAWN_CACHE_RUNNER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Cache", "Fastest unit", 25, 20, "Storage", CACHE_RUNNER.productionTime
            )
            CardType.SPAWN_INDEXER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Indexer", "Marks for bonus damage", 50, 45, "Storage", INDEXER.productionTime
            )
            CardType.SPAWN_TRANSACTION_GUARD -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Transaction", "Rollback on death", 75, 60, "Storage", TRANSACTION_GUARD.productionTime
            )
            
            // Memory units
            CardType.SPAWN_POINTER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Pointer", "Teleport striker", 60, 50, "Memory", POINTER.productionTime
            )
            CardType.SPAWN_BUFFER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Buffer", "Absorbs ally damage", 70, 40, "Memory", BUFFER.productionTime
            )
            
            // Safety units
            CardType.SPAWN_ASSERT -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Assert", "Execute low HP enemies", 45, 35, "Safety", ASSERT.productionTime
            )
            CardType.SPAWN_STATIC_CAST -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Static Cast", "Convert buffs to debuffs", 55, 45, "Safety", STATIC_CAST.productionTime
            )
            CardType.SPAWN_DYNAMIC_CAST -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Dynamic Cast", "50% 2x or 0 damage", 50, 55, "Safety", DYNAMIC_CAST.productionTime
            )
            
            // Concurrency units
            CardType.SPAWN_MUTEX_GUARDIAN -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Mutex", "Locks single enemy", 65, 55, "Concurrency", MUTEX_GUARDIAN.productionTime
            )
            CardType.SPAWN_SEMAPHORE_CONTROLLER -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Semaphore", "Limits attackers", 70, 60, "Concurrency", SEMAPHORE_CONTROLLER.productionTime
            )
            CardType.SPAWN_THREAD_POOL -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Thread Pool", "Spawns workers", 90, 70, "Concurrency", THREAD_POOL.productionTime
            )
            
            CardType.UPGRADE_INHERITANCE -> com.memoryleak.shared.network.CardDefinition(
                cardType, "Upgrade", "Combine units at Inheritance Factory", 50, 50, "Special", 0f
            )
        }
    }
}

