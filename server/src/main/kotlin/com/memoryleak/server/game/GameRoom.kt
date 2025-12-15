package com.memoryleak.server.game

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
    
    private var gameScope = CoroutineScope(Dispatchers.Default)
    private var isRunning = false
    private var lastTickTime = System.currentTimeMillis()

    fun start() {
        if (isRunning) return
        isRunning = true
        
        // Initialize Map Resources
        spawnResource("mem_1", ResourceType.MEMORY, 100f, 100f)
        spawnResource("mem_2", ResourceType.MEMORY, 700f, 500f)
        spawnResource("cpu_1", ResourceType.CPU, 700f, 100f)
        spawnResource("cpu_2", ResourceType.CPU, 100f, 500f)

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

    suspend fun join(sessionId: String, socket: WebSocketSession): PlayerState {
        sessions[sessionId] = socket
        val player = PlayerState(
            id = sessionId, 
            name = "Player-$sessionId",
            memory = 200,
            cpu = 100
        )
        
        // Initialize deck and draw starting hand
        player.deck = DeckBuilder.createDefaultDeck()
        repeat(4) { DeckBuilder.drawCard(player) }
        
        players[sessionId] = player
        
        // Spawn Instance for player
        val instanceId = UUID.randomUUID().toString()
        entities[instanceId] = GameEntity(
            id = instanceId,
            type = EntityType.INSTANCE,
            x = (Math.random() * 800).toFloat(), // Random spawn
            y = (Math.random() * 600).toFloat(),
            ownerId = sessionId,
            hp = 1000,
            maxHp = 1000,
            speed = 15f  // Very slow
        )
        
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
                
                CardType.BUILD_FACTORY -> {
                    val factoryId = UUID.randomUUID().toString()
                    entities[factoryId] = GameEntity(
                        id = factoryId,
                        type = EntityType.FACTORY,
                        x = cmd.targetX,
                        y = cmd.targetY,
                        ownerId = sessionId,
                        hp = 200,
                        maxHp = 200,
                        speed = 35f
                    )
                }
            }
            
            // Draw new card
            DeckBuilder.drawCard(player)
        }
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
    }
    
    private fun distance(a: GameEntity, b: GameEntity): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return Math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
    }

    private suspend fun broadcastState() {
        try {
            val packet = StateUpdatePacket(
                entities = entities.values.toList(),
                players = players.values.toList(),
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

    fun removePlayer(sessionId: String) {
        players.remove(sessionId)
        // Cleanup entities owned by this player
        entities.entries.removeIf { it.value.ownerId == sessionId }
    }
}
