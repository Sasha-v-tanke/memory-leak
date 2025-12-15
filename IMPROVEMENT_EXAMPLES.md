# Примеры улучшений с кодом

Этот документ содержит готовые примеры кода для улучшения проекта Memory Leak.

## 1. Система матчмейкинга

### MatchmakingService.kt
```kotlin
package com.memoryleak.server.matchmaking

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

data class PlayerQueue(
    val playerId: String,
    val rating: Int,
    val joinTime: Long
)

data class Match(
    val id: String,
    val players: List<String>,
    val roomId: String
)

class MatchmakingService {
    private val queue = ConcurrentLinkedQueue<PlayerQueue>()
    private val scope = CoroutineScope(Dispatchers.Default)
    
    fun start() {
        scope.launch {
            while (isActive) {
                processQueue()
                delay(1000L) // Check every second
            }
        }
    }
    
    fun joinQueue(playerId: String, rating: Int) {
        queue.offer(PlayerQueue(playerId, rating, System.currentTimeMillis()))
        println("Player $playerId joined matchmaking (rating: $rating)")
    }
    
    fun leaveQueue(playerId: String) {
        queue.removeIf { it.playerId == playerId }
    }
    
    private fun processQueue() {
        if (queue.size < 2) return
        
        val players = queue.toList().sortedBy { it.rating }
        val matches = mutableListOf<Match>()
        
        // Simple 1v1 matching (closest ratings)
        var i = 0
        while (i < players.size - 1) {
            val p1 = players[i]
            val p2 = players[i + 1]
            
            // Check rating difference
            if (kotlin.math.abs(p1.rating - p2.rating) < 200) {
                val matchId = java.util.UUID.randomUUID().toString()
                matches.add(Match(matchId, listOf(p1.playerId, p2.playerId), matchId))
                
                // Remove from queue
                queue.remove(p1)
                queue.remove(p2)
                
                println("Match created: ${p1.playerId} vs ${p2.playerId}")
            }
            i += 2
        }
        
        // Timeout matching (wait > 30 seconds, match with anyone)
        val now = System.currentTimeMillis()
        val waiting = queue.filter { now - it.joinTime > 30000 }
        if (waiting.size >= 2) {
            val p1 = waiting[0]
            val p2 = waiting[1]
            val matchId = java.util.UUID.randomUUID().toString()
            matches.add(Match(matchId, listOf(p1.playerId, p2.playerId), matchId))
            queue.remove(p1)
            queue.remove(p2)
            println("Timeout match created: ${p1.playerId} vs ${p2.playerId}")
        }
    }
}
```

## 2. Персистентность игрока (Database)

### PlayerRepository.kt
```kotlin
package com.memoryleak.server.persistence

import kotlinx.serialization.Serializable
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class PlayerProfile(
    val id: String,
    val username: String,
    var level: Int = 1,
    var experience: Int = 0,
    var wins: Int = 0,
    var losses: Int = 0,
    var gamesPlayed: Int = 0,
    var rating: Int = 1000,
    val unlockedCards: MutableSet<String> = mutableSetOf(),
    val customDecks: MutableMap<String, List<String>> = mutableMapOf()
)

class PlayerRepository {
    private val dataDir = File("data/players")
    private val json = Json { prettyPrint = true }
    
    init {
        dataDir.mkdirs()
    }
    
    fun save(profile: PlayerProfile) {
        val file = File(dataDir, "${profile.id}.json")
        file.writeText(json.encodeToString(profile))
    }
    
    fun load(playerId: String): PlayerProfile? {
        val file = File(dataDir, "$playerId.json")
        return if (file.exists()) {
            try {
                json.decodeFromString<PlayerProfile>(file.readText())
            } catch (e: Exception) {
                println("Error loading profile: ${e.message}")
                null
            }
        } else {
            null
        }
    }
    
    fun create(playerId: String, username: String): PlayerProfile {
        val profile = PlayerProfile(
            id = playerId,
            username = username,
            unlockedCards = mutableSetOf(
                "SPAWN_SCOUT",
                "SPAWN_BASIC_PROCESS",
                "SPAWN_ALLOCATOR",
                "BUILD_FACTORY"
            )
        )
        save(profile)
        return profile
    }
    
    fun updateAfterGame(playerId: String, won: Boolean, experience: Int) {
        val profile = load(playerId) ?: return
        
        profile.gamesPlayed++
        if (won) {
            profile.wins++
            profile.rating += 25
        } else {
            profile.losses++
            profile.rating = maxOf(0, profile.rating - 20)
        }
        
        profile.experience += experience
        
        // Level up system
        val expForNextLevel = profile.level * 100
        if (profile.experience >= expForNextLevel) {
            profile.level++
            profile.experience -= expForNextLevel
            println("Player $playerId leveled up to ${profile.level}!")
            
            // Unlock new card at certain levels
            unlockCardForLevel(profile, profile.level)
        }
        
        save(profile)
    }
    
    private fun unlockCardForLevel(profile: PlayerProfile, level: Int) {
        val unlockMap = mapOf(
            2 to "SPAWN_TANK",
            3 to "SPAWN_RANGED",
            4 to "SPAWN_GARBAGE_COLLECTOR",
            5 to "SPAWN_INHERITANCE_DRONE",
            7 to "SPAWN_POLYMORPH_WARRIOR",
            10 to "SPAWN_LAMBDA_SNIPER"
            // ... больше уровней
        )
        
        unlockMap[level]?.let { cardType ->
            profile.unlockedCards.add(cardType)
            println("Unlocked $cardType for player ${profile.id}!")
        }
    }
}
```

## 3. Улучшенная AI с состояниями

### UnitAI.kt
```kotlin
package com.memoryleak.server.ai

import com.memoryleak.shared.model.*
import com.memoryleak.server.game.GameWorld
import kotlin.math.sqrt

class UnitAI {
    companion object {
        const val RETREAT_HP_THRESHOLD = 0.25f  // Retreat when below 25% HP
        const val CHASE_DISTANCE = 300f
    }
    
    fun update(unit: GameEntity, world: GameWorld, delta: Float) {
        // Check if should retreat (low HP)
        val hpPercent = unit.hp.toFloat() / unit.maxHp.toFloat()
        if (hpPercent < RETREAT_HP_THRESHOLD && unit.aiState != AIState.RETREATING) {
            unit.aiState = AIState.RETREATING
            unit.targetEnemyId = null
        }
        
        when (unit.aiState) {
            AIState.IDLE -> handleIdle(unit, world)
            AIState.MOVING_TO_TARGET -> handleMoving(unit, world, delta)
            AIState.ATTACKING -> handleAttacking(unit, world)
            AIState.RETREATING -> handleRetreating(unit, world, delta)
            AIState.USING_ABILITY -> handleAbility(unit, world)
        }
    }
    
    private fun handleIdle(unit: GameEntity, world: GameWorld) {
        // Look for targets
        val target = findBestTarget(unit, world)
        if (target != null) {
            unit.targetEnemyId = target.id
            unit.aiState = AIState.MOVING_TO_TARGET
        } else {
            // No enemies, maybe patrol or capture nodes
            handlePatrol(unit, world)
        }
    }
    
    private fun handleMoving(unit: GameEntity, world: GameWorld, delta: Float) {
        val target = unit.targetEnemyId?.let { world.getEntity(it) }
        
        if (target == null) {
            unit.aiState = AIState.IDLE
            return
        }
        
        val distance = calculateDistance(unit, target)
        val stats = UnitStatsData.getStats(unit.unitType ?: return)
        
        if (distance <= stats.attackRange) {
            unit.aiState = AIState.ATTACKING
        } else if (distance > CHASE_DISTANCE) {
            // Too far, give up chase
            unit.targetEnemyId = null
            unit.aiState = AIState.IDLE
        } else {
            // Move towards target
            moveTowards(unit, target.x, target.y, delta)
        }
    }
    
    private fun handleAttacking(unit: GameEntity, world: GameWorld) {
        val target = unit.targetEnemyId?.let { world.getEntity(it) }
        
        if (target == null) {
            unit.aiState = AIState.IDLE
            return
        }
        
        val stats = UnitStatsData.getStats(unit.unitType ?: return)
        val distance = calculateDistance(unit, target)
        
        if (distance > stats.attackRange) {
            unit.aiState = AIState.MOVING_TO_TARGET
        } else {
            // Attack is handled by combat system
            unit.attackingTargetId = target.id
        }
    }
    
    private fun handleRetreating(unit: GameEntity, world: GameWorld, delta: Float) {
        // Find friendly base
        val base = world.entities.values.find { 
            it.type == EntityType.INSTANCE && it.ownerId == unit.ownerId 
        }
        
        if (base != null) {
            moveTowards(unit, base.x, base.y, delta)
            
            val distance = calculateDistance(unit, base)
            if (distance < 50f) {
                // Near base, check if HP recovered
                val hpPercent = unit.hp.toFloat() / unit.maxHp.toFloat()
                if (hpPercent > 0.5f) {
                    unit.aiState = AIState.IDLE
                }
            }
        } else {
            // No base, just flee from enemies
            val nearestEnemy = findNearestEnemy(unit, world)
            if (nearestEnemy != null) {
                val dx = unit.x - nearestEnemy.x
                val dy = unit.y - nearestEnemy.y
                moveTowards(unit, unit.x + dx, unit.y + dy, delta)
            }
        }
    }
    
    private fun handleAbility(unit: GameEntity, world: GameWorld) {
        // Ability activation handled separately
        // After ability, return to previous state
        unit.aiState = AIState.IDLE
    }
    
    private fun handlePatrol(unit: GameEntity, world: GameWorld) {
        // Special behavior for certain unit types
        when (unit.unitType) {
            UnitType.ALLOCATOR, UnitType.CACHE_RUNNER -> {
                // Move to nearest uncaptured resource node
                val node = world.entities.values
                    .filter { it.type == EntityType.RESOURCE_NODE && it.ownerId != unit.ownerId }
                    .minByOrNull { calculateDistance(unit, it) }
                
                if (node != null) {
                    unit.targetX = node.x
                    unit.targetY = node.y
                }
            }
            else -> {
                // Default: stay near base
                val base = world.entities.values.find {
                    it.type == EntityType.INSTANCE && it.ownerId == unit.ownerId
                }
                base?.let {
                    // Patrol around base
                    val angle = (System.currentTimeMillis() / 3000.0) % (2 * Math.PI)
                    val radius = 150f
                    unit.targetX = it.x + (Math.cos(angle) * radius).toFloat()
                    unit.targetY = it.y + (Math.sin(angle) * radius).toFloat()
                }
            }
        }
    }
    
    private fun findBestTarget(unit: GameEntity, world: GameWorld): GameEntity? {
        val enemies = world.entities.values.filter { 
            it.ownerId != unit.ownerId && 
            it.ownerId != "0" && 
            it.type != EntityType.RESOURCE_NODE 
        }
        
        if (enemies.isEmpty()) return null
        
        // Priority: Weakest nearby unit > Factory > Base
        return enemies
            .filter { calculateDistance(unit, it) < 400f }
            .sortedWith(compareBy(
                { when(it.type) {
                    EntityType.UNIT -> 1
                    EntityType.FACTORY -> 2
                    EntityType.INSTANCE -> 3
                    else -> 4
                }},
                { it.hp.toFloat() / it.maxHp.toFloat() },  // Target weak units first
                { calculateDistance(unit, it) }
            ))
            .firstOrNull()
    }
    
    private fun findNearestEnemy(unit: GameEntity, world: GameWorld): GameEntity? {
        return world.entities.values
            .filter { it.ownerId != unit.ownerId && it.ownerId != "0" }
            .minByOrNull { calculateDistance(unit, it) }
    }
    
    private fun moveTowards(unit: GameEntity, targetX: Float, targetY: Float, delta: Float) {
        val dx = targetX - unit.x
        val dy = targetY - unit.y
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance > 1f) {
            val moveAmount = unit.speed * delta
            unit.x += (dx / distance) * moveAmount
            unit.y += (dy / distance) * moveAmount
        }
    }
    
    private fun calculateDistance(a: GameEntity, b: GameEntity): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

// GameWorld interface for cleaner access
interface GameWorld {
    val entities: Map<String, GameEntity>
    fun getEntity(id: String): GameEntity?
}
```

## 4. Система достижений

### AchievementSystem.kt
```kotlin
package com.memoryleak.server.achievements

import kotlinx.serialization.Serializable

@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val requirement: Int,
    val rewardType: RewardType,
    val rewardValue: Int
)

@Serializable
enum class RewardType {
    EXPERIENCE, UNLOCK_CARD, RATING_BOOST
}

@Serializable
data class PlayerAchievements(
    val playerId: String,
    val unlockedAchievements: MutableSet<String> = mutableSetOf(),
    val progress: MutableMap<String, Int> = mutableMapOf()
)

class AchievementSystem {
    private val achievements = listOf(
        Achievement(
            "first_win",
            "Первая победа",
            "Выиграйте свою первую игру",
            1,
            RewardType.EXPERIENCE,
            100
        ),
        Achievement(
            "win_10",
            "Опытный боец",
            "Выиграйте 10 игр",
            10,
            RewardType.UNLOCK_CARD,
            0
        ),
        Achievement(
            "spawn_100_units",
            "Армия",
            "Создайте 100 юнитов",
            100,
            RewardType.EXPERIENCE,
            250
        ),
        Achievement(
            "master_allocator",
            "Мастер ресурсов",
            "Соберите 10000 Memory с помощью Allocator",
            10000,
            RewardType.UNLOCK_CARD,
            0
        ),
        Achievement(
            "unstoppable",
            "Неудержимый",
            "Выиграйте 5 игр подряд",
            5,
            RewardType.RATING_BOOST,
            50
        )
    )
    
    fun checkAchievements(
        playerAchievements: PlayerAchievements,
        stats: GameStats
    ): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()
        
        achievements.forEach { achievement ->
            if (achievement.id in playerAchievements.unlockedAchievements) {
                return@forEach  // Already unlocked
            }
            
            // Update progress
            val currentProgress = when (achievement.id) {
                "first_win" -> stats.wins
                "win_10" -> stats.wins
                "spawn_100_units" -> stats.totalUnitsSpawned
                "master_allocator" -> stats.memoryCollectedByAllocator
                "unstoppable" -> stats.winStreak
                else -> 0
            }
            
            playerAchievements.progress[achievement.id] = currentProgress
            
            // Check if unlocked
            if (currentProgress >= achievement.requirement) {
                playerAchievements.unlockedAchievements.add(achievement.id)
                newlyUnlocked.add(achievement)
                println("Achievement unlocked: ${achievement.name}")
            }
        }
        
        return newlyUnlocked
    }
    
    fun getProgress(playerAchievements: PlayerAchievements): Map<Achievement, Float> {
        return achievements.associateWith { achievement ->
            val progress = playerAchievements.progress[achievement.id] ?: 0
            progress.toFloat() / achievement.requirement.toFloat()
        }
    }
}

@Serializable
data class GameStats(
    val wins: Int,
    val totalUnitsSpawned: Int,
    val memoryCollectedByAllocator: Int,
    val winStreak: Int
)
```

## 5. Кастомные колоды

### DeckCustomization.kt
```kotlin
package com.memoryleak.server.deck

import com.memoryleak.shared.model.*
import java.util.UUID

data class CustomDeck(
    val id: String,
    val name: String,
    val ownerId: String,
    val cardTypes: List<CardType>,
    val createdAt: Long = System.currentTimeMillis()
)

class DeckCustomizationSystem(
    private val playerRepository: PlayerRepository
) {
    companion object {
        const val MIN_DECK_SIZE = 20
        const val MAX_DECK_SIZE = 40
        const val MAX_CARD_COPIES = 3
    }
    
    fun createDeck(
        playerId: String,
        deckName: String,
        cardTypes: List<CardType>
    ): Result<CustomDeck> {
        // Validate deck
        val validation = validateDeck(playerId, cardTypes)
        if (!validation.isValid) {
            return Result.failure(Exception(validation.errorMessage))
        }
        
        val deck = CustomDeck(
            id = UUID.randomUUID().toString(),
            name = deckName,
            ownerId = playerId,
            cardTypes = cardTypes
        )
        
        // Save to player profile
        val profile = playerRepository.load(playerId) ?: return Result.failure(Exception("Player not found"))
        profile.customDecks[deck.id] = cardTypes.map { it.name }
        playerRepository.save(profile)
        
        return Result.success(deck)
    }
    
    fun validateDeck(playerId: String, cardTypes: List<CardType>): DeckValidation {
        val profile = playerRepository.load(playerId) 
            ?: return DeckValidation(false, "Player not found")
        
        // Check size
        if (cardTypes.size < MIN_DECK_SIZE) {
            return DeckValidation(false, "Deck must have at least $MIN_DECK_SIZE cards")
        }
        if (cardTypes.size > MAX_DECK_SIZE) {
            return DeckValidation(false, "Deck cannot exceed $MAX_DECK_SIZE cards")
        }
        
        // Check card copies
        val cardCounts = cardTypes.groupingBy { it }.eachCount()
        cardCounts.forEach { (cardType, count) ->
            if (count > MAX_CARD_COPIES) {
                return DeckValidation(false, "Cannot have more than $MAX_CARD_COPIES copies of $cardType")
            }
        }
        
        // Check unlocked cards
        cardTypes.forEach { cardType ->
            if (cardType.name !in profile.unlockedCards) {
                return DeckValidation(false, "Card $cardType is not unlocked")
            }
        }
        
        // Must have at least one way to get resources
        val hasResourceCards = cardTypes.any { it in listOf(
            CardType.SPAWN_ALLOCATOR,
            CardType.BUILD_FACTORY
        )}
        if (!hasResourceCards) {
            return DeckValidation(false, "Deck must contain resource-generating cards")
        }
        
        return DeckValidation(true, "")
    }
    
    fun getDeck(deckId: String, playerId: String): CustomDeck? {
        val profile = playerRepository.load(playerId) ?: return null
        val cardTypeNames = profile.customDecks[deckId] ?: return null
        
        val cardTypes = cardTypeNames.mapNotNull { name ->
            try {
                CardType.valueOf(name)
            } catch (e: Exception) {
                null
            }
        }
        
        return CustomDeck(
            id = deckId,
            name = "Custom Deck",
            ownerId = playerId,
            cardTypes = cardTypes
        )
    }
    
    fun buildDeckForGame(customDeck: CustomDeck): MutableList<Card> {
        return customDeck.cardTypes.map { cardType ->
            val (memoryCost, cpuCost) = getCardCosts(cardType)
            Card(
                id = UUID.randomUUID().toString(),
                type = cardType,
                memoryCost = memoryCost,
                cpuCost = cpuCost
            )
        }.shuffled().toMutableList()
    }
    
    private fun getCardCosts(cardType: CardType): Pair<Int, Int> {
        return when (cardType) {
            CardType.SPAWN_SCOUT -> 30 to 20
            CardType.SPAWN_TANK -> 80 to 40
            CardType.SPAWN_ALLOCATOR -> 40 to 30
            CardType.BUILD_FACTORY -> 100 to 0
            // ... все остальные карты
            else -> 50 to 50
        }
    }
}

data class DeckValidation(
    val isValid: Boolean,
    val errorMessage: String
)
```

## 6. Replay система

### ReplaySystem.kt
```kotlin
package com.memoryleak.server.replay

import com.memoryleak.shared.model.GameEntity
import com.memoryleak.shared.model.PlayerState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream

@Serializable
data class ReplayFrame(
    val tick: Long,
    val timestamp: Long,
    val entities: List<GameEntity>,
    val players: List<PlayerState>
)

@Serializable
data class ReplayMetadata(
    val gameId: String,
    val startTime: Long,
    val endTime: Long,
    val playerNames: List<String>,
    val winnerId: String?,
    val duration: Long
)

@Serializable
data class Replay(
    val metadata: ReplayMetadata,
    val frames: List<ReplayFrame>
)

class ReplaySystem {
    private val replayDir = File("data/replays")
    private val json = Json { prettyPrint = false }
    
    private var currentRecording: MutableList<ReplayFrame>? = null
    private var recordingMetadata: ReplayMetadata? = null
    private var tick: Long = 0
    
    init {
        replayDir.mkdirs()
    }
    
    fun startRecording(gameId: String, playerNames: List<String>) {
        currentRecording = mutableListOf()
        recordingMetadata = ReplayMetadata(
            gameId = gameId,
            startTime = System.currentTimeMillis(),
            endTime = 0,
            playerNames = playerNames,
            winnerId = null,
            duration = 0
        )
        tick = 0
        println("Started recording replay for game $gameId")
    }
    
    fun recordFrame(entities: List<GameEntity>, players: List<PlayerState>) {
        currentRecording?.add(ReplayFrame(
            tick = tick++,
            timestamp = System.currentTimeMillis(),
            entities = entities,
            players = players
        ))
    }
    
    fun stopRecording(winnerId: String?): String? {
        val recording = currentRecording ?: return null
        val metadata = recordingMetadata ?: return null
        
        val finalMetadata = metadata.copy(
            endTime = System.currentTimeMillis(),
            winnerId = winnerId,
            duration = System.currentTimeMillis() - metadata.startTime
        )
        
        val replay = Replay(
            metadata = finalMetadata,
            frames = recording
        )
        
        val replayId = saveReplay(replay)
        currentRecording = null
        recordingMetadata = null
        
        println("Stopped recording replay: $replayId")
        return replayId
    }
    
    private fun saveReplay(replay: Replay): String {
        val replayId = replay.metadata.gameId
        val file = File(replayDir, "$replayId.replay")
        
        // Compress with GZIP to save space
        GZIPOutputStream(file.outputStream()).bufferedWriter().use { writer ->
            writer.write(json.encodeToString(replay))
        }
        
        println("Replay saved: ${file.path} (${file.length() / 1024} KB)")
        return replayId
    }
    
    fun loadReplay(replayId: String): Replay? {
        val file = File(replayDir, "$replayId.replay")
        if (!file.exists()) return null
        
        return try {
            val jsonText = GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
            json.decodeFromString<Replay>(jsonText)
        } catch (e: Exception) {
            println("Error loading replay: ${e.message}")
            null
        }
    }
    
    fun listReplays(): List<ReplayMetadata> {
        return replayDir.listFiles()
            ?.filter { it.extension == "replay" }
            ?.mapNotNull { file ->
                try {
                    val replay = loadReplay(file.nameWithoutExtension)
                    replay?.metadata
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }
}

// Клиент для воспроизведения
class ReplayPlayer(private val replay: Replay) {
    private var currentFrame = 0
    private val startTime = System.currentTimeMillis()
    
    fun getCurrentFrame(): ReplayFrame? {
        if (currentFrame >= replay.frames.size) return null
        return replay.frames[currentFrame]
    }
    
    fun update(): ReplayFrame? {
        val elapsed = System.currentTimeMillis() - startTime
        val targetFrame = (elapsed / 16).toInt()  // 60 FPS
        
        if (targetFrame < replay.frames.size) {
            currentFrame = targetFrame
            return replay.frames[currentFrame]
        }
        
        return null  // Replay ended
    }
    
    fun seek(frameNumber: Int) {
        currentFrame = frameNumber.coerceIn(0, replay.frames.size - 1)
    }
    
    fun pause() {
        // Implement pause logic
    }
    
    fun setSpeed(speed: Float) {
        // Implement playback speed (0.5x, 1x, 2x)
    }
}
```

## 7. Интеграция всех улучшений

### EnhancedGameRoom.kt
```kotlin
package com.memoryleak.server.game

import com.memoryleak.server.ai.UnitAI
import com.memoryleak.server.achievements.AchievementSystem
import com.memoryleak.server.persistence.PlayerRepository
import com.memoryleak.server.replay.ReplaySystem

class EnhancedGameRoom(
    private val roomId: String,
    private val playerRepository: PlayerRepository,
    private val achievementSystem: AchievementSystem,
    private val replaySystem: ReplaySystem
) : GameWorld {
    // Existing game state
    override val entities = ConcurrentHashMap<String, GameEntity>()
    private val players = ConcurrentHashMap<String, PlayerState>()
    
    // New systems
    private val unitAI = UnitAI()
    private var isRecording = false
    
    fun start() {
        // Start replay recording
        val playerNames = players.values.map { it.name }
        replaySystem.startRecording(roomId, playerNames)
        isRecording = true
        
        // ... existing start logic
    }
    
    private suspend fun update(delta: Float) {
        // ... existing update logic
        
        // Use enhanced AI
        entities.values.filter { it.type == EntityType.UNIT }.forEach { unit ->
            unitAI.update(unit, this, delta)
        }
        
        // Record frame for replay
        if (isRecording) {
            replaySystem.recordFrame(
                entities.values.toList(),
                players.values.toList()
            )
        }
    }
    
    private suspend fun endGame(winnerId: String) {
        // Stop replay
        val replayId = replaySystem.stopRecording(winnerId)
        isRecording = false
        
        // Update player stats and achievements
        players.values.forEach { player ->
            val won = player.id == winnerId
            playerRepository.updateAfterGame(player.id, won, 100)
            
            // Check achievements
            // val achievements = achievementSystem.checkAchievements(...)
        }
        
        // Broadcast game over
        broadcastGameOver(winnerId)
    }
    
    override fun getEntity(id: String): GameEntity? {
        return entities[id]
    }
}
```

## Заключение

Эти примеры показывают, как можно постепенно улучшать проект:

1. **Матчмейкинг** - для автоматического подбора игроков
2. **Персистентность** - сохранение прогресса игрока
3. **Улучшенная AI** - более умное поведение юнитов
4. **Достижения** - геймификация и мотивация игроков
5. **Кастомные колоды** - персонализация и стратегия
6. **Replay система** - обучение и контент для стримов

Каждая система независима и может быть добавлена отдельно без нарушения существующего кода.
