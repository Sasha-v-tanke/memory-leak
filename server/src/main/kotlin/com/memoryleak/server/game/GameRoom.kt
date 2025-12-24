package com.memoryleak.server.game

import com.memoryleak.server.database.DatabaseConfig
import com.memoryleak.server.database.GameRepository
import com.memoryleak.server.playerSessions
import com.memoryleak.shared.model.*
import com.memoryleak.shared.network.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

class GameRoom {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val players = ConcurrentHashMap<String, PlayerState>()
    private val entities = ConcurrentHashMap<String, GameEntity>()
    
    // Factory production queues
    private val factoryQueues = ConcurrentHashMap<String, MutableList<ProductionQueueItem>>()
    
    private var gameScope = CoroutineScope(Dispatchers.Default)
    private var isRunning = false
    private var lastTickTime = System.currentTimeMillis()
    
    // Map configuration
    val mapWidth = 1600f
    val mapHeight = 800f
    val sessionId = UUID.randomUUID().toString()
    
    // Database session tracking
    private var dbSessionId: UUID? = null
    private var gameStartTime: Long = 0L
    
    // Player count tracking  
    private var playerCount = 0
    
    // Player statistics tracking
    private val playerStats = ConcurrentHashMap<String, PlayerGameStats>()
    
    // Statistics data class for tracking in-game metrics
    private data class PlayerGameStats(
        var unitsCreated: Int = 0,
        var unitsLost: Int = 0,
        var unitsKilled: Int = 0,
        var factoriesBuilt: Int = 0,
        var resourcesCaptured: Int = 0,
        var cardsPlayed: Int = 0
    )

    fun start() {
        if (isRunning) return
        isRunning = true
        gameStartTime = System.currentTimeMillis()
        
        // Initialize Symmetric Map - resources mirrored across center
        val centerX = mapWidth / 2
        
        // Memory nodes - symmetric
        spawnResource("mem_1", ResourceType.MEMORY, centerX - 400, 200f)
        spawnResource("mem_2", ResourceType.MEMORY, centerX + 400, mapHeight - 200)
        
        // CPU nodes - symmetric  
        spawnResource("cpu_1", ResourceType.CPU, centerX - 400, mapHeight - 200)
        spawnResource("cpu_2", ResourceType.CPU, centerX + 400, 200f)
        
        // Additional central resources
        spawnResource("mem_center", ResourceType.MEMORY, centerX, mapHeight / 2 - 100)
        spawnResource("cpu_center", ResourceType.CPU, centerX, mapHeight / 2 + 100)

        gameScope.launch {
            while (isRunning) {
                val currentTime = System.currentTimeMillis()
                val delta = (currentTime - lastTickTime) / 1000f
                lastTickTime = currentTime
                
                update(delta)
                broadcastState()
                
                delay(1000L / 60L) // 60 Hz tick
            }
        }
    }

    private fun spawnResource(id: String, type: ResourceType, x: Float, y: Float) {
        entities[id] = GameEntity(
            id = id,
            type = EntityType.RESOURCE_NODE,
            x = x, 
            y = y,
            ownerId = "0",
            hp = 100,
            maxHp = 100,
            resourceType = type,
            resourceAmount = 1000
        )
    }

    suspend fun join(sessionId: String, socket: WebSocketSession, selectedDeck: List<String> = emptyList()): PlayerState {
        sessions[sessionId] = socket
        playerCount++
        
        val player = PlayerState(
            id = sessionId, 
            name = "Player-$sessionId",
            memory = 200,
            cpu = 100,
            selectedDeckTypes = selectedDeck.toMutableList()
        )
        
        // Initialize deck based on selection or default
        player.deck = if (selectedDeck.isNotEmpty()) {
            DeckBuilder.createDeckFromSelection(selectedDeck)
        } else {
            DeckBuilder.createDefaultDeck()
        }
        repeat(4) { DeckBuilder.drawCard(player) }
        
        players[sessionId] = player
        
        // Initialize stats tracking for this player
        playerStats[sessionId] = PlayerGameStats()
        
        // Database: Create or join session
        if (DatabaseConfig.isConnected()) {
            GameRepository.getOrCreatePlayerStats(sessionId, player.name)
            
            if (dbSessionId == null) {
                // First player - create session
                dbSessionId = GameRepository.createSession(sessionId)
                println("[Database] Created game session: $dbSessionId")
            } else {
                // Second player - join session
                GameRepository.joinSession(dbSessionId!!, sessionId)
                println("[Database] Player $sessionId joined session: $dbSessionId")
            }
        }
        
        // Symmetric spawning - Player 1 on left, Player 2 on right
        val isPlayer1 = playerCount == 1
        val baseX = if (isPlayer1) 150f else mapWidth - 150f
        val baseY = mapHeight / 2
        
        // Spawn Instance (Base) for player
        val instanceId = UUID.randomUUID().toString()
        entities[instanceId] = GameEntity(
            id = instanceId,
            type = EntityType.INSTANCE,
            x = baseX,
            y = baseY,
            ownerId = sessionId,
            hp = 1000,
            maxHp = 1000,
            speed = 15f
        )
        
        // Spawn initial factory near base
        val factoryId = UUID.randomUUID().toString()
        val factoryX = if (isPlayer1) baseX + 60f else baseX - 60f
        entities[factoryId] = GameEntity(
            id = factoryId,
            type = EntityType.FACTORY,
            x = factoryX,
            y = baseY,
            ownerId = sessionId,
            hp = 200,
            maxHp = 200,
            speed = 35f,
            factoryType = FactoryType.STANDARD
        )
        factoryQueues[factoryId] = mutableListOf()
        playerStats[sessionId]?.factoriesBuilt = 1
        
        // Send join acknowledgment
        val joinPacket = JoinAckPacket(sessionId, mapWidth, mapHeight)
        socket.send(Frame.Text(Json.encodeToString<Packet>(joinPacket)))
        
        return player
    }
    
    private fun spawnUnitByCard(ownerId: String, unitType: UnitType, x: Float, y: Float, stats: UnitStats): GameEntity {
        val unitId = UUID.randomUUID().toString()
        val entity = GameEntity(
            id = unitId,
            type = EntityType.UNIT,
            x = x,
            y = y,
            ownerId = ownerId,
            hp = stats.maxHp,
            maxHp = stats.maxHp,
            speed = stats.speed,
            unitType = unitType
        )
        entities[unitId] = entity
        
        // Track statistics
        playerStats[ownerId]?.unitsCreated = (playerStats[ownerId]?.unitsCreated ?: 0) + 1
        
        return entity
    }

    fun handleCommand(sessionId: String, cmd: CommandPacket) {
        // Validation logic
        // println("CMD from $sessionId: ${cmd.commandType}")
        
        if (cmd.commandType == CommandType.MOVE && cmd.entityId != null) {
            val entity = entities[cmd.entityId]
            if (entity != null && entity.ownerId == sessionId) {
                 // Set target position for smooth movement
                 entity.targetX = cmd.targetX
                 entity.targetY = cmd.targetY
            }
        } else if (cmd.commandType == CommandType.BUILD) {
             // Logic: Check if selected entity is Instance (Build Factory) or Factory (Build Unit)
             if (cmd.entityId != null) {
                 val source = entities[cmd.entityId]
                 val player = players[sessionId]
                 if (source != null && source.ownerId == sessionId && player != null) {
                     if (source.type == EntityType.INSTANCE) {
                         // Build Factory - costs Memory=100
                         if (player.memory >= 100) {
                             player.memory -= 100
                             val factoryId = UUID.randomUUID().toString()
                             entities[factoryId] = GameEntity(
                                 id = factoryId,
                                 type = EntityType.FACTORY,
                                 x = source.x + (Math.random() * 60 - 30).toFloat(),
                                 y = source.y + (Math.random() * 60 - 30).toFloat(),
                                 ownerId = sessionId,
                                 hp = 200,
                                 maxHp = 200,
                                 speed = 35f  // Slow
                             )
                         }
                     } else if (source.type == EntityType.FACTORY) {
                         // Build Unit - costs CPU=50
                         if (player.cpu >= 50) {
                             player.cpu -= 50
                             val unitId = UUID.randomUUID().toString()
                             entities[unitId] = GameEntity(
                                 id = unitId,
                                 type = EntityType.UNIT,
                                 x = source.x + 20f, // Offset
                                 y = source.y + 20f,
                                 ownerId = sessionId,
                                 hp = 50,
                                 maxHp = 50,
                                 speed = 120f  // Fast
                             )
                         }
                     }
                 }
             }
        } else if (cmd.commandType == CommandType.PLAY_CARD) {
            // New card system!
            val cardId = cmd.cardId ?: return  // Fix smart cast issue
            val player = players[sessionId] ?: return
            val card = DeckBuilder.playCard(player, cardId) ?: return
            
            // NEW: Check Global Cooldown
            if (player.globalCooldown > 0) {
                // Ignore command if on cooldown
                return
            }

            // NEW: Check if spawning near base
            val myBase = entities.values.find { it.ownerId == sessionId && it.type == EntityType.INSTANCE }
            if (myBase != null) {
                val dx = cmd.targetX - myBase.x
                val dy = cmd.targetY - myBase.y
                val distSq = dx*dx + dy*dy
                val maxDist = 200f // REDUCED Spawn radius (was 400)
                
                if (distSq > maxDist * maxDist) {
                    println("Spawn too far from base!")
                    // Refund card
                    player.hand.add(card)
                    player.discardPile.remove(card)
                    return
                }
            }
            
            // Check resources
            if (player.memory < card.memoryCost || player.cpu < card.cpuCost) {
                // Can't afford - add back to hand
                player.hand.add(card)
                player.discardPile.remove(card)
                return
            }
            
            // Deduct cost
            player.memory -= card.memoryCost
            player.cpu -= card.cpuCost
            
            // Set Global Cooldown (1.5 seconds)
            player.globalCooldown = 1.5f
            
            // Track card played
            playerStats[sessionId]?.cardsPlayed = (playerStats[sessionId]?.cardsPlayed ?: 0) + 1
            
            // Spawn unit or build
            when (card.type) {
                // Legacy units
                CardType.SPAWN_SCOUT -> spawnUnitByCard(sessionId, UnitType.SCOUT, cmd.targetX, cmd.targetY, UnitStatsData.SCOUT)
                CardType.SPAWN_TANK -> spawnUnitByCard(sessionId, UnitType.TANK, cmd.targetX, cmd.targetY, UnitStatsData.TANK)
                CardType.SPAWN_RANGED -> spawnUnitByCard(sessionId, UnitType.RANGED, cmd.targetX, cmd.targetY, UnitStatsData.RANGED)
                CardType.SPAWN_HEALER -> spawnUnitByCard(sessionId, UnitType.HEALER, cmd.targetX, cmd.targetY, UnitStatsData.HEALER)
                
                // Basic Processes
                CardType.SPAWN_ALLOCATOR -> spawnUnitByCard(sessionId, UnitType.ALLOCATOR, cmd.targetX, cmd.targetY, UnitStatsData.ALLOCATOR)
                CardType.SPAWN_GARBAGE_COLLECTOR -> spawnUnitByCard(sessionId, UnitType.GARBAGE_COLLECTOR, cmd.targetX, cmd.targetY, UnitStatsData.GARBAGE_COLLECTOR)
                CardType.SPAWN_BASIC_PROCESS -> spawnUnitByCard(sessionId, UnitType.BASIC_PROCESS, cmd.targetX, cmd.targetY, UnitStatsData.BASIC_PROCESS)
                
                // OOP Units
                CardType.SPAWN_INHERITANCE_DRONE -> spawnUnitByCard(sessionId, UnitType.INHERITANCE_DRONE, cmd.targetX, cmd.targetY, UnitStatsData.INHERITANCE_DRONE)
                CardType.SPAWN_POLYMORPH_WARRIOR -> spawnUnitByCard(sessionId, UnitType.POLYMORPH_WARRIOR, cmd.targetX, cmd.targetY, UnitStatsData.POLYMORPH_WARRIOR)
                CardType.SPAWN_ENCAPSULATION_SHIELD -> spawnUnitByCard(sessionId, UnitType.ENCAPSULATION_SHIELD, cmd.targetX, cmd.targetY, UnitStatsData.ENCAPSULATION_SHIELD)
                CardType.SPAWN_ABSTRACTION_AGENT -> spawnUnitByCard(sessionId, UnitType.ABSTRACTION_AGENT, cmd.targetX, cmd.targetY, UnitStatsData.ABSTRACTION_AGENT)
                
                // Reflection & Metaprogramming
                CardType.SPAWN_REFLECTION_SPY -> spawnUnitByCard(sessionId, UnitType.REFLECTION_SPY, cmd.targetX, cmd.targetY, UnitStatsData.REFLECTION_SPY)
                CardType.SPAWN_CODE_INJECTOR -> spawnUnitByCard(sessionId, UnitType.CODE_INJECTOR, cmd.targetX, cmd.targetY, UnitStatsData.CODE_INJECTOR)
                CardType.SPAWN_DYNAMIC_DISPATCHER -> spawnUnitByCard(sessionId, UnitType.DYNAMIC_DISPATCHER, cmd.targetX, cmd.targetY, UnitStatsData.DYNAMIC_DISPATCHER)
                
                // Async & Parallelism
                CardType.SPAWN_COROUTINE_ARCHER -> spawnUnitByCard(sessionId, UnitType.COROUTINE_ARCHER, cmd.targetX, cmd.targetY, UnitStatsData.COROUTINE_ARCHER)
                CardType.SPAWN_PROMISE_KNIGHT -> spawnUnitByCard(sessionId, UnitType.PROMISE_KNIGHT, cmd.targetX, cmd.targetY, UnitStatsData.PROMISE_KNIGHT)
                CardType.SPAWN_DEADLOCK_TRAP -> spawnUnitByCard(sessionId, UnitType.DEADLOCK_TRAP, cmd.targetX, cmd.targetY, UnitStatsData.DEADLOCK_TRAP)
                
                // Functional Programming
                CardType.SPAWN_LAMBDA_SNIPER -> spawnUnitByCard(sessionId, UnitType.LAMBDA_SNIPER, cmd.targetX, cmd.targetY, UnitStatsData.LAMBDA_SNIPER)
                CardType.SPAWN_RECURSIVE_BOMB -> spawnUnitByCard(sessionId, UnitType.RECURSIVE_BOMB, cmd.targetX, cmd.targetY, UnitStatsData.RECURSIVE_BOMB)
                CardType.SPAWN_HIGHER_ORDER_COMMANDER -> spawnUnitByCard(sessionId, UnitType.HIGHER_ORDER_COMMANDER, cmd.targetX, cmd.targetY, UnitStatsData.HIGHER_ORDER_COMMANDER)
                
                // Network & Communication
                CardType.SPAWN_API_GATEWAY -> spawnUnitByCard(sessionId, UnitType.API_GATEWAY, cmd.targetX, cmd.targetY, UnitStatsData.API_GATEWAY)
                CardType.SPAWN_WEBSOCKET_SCOUT -> spawnUnitByCard(sessionId, UnitType.WEBSOCKET_SCOUT, cmd.targetX, cmd.targetY, UnitStatsData.WEBSOCKET_SCOUT)
                CardType.SPAWN_RESTFUL_HEALER -> spawnUnitByCard(sessionId, UnitType.RESTFUL_HEALER, cmd.targetX, cmd.targetY, UnitStatsData.RESTFUL_HEALER)
                
                // Storage Units
                CardType.SPAWN_CACHE_RUNNER -> spawnUnitByCard(sessionId, UnitType.CACHE_RUNNER, cmd.targetX, cmd.targetY, UnitStatsData.CACHE_RUNNER)
                CardType.SPAWN_INDEXER -> spawnUnitByCard(sessionId, UnitType.INDEXER, cmd.targetX, cmd.targetY, UnitStatsData.INDEXER)
                CardType.SPAWN_TRANSACTION_GUARD -> spawnUnitByCard(sessionId, UnitType.TRANSACTION_GUARD, cmd.targetX, cmd.targetY, UnitStatsData.TRANSACTION_GUARD)
                
                // Memory Management
                CardType.SPAWN_POINTER -> spawnUnitByCard(sessionId, UnitType.POINTER, cmd.targetX, cmd.targetY, UnitStatsData.POINTER)
                CardType.SPAWN_BUFFER -> spawnUnitByCard(sessionId, UnitType.BUFFER, cmd.targetX, cmd.targetY, UnitStatsData.BUFFER)
                
                // Safety & Type
                CardType.SPAWN_ASSERT -> spawnUnitByCard(sessionId, UnitType.ASSERT, cmd.targetX, cmd.targetY, UnitStatsData.ASSERT)
                CardType.SPAWN_STATIC_CAST -> spawnUnitByCard(sessionId, UnitType.STATIC_CAST, cmd.targetX, cmd.targetY, UnitStatsData.STATIC_CAST)
                CardType.SPAWN_DYNAMIC_CAST -> spawnUnitByCard(sessionId, UnitType.DYNAMIC_CAST, cmd.targetX, cmd.targetY, UnitStatsData.DYNAMIC_CAST)
                
                // Concurrency
                CardType.SPAWN_MUTEX_GUARDIAN -> spawnUnitByCard(sessionId, UnitType.MUTEX_GUARDIAN, cmd.targetX, cmd.targetY, UnitStatsData.MUTEX_GUARDIAN)
                CardType.SPAWN_SEMAPHORE_CONTROLLER -> spawnUnitByCard(sessionId, UnitType.SEMAPHORE_CONTROLLER, cmd.targetX, cmd.targetY, UnitStatsData.SEMAPHORE_CONTROLLER)
                CardType.SPAWN_THREAD_POOL -> spawnUnitByCard(sessionId, UnitType.THREAD_POOL, cmd.targetX, cmd.targetY, UnitStatsData.THREAD_POOL)
                
                // Factories
                CardType.BUILD_FACTORY -> buildFactory(sessionId, cmd.targetX, cmd.targetY, FactoryType.STANDARD)
                CardType.BUILD_COMPILER_FACTORY -> buildFactory(sessionId, cmd.targetX, cmd.targetY, FactoryType.COMPILER)
                CardType.BUILD_INTERPRETER_FACTORY -> buildFactory(sessionId, cmd.targetX, cmd.targetY, FactoryType.INTERPRETER)
                CardType.BUILD_INHERITANCE_FACTORY -> buildFactory(sessionId, cmd.targetX, cmd.targetY, FactoryType.INHERITANCE)
                
                // Special - Upgrade card for inheritance factory
                CardType.UPGRADE_INHERITANCE -> {
                    // Find nearest inheritance factory owned by player
                    val nearestFactory = entities.values
                        .filter { it.type == EntityType.FACTORY && it.ownerId == sessionId && it.factoryType == FactoryType.INHERITANCE }
                        .minByOrNull { 
                            val dx = it.x - cmd.targetX
                            val dy = it.y - cmd.targetY
                            dx * dx + dy * dy
                        }
                    
                    if (nearestFactory != null) {
                        // Find nearby units to combine (within 100px of the factory)
                        val nearbyUnits = entities.values
                            .filter { unit -> 
                                unit.type == EntityType.UNIT && 
                                unit.ownerId == sessionId &&
                                kotlin.math.sqrt((unit.x - nearestFactory.x).let { dx -> dx * dx } + 
                                               (unit.y - nearestFactory.y).let { dy -> dy * dy }.toDouble()) < 100
                            }
                            .take(2)
                        
                        if (nearbyUnits.size >= 2) {
                            // Remove the sacrificed units
                            nearbyUnits.forEach { entities.remove(it.id) }
                            
                            // Create upgraded unit with combined stats
                            val combinedHp = nearbyUnits.sumOf { it.maxHp }
                            val avgSpeed = nearbyUnits.map { it.speed }.average().toFloat()
                            
                            spawnUnitByCard(
                                sessionId, 
                                UnitType.INHERITANCE_DRONE, // Upgraded unit type
                                nearestFactory.x + 20f, 
                                nearestFactory.y,
                                UnitStatsData.INHERITANCE_DRONE.copy(
                                    maxHp = (combinedHp * 1.2).toInt(),
                                    speed = avgSpeed * 1.1f
                                )
                            )
                        }
                    }
                }
            }
            
            // Draw new card
            DeckBuilder.drawCard(player)
        }
    }
    
    private fun buildFactory(ownerId: String, x: Float, y: Float, factoryType: FactoryType) {
        val factoryId = UUID.randomUUID().toString()
        entities[factoryId] = GameEntity(
            id = factoryId,
            type = EntityType.FACTORY,
            x = x,
            y = y,
            ownerId = ownerId,
            hp = 200,
            maxHp = 200,
            speed = 35f,
            factoryType = factoryType
        )
        factoryQueues[factoryId] = mutableListOf()
        playerStats[ownerId]?.factoriesBuilt = (playerStats[ownerId]?.factoriesBuilt ?: 0) + 1
    }
    
    private var lastTick = 0L
    private val deadUnitsThisFrame = mutableListOf<GameEntity>()  // For InheritanceDrone
    
    private suspend fun update(delta: Float) {
        deadUnitsThisFrame.clear()
        
        // 1. Cooldowns - update every frame
        players.values.forEach { player ->
            if (player.globalCooldown > 0) {
                player.globalCooldown -= delta
                if (player.globalCooldown < 0) player.globalCooldown = 0f
            }
        }
        
        // 2. Passive Resource Generation + Node Income (every second)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTick > 1000) {
            lastTick = currentTime
            
            // Passive Income
            players.values.forEach { player ->
                 player.memory += 5
                 player.cpu += 5
            }
            
            // Resource from captured nodes
            entities.values.filter { it.type == EntityType.RESOURCE_NODE && it.ownerId != "0" }.forEach { node ->
                val owner = players[node.ownerId]
                if (owner != null) {
                    when(node.resourceType) {
                        ResourceType.MEMORY -> owner.memory += 1
                        ResourceType.CPU -> owner.cpu += 1
                        null -> {}
                    }
                }
            }
            
            // ALLOCATOR Passive: Generate Memory for owner periodically
            entities.values.filter { it.type == EntityType.UNIT && it.unitType == UnitType.ALLOCATOR }.forEach { allocator ->
                val owner = players[allocator.ownerId]
                if (owner != null) {
                    owner.memory += 2  // Allocators generate memory
                }
            }
        }
        
        // 3. AUTONOMOUS AI SYSTEM - All Units
        entities.values.filter { it.type == EntityType.UNIT }.forEach { unit ->
            updateUnitAI(unit, delta, currentTime)
        }
        
        // 4. Movement System - All movable entities (manual control for buildings)
        entities.values.filter { 
            it.type == EntityType.FACTORY || it.type == EntityType.INSTANCE 
        }.forEach { entity ->
            val tx = entity.targetX
            val ty = entity.targetY
            if (tx != null && ty != null) {
                val dx = tx - entity.x
                val dy = ty - entity.y
                val dist = Math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
                
                if (dist > 2f) {  // Still moving
                    val moveAmount = entity.speed * delta
                    if (moveAmount >= dist) {
                        entity.x = tx
                        entity.y = ty
                        entity.targetX = null
                        entity.targetY = null
                    } else {
                        entity.x += (dx / dist) * moveAmount
                        entity.y += (dy / dist) * moveAmount
                    }
                } else {
                    entity.x = tx
                    entity.y = ty
                    entity.targetX = null
                    entity.targetY = null
                }
            }
        }
        
        // 5. Resource Capture (Node ownership change)
        entities.values.filter { it.type == EntityType.RESOURCE_NODE }.forEach { node ->
            val nearbyUnit = entities.values.find { 
                it.type == EntityType.UNIT && it.ownerId != "0" && distance(it, node) < 30f 
            }
            if (nearbyUnit != null && node.ownerId != nearbyUnit.ownerId) {
                node.ownerId = nearbyUnit.ownerId // CAPTURED!
            }
        }
    }
    
    // === CORE AI CONTROLLER ===
    private suspend fun updateUnitAI(unit: GameEntity, delta: Float, currentTime: Long) {
        val stats = unit.unitType?.let { UnitStatsData.getStats(it) } ?: return
        
        // 1. Find Target (if no current target or target is dead)
        if (unit.targetEnemyId == null || entities[unit.targetEnemyId!!] == null) {
            unit.targetEnemyId = findNearestEnemy(unit)
            unit.aiState = if (unit.targetEnemyId != null) AIState.MOVING_TO_TARGET else AIState.IDLE
        }
        
        val target = unit.targetEnemyId?.let { entities[it] }
        
        if (target == null) {
            // No target - idle or find resource nodes for ALLOCATOR/CACHE_RUNNER
            if (unit.unitType == UnitType.ALLOCATOR || unit.unitType == UnitType.CACHE_RUNNER) {
                val nearestNode = findNearestResourceNode(unit)
                if (nearestNode != null && distance(unit, nearestNode) > 25f) {
                    unit.targetX = nearestNode.x
                    unit.targetY = nearestNode.y
                }
            }
            unit.aiState = AIState.IDLE
            unit.attackingTargetId = null
            return
        }
        
        val dist = distance(unit, target)
        
        // 2. Check if in attack range
        if (dist <= stats.attackRange) {
            // In range - ATTACK!
            unit.aiState = AIState.ATTACKING
            unit.targetX = null
            unit.targetY = null
            
            // Attack cooldown
            val attackCooldown = (1000f / stats.attackSpeed).toLong()
            if (currentTime - unit.lastAttackTime >= attackCooldown) {
                performAttack(unit, target, stats, currentTime)
                unit.lastAttackTime = currentTime
            }
            
            unit.attackingTargetId = target.id
        } else {
            // Out of range - MOVE TOWARDS
            unit.aiState = AIState.MOVING_TO_TARGET
            unit.targetX = target.x
            unit.targetY = target.y
            unit.attackingTargetId = null
            
            // Actually move (inline movement for units)
            val dx = target.x - unit.x
            val dy = target.y - unit.y
            val moveAmount = unit.speed * delta
            val normDist = Math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
            if (normDist > 0) {
                unit.x += (dx / normDist) * moveAmount
                unit.y += (dy / normDist) * moveAmount
            }
        }
        
        // 3. Check and trigger special abilities
        triggerUnitAbility(unit, target, stats, currentTime)
    }
    
    private fun findNearestEnemy(unit: GameEntity): String? {
        // Priority: Units > Factories > Instance
        val enemies = entities.values.filter { 
            it.ownerId != unit.ownerId && it.ownerId != "0" && it.type != EntityType.RESOURCE_NODE 
        }
        
        if (enemies.isEmpty()) return null
        
        // Sort by priority then distance
        val prioritized = enemies.sortedWith(compareBy(
            { when(it.type) {
                EntityType.INSTANCE -> 3  // Lowest priority
                EntityType.FACTORY -> 2
                EntityType.UNIT -> 1  // Highest priority
                else -> 4
            }},
            { distance(unit, it) }
        ))
        
        return prioritized.firstOrNull()?.id
    }
    
    private fun findNearestResourceNode(unit: GameEntity): GameEntity? {
        return entities.values
            .filter { it.type == EntityType.RESOURCE_NODE && it.ownerId != unit.ownerId }
            .minByOrNull { distance(unit, it) }
    }
    
    private suspend fun performAttack(attacker: GameEntity, target: GameEntity, stats: UnitStats, currentTime: Long) {
        var damage = stats.damage
        
        // === POLYMORPH WARRIOR: Change damage based on enemy type ===
        if (attacker.unitType == UnitType.POLYMORPH_WARRIOR) {
            damage = when(target.type) {
                EntityType.UNIT -> (stats.damage * 1.3f).toInt()  // +30% vs units
                EntityType.FACTORY -> (stats.damage * 1.5f).toInt()  // +50% vs factories
                EntityType.INSTANCE -> (stats.damage * 2.0f).toInt()  // +100% vs instance
                else -> stats.damage
            }
        }
        
        // === INDEXER: Check if target is marked (bonus damage from allies) ===
        if (target.abilityData.contains("indexed_by")) {
            damage = (damage * 1.25f).toInt()  // +25% damage to indexed targets
        }
        
        // === COROUTINE ARCHER: Ignores 30% of HP (armor penetration) ===
        if (attacker.unitType == UnitType.COROUTINE_ARCHER) {
            damage = (damage * 1.3f).toInt()  // Async arrows pierce armor
        }
        
        target.hp -= damage
        
        if (target.hp <= 0) {
            // === PROMISE KNIGHT: Delayed damage on death ===
            if (target.unitType == UnitType.PROMISE_KNIGHT) {
                // Deal AoE damage to nearby enemies
                entities.values.filter { 
                    it.ownerId != target.ownerId && it.type == EntityType.UNIT && distance(it, target) < 80f 
                }.forEach {
                    it.hp -= 15  // Delayed explosion damage
                }
            }
            
            // === RECURSIVE BOMB: Split into smaller bombs ===
            if (target.unitType == UnitType.RECURSIVE_BOMB) {
                val bombLevel = target.abilityData.toIntOrNull() ?: 0
                if (bombLevel < 2) {  // Max 2 recursions
                    repeat(2) {
                        spawnUnitByCard(
                            target.ownerId, 
                            UnitType.RECURSIVE_BOMB, 
                            target.x + (Math.random() * 40 - 20).toFloat(),
                            target.y + (Math.random() * 40 - 20).toFloat(),
                            UnitStatsData.RECURSIVE_BOMB.copy(
                                maxHp = UnitStatsData.RECURSIVE_BOMB.maxHp / 2,
                                damage = UnitStatsData.RECURSIVE_BOMB.damage / 2
                            )
                        ).also { newBomb ->
                            newBomb.abilityData = (bombLevel + 1).toString()
                        }
                    }
                }
            }
            
            // === TRANSACTION GUARD: Revert node capture on death ===
            if (target.unitType == UnitType.TRANSACTION_GUARD) {
                // Find nearby captured nodes and revert ownership
                entities.values.filter { 
                    it.type == EntityType.RESOURCE_NODE && 
                    it.ownerId == target.ownerId && 
                    distance(it, target) < 100f 
                }.forEach {
                    it.ownerId = "0"  // Rollback to neutral
                }
            }
            
            deadUnitsThisFrame.add(target)
            entities.remove(target.id)
            
            // Track kills and losses in statistics
            if (target.type == EntityType.UNIT) {
                // Attacker gets a kill
                playerStats[attacker.ownerId]?.unitsKilled = (playerStats[attacker.ownerId]?.unitsKilled ?: 0) + 1
                // Target owner loses a unit
                playerStats[target.ownerId]?.unitsLost = (playerStats[target.ownerId]?.unitsLost ?: 0) + 1
            }
            
            // === GARBAGE COLLECTOR: Return resources on cleanup ===
            if (attacker.unitType == UnitType.GARBAGE_COLLECTOR) {
                val owner = players[attacker.ownerId]
                if (owner != null) {
                    owner.memory += 3
                    owner.cpu += 2
                }
            }
            
            // CHECK WIN CONDITION
            if (target.type == EntityType.INSTANCE) {
                val remainingInstances = entities.values.filter { it.type == EntityType.INSTANCE }
                if (remainingInstances.size == 1) {
                    val winnerId = remainingInstances.first().ownerId
                    broadcastGameOver(winnerId)
                }
            }
        }
    }
    
    // === SPECIAL ABILITIES SYSTEM ===
    private fun triggerUnitAbility(unit: GameEntity, target: GameEntity?, stats: UnitStats, currentTime: Long) {
        val abilityCooldown = 3000L  // 3 seconds default
        
        if (currentTime - unit.lastAbilityTime < abilityCooldown) return
        
        when(unit.unitType) {
            // === OOP UNITS ===
            UnitType.INHERITANCE_DRONE -> {
                // Absorb stats from nearby dead allies
                deadUnitsThisFrame.filter { 
                    it.ownerId == unit.ownerId && distance(unit, it) < 70f 
                }.forEach { deadAlly ->
                    val inheritedHp = (deadAlly.maxHp * 0.2f).toInt()
                    unit.hp = (unit.hp + inheritedHp).coerceAtMost(unit.maxHp + inheritedHp)
                    unit.speed += deadAlly.speed * 0.1f
                    unit.lastAbilityTime = currentTime
                }
            }
            
            UnitType.ENCAPSULATION_SHIELD -> {
                // Create shield for nearby allies (reduce incoming damage)
                entities.values.filter { 
                    it.ownerId == unit.ownerId && 
                    it.type == EntityType.UNIT && 
                    it.id != unit.id && 
                    distance(unit, it) < 80f 
                }.forEach {
                    it.abilityData = "shielded_until_${currentTime + 2000}"  // 2s shield
                }
                unit.lastAbilityTime = currentTime
            }
            
            UnitType.ABSTRACTION_AGENT -> {
                // Hide allies (enemies skip targeting them)
                entities.values.filter { 
                    it.ownerId == unit.ownerId && 
                    it.type == EntityType.UNIT && 
                    distance(unit, it) < 90f 
                }.forEach {
                    it.abilityData = "hidden_until_${currentTime + 3000}"
                }
                unit.lastAbilityTime = currentTime
            }
            
            // === REFLECTION & META ===
            UnitType.REFLECTION_SPY -> {
                // Scan enemy and reveal stats
                target?.let {
                    it.abilityData = "scanned_by_${unit.id}"
                    unit.lastAbilityTime = currentTime
                }
            }
            
            UnitType.CODE_INJECTOR -> {
                // Inject bug into enemy factory (slow production)
                val nearbyFactory = entities.values.find { 
                    it.ownerId != unit.ownerId && 
                    it.type == EntityType.FACTORY && 
                    distance(unit, it) < 100f 
                }
                nearbyFactory?.let {
                    it.hp -= 20  // Damage factory over time
                    it.abilityData = "infected_until_${currentTime + 5000}"
                    unit.lastAbilityTime = currentTime
                }
            }
            
            UnitType.DYNAMIC_DISPATCHER -> {
                // Boost nearby ally attack speed
                entities.values.filter { 
                    it.ownerId == unit.ownerId && 
                    it.type == EntityType.UNIT && 
                    it.id != unit.id && 
                    distance(unit, it) < 100f 
                }.forEach {
                    it.abilityData = "boosted_until_${currentTime + 2000}"
                    // Attack speed boost handled in performAttack
                }
                unit.lastAbilityTime = currentTime
            }
            
            // === ASYNC UNITS ===
            UnitType.DEADLOCK_TRAP -> {
                // Immobilize clustered enemies
                val nearbyEnemies = entities.values.filter { 
                    it.ownerId != unit.ownerId && 
                    it.type == EntityType.UNIT && 
                    distance(unit, it) < 70f 
                }
                if (nearbyEnemies.size >= 2) {
                    nearbyEnemies.forEach {
                        it.speed = 0f  // Deadlocked!
                        it.abilityData = "deadlocked_until_${currentTime + 3000}"
                    }
                    unit.lastAbilityTime = currentTime
                }
            }
            
            // === FUNCTIONAL UNITS ===
            UnitType.LAMBDA_SNIPER -> {
                // Pure function: one-shot high damage
                target?.let {
                    it.hp -= 50  // Instant high damage
                    unit.lastAbilityTime = currentTime + 5000  // Long cooldown
                }
            }
            
            UnitType.HIGHER_ORDER_COMMANDER -> {
                // Buff all nearby allies
                entities.values.filter { 
                    it.ownerId == unit.ownerId && 
                    it.type == EntityType.UNIT && 
                    distance(unit, it) < 120f 
                }.forEach {
                    it.abilityData = "commanded_until_${currentTime + 3000}"
                    // Damage boost: +20%
                }
                unit.lastAbilityTime = currentTime
            }
            
            // === NETWORK UNITS ===
            UnitType.API_GATEWAY -> {
                // Extend ally attack range
                entities.values.filter { 
                    it.ownerId == unit.ownerId && 
                    it.type == EntityType.UNIT && 
                    distance(unit, it)< 100f 
                }.forEach {
                    it.abilityData = "range_boosted_until_${currentTime + 4000}"
                }
                unit.lastAbilityTime = currentTime
            }
            
            UnitType.WEBSOCKET_SCOUT -> {
                // Reveal all enemy positions (mark them)
                entities.values.filter { 
                    it.ownerId != unit.ownerId && it.type == EntityType.UNIT 
                }.forEach {
                    it.abilityData = "revealed_until_${currentTime + 5000}"
                }
                unit.lastAbilityTime = currentTime
            }
            
            UnitType.RESTFUL_HEALER -> {
                // GET=diagnose, POST=heal, PUT=buff, DELETE=cleanse
                val nearbyAllies = entities.values.filter { 
                    it.ownerId == unit.ownerId && 
                    it.type == EntityType.UNIT && 
                    it.id != unit.id && 
                    distance(unit, it) < 90f 
                }
                nearbyAllies.forEach { ally ->
                    // POST: Heal
                    ally.hp = (ally.hp + 15).coerceAtMost(ally.maxHp)
                    // DELETE: Cleanse debuffs
                    if (ally.abilityData.contains("deadlocked") || ally.abilityData.contains("infected")) {
                        ally.abilityData = ""
                        ally.speed = ally.unitType?.let { UnitStatsData.getStats(it).speed } ?: 100f
                    }
                }
                unit.lastAbilityTime = currentTime
            }
            
            // === STORAGE UNITS ===
            UnitType.INDEXER -> {
                // Mark target for bonus damage
                target?.let {
                    it.abilityData = "indexed_by_${unit.id}"
                    unit.lastAbilityTime = currentTime
                }
            }
            
            else -> {}  // No special ability
        }
    }

    
    private suspend fun broadcastGameOver(winnerId: String) {
        val packet = GameOverPacket(winnerId)
        val json = Json.encodeToString<Packet>(packet)
        players.keys.forEach { sessionId ->
             sessions[sessionId]?.send(Frame.Text(json))
        }
        
        // Save game results to database
        saveGameResults(winnerId)
        
        // Clear currentGameRoom for all players so they can start a new match
        players.keys.forEach { sessionId ->
            playerSessions[sessionId]?.currentGameRoom = null
        }
        
        // Stop the game loop
        isRunning = false
    }
    
    private fun saveGameResults(winnerId: String) {
        if (!DatabaseConfig.isConnected() || dbSessionId == null) return
        
        val gameDuration = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
        
        // Complete session in database
        GameRepository.completeSession(dbSessionId!!, winnerId)
        
        // Record match results for each player
        players.forEach { (playerId, player) ->
            val stats = playerStats[playerId] ?: PlayerGameStats()
            val isWinner = playerId == winnerId
            
            GameRepository.recordMatchResult(
                sessionId = dbSessionId!!,
                playerId = playerId,
                isWinner = isWinner,
                finalMemory = player.memory,
                finalCpu = player.cpu,
                unitsCreated = stats.unitsCreated,
                unitsLost = stats.unitsLost,
                unitsKilled = stats.unitsKilled,
                factoriesBuilt = stats.factoriesBuilt,
                resourcesCaptured = stats.resourcesCaptured,
                cardsPlayed = stats.cardsPlayed,
                gameDurationSeconds = gameDuration
            )
            
            // Update player aggregate stats
            GameRepository.updatePlayerStats(
                playerId = playerId,
                won = isWinner,
                unitsCreated = stats.unitsCreated,
                unitsKilled = stats.unitsKilled,
                factoriesBuilt = stats.factoriesBuilt,
                cardsPlayed = stats.cardsPlayed,
                playTimeSeconds = gameDuration
            )
        }
        
        println("[Database] Game results saved. Winner: $winnerId, Duration: ${gameDuration}s")
    }
    
    private fun distance(a: GameEntity, b: GameEntity): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return Math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
    }

    private suspend fun broadcastState() {
        try {
            // Build factory states from queues
            val factoryStates = factoryQueues.mapNotNull { (factoryId, queue) ->
                val factory = entities[factoryId] ?: return@mapNotNull null
                FactoryState(
                    id = factoryId,
                    ownerId = factory.ownerId,
                    factoryType = factory.factoryType ?: FactoryType.STANDARD,
                    productionQueue = queue.map { item ->
                        QueuedUnit(item.unitType, item.remainingTime, item.targetX, item.targetY, item.targetEntityId)
                    },
                    x = factory.x,
                    y = factory.y
                )
            }
            
            val packet = StateUpdatePacket(
                entities = entities.values.toList(),
                players = players.values.toList(),
                factories = factoryStates,
                serverTime = System.currentTimeMillis()
            )
            val json = Json.encodeToString<Packet>(packet)
            sessions.values.forEach { session ->
                try {
                    session.send(Frame.Text(json))
                } catch (e: Exception) {
                    // handle disconnect
                }
            }
        } catch (e: Exception) {
            println("Broadcast error: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun handleSurrender(sessionId: String) {
        // Find the opponent - they are the winner
        val opponentId = players.keys.firstOrNull { it != sessionId }
        if (opponentId != null) {
            // Broadcast game over with opponent as winner
            broadcastGameOver(opponentId)
        }
    }

    fun removePlayer(sessionId: String) {
        // Track disconnect in database (if game was in progress)
        if (DatabaseConfig.isConnected() && dbSessionId != null && players.size > 1) {
            GameRepository.abandonSession(dbSessionId!!, sessionId)
        }
        
        // Clear currentGameRoom for this player
        playerSessions[sessionId]?.currentGameRoom = null
        
        // Notify opponent of disconnect and clear their game room
        gameScope.launch {
            val opponentId = players.keys.firstOrNull { it != sessionId }
            if (opponentId != null) {
                val opponentSocket = sessions[opponentId]
                if (opponentSocket != null) {
                    try {
                        val packet = OpponentDisconnectedPacket(
                            message = "Your opponent has disconnected",
                            youWin = true
                        )
                        opponentSocket.send(Frame.Text(Json.encodeToString<Packet>(packet)))
                        
                        // Clear opponent's game room too
                        playerSessions[opponentId]?.currentGameRoom = null
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
        
        players.remove(sessionId)
        playerStats.remove(sessionId)
        sessions.remove(sessionId)
        // Cleanup entities owned by this player
        entities.entries.removeIf { it.value.ownerId == sessionId }
        
        // Stop the game if no players left
        if (players.isEmpty()) {
            isRunning = false
        }
    }
}
