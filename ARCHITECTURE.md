# Архитектура проекта Memory Leak

## 📐 Диаграмма архитектуры

```
┌─────────────────────────────────────────────────────────────────┐
│                        КЛИЕНТ (LibGDX)                          │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐        ┌───────────────────┐             │
│  │ MemoryLeakGame   │───────▶│  NetworkClient    │             │
│  │ (Rendering)      │        │  (WebSocket)      │             │
│  └──────────────────┘        └───────────────────┘             │
│         │                              │                         │
│         │ Render Loop                  │ State Sync              │
│         ▼                              ▼                         │
│  ┌──────────────────┐        ┌───────────────────┐             │
│  │ ShapeRenderer    │        │ Local State       │             │
│  │ SpriteBatch      │        │ (entities, players)│             │
│  └──────────────────┘        └───────────────────┘             │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  │ WebSocket
                                  │ (ws://host:8080/game)
                                  │
┌─────────────────────────────────────────────────────────────────┐
│                        СЕРВЕР (Ktor)                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐        ┌───────────────────┐             │
│  │ Application.kt   │───────▶│  GameSocket.kt    │             │
│  │ (Entry Point)    │        │  (WebSocket Route)│             │
│  └──────────────────┘        └───────────────────┘             │
│                                      │                           │
│                                      │ Player Commands           │
│                                      ▼                           │
│                          ┌───────────────────┐                  │
│                          │   GameRoom.kt     │                  │
│                          │   (Game Loop)     │                  │
│                          └───────────────────┘                  │
│                                  │                               │
│         ┌────────────────────────┼────────────────────────┐     │
│         │                        │                        │     │
│         ▼                        ▼                        ▼     │
│  ┌─────────────┐      ┌──────────────────┐      ┌────────────┐│
│  │ AI System   │      │ Combat System    │      │ Resource   ││
│  │ (Unit AI)   │      │ (Attacks, HP)    │      │ System     ││
│  └─────────────┘      └──────────────────┘      └────────────┘│
│         │                        │                        │     │
│         │                        │                        │     │
│         └────────────────────────┼────────────────────────┘     │
│                                  │                               │
│                                  ▼                               │
│                          ┌───────────────────┐                  │
│                          │   DeckBuilder.kt  │                  │
│                          │   (Card System)   │                  │
│                          └───────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  │ Uses
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SHARED MODULE (Kotlin)                       │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌────────────────┐  ┌──────────────────┐   │
│  │ Packets.kt   │  │ GameModels.kt  │  │ Cards.kt         │   │
│  │              │  │                │  │                  │   │
│  │ - LoginPacket│  │ - GameEntity   │  │ - CardType       │   │
│  │ - StateUpdate│  │ - PlayerState  │  │ - UnitType       │   │
│  │ - Command    │  │ - EntityType   │  │ - UnitStats      │   │
│  │ - GameOver   │  │ - AIState      │  │ - UnitStatsData  │   │
│  └──────────────┘  └────────────────┘  └──────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 Поток данных

### 1. Подключение клиента
```
Client                Server
  │                     │
  ├──── Connect ───────▶│
  │                     │ Create Session
  │                     │ Create PlayerState
  │                     │ Spawn Base
  │                     │ Create Deck
  │◀── JoinAck ─────────┤
  │   (playerId)        │
```

### 2. Игровой цикл (60 FPS)
```
Server Game Loop:
┌───────────────────────────────┐
│ 1. Update Delta Time          │
│ 2. Tick Resource Generation   │
│ 3. Update All Unit AI         │
│    ├─ Find Targets            │
│    ├─ Move to Target          │
│    ├─ Attack in Range         │
│    └─ Trigger Abilities       │
│ 4. Handle Combat              │
│    ├─ Calculate Damage        │
│    ├─ Apply Special Effects   │
│    └─ Check Win Condition     │
│ 5. Update Resource Nodes      │
│ 6. Broadcast State to Clients│
│ 7. Sleep (16.67ms for 60fps) │
└───────────────────────────────┘
```

### 3. Команды игрока
```
Client                         Server
  │                              │
  ├──── CommandPacket ──────────▶│
  │     (PLAY_CARD)              │ 1. Validate Command
  │                              │ 2. Check Resources
  │                              │ 3. Check Spawn Distance
  │                              │ 4. Deduct Resources
  │                              │ 5. Spawn Unit
  │◀──── StateUpdate ────────────┤ 6. Draw New Card
  │     (Updated Entities)       │ 7. Set Cooldown
```

## 🎮 Системы игры

### AI System
```kotlin
Для каждого юнита каждый тик:

1. Поиск цели (если нет текущей):
   ┌────────────────────────────┐
   │ Приоритет:                 │
   │ 1. UNIT (юниты)            │
   │ 2. FACTORY (фабрики)       │
   │ 3. INSTANCE (базы)         │
   └────────────────────────────┘
   
2. Перемещение:
   if (distance > attackRange) {
       MOVING_TO_TARGET
       dx = target.x - unit.x
       dy = target.y - unit.y
       unit.x += normalize(dx) * speed * delta
       unit.y += normalize(dy) * speed * delta
   }

3. Атака:
   if (distance <= attackRange) {
       ATTACKING
       if (currentTime - lastAttack >= attackCooldown) {
           performAttack(unit, target)
           lastAttack = currentTime
       }
   }

4. Способности:
   if (currentTime - lastAbility >= abilityCooldown) {
       triggerUnitAbility(unit, target)
       lastAbility = currentTime
   }
```

### Resource System
```
Генерация ресурсов (каждую секунду):
┌────────────────────────────────────┐
│ Пассивный доход:                   │
│   Memory: +5                       │
│   CPU:    +5                       │
│                                    │
│ Захваченные узлы:                 │
│   Memory Node: +1 Memory           │
│   CPU Node:    +1 CPU              │
│                                    │
│ Специальные юниты:                │
│   ALLOCATOR: +2 Memory             │
└────────────────────────────────────┘
```

### Card System
```
Deck Structure:
┌─────────────────────────────────┐
│ Deck (30 карт) ──┐              │
│                  │              │
│                  ├─ Draw ──▶ Hand (4 карты max)
│                  │              │
│                  │              │
│ Discard Pile ◀───┘─ Play       │
│                                 │
│ Если Deck пуст:                │
│   Shuffle Discard → Deck       │
└─────────────────────────────────┘

Ограничения:
- Global Cooldown: 1.5 секунды
- Spawn Range: 200 пикселей от базы
- Hand Size: 4 карты максимум
```

### Combat System
```
Базовый урон:
  target.hp -= attacker.damage

Модификаторы:
  ┌─────────────────────────────────┐
  │ POLYMORPH_WARRIOR:              │
  │   vs UNIT:     +30% damage      │
  │   vs FACTORY:  +50% damage      │
  │   vs INSTANCE: +100% damage     │
  │                                 │
  │ COROUTINE_ARCHER:               │
  │   +30% armor penetration        │
  │                                 │
  │ INDEXER bonus:                  │
  │   Marked targets: +25% damage   │
  └─────────────────────────────────┘

При смерти:
  ┌─────────────────────────────────┐
  │ PROMISE_KNIGHT:                 │
  │   AoE 80 range, 15 damage       │
  │                                 │
  │ RECURSIVE_BOMB:                 │
  │   Split into 2 smaller bombs    │
  │   (Max 2 recursions)            │
  │                                 │
  │ GARBAGE_COLLECTOR kill bonus:   │
  │   +3 Memory, +2 CPU             │
  └─────────────────────────────────┘
```

## 📊 Структура данных

### GameEntity
```kotlin
data class GameEntity(
    // Identity
    val id: String,
    val type: EntityType,
    
    // Position
    var x: Float,
    var y: Float,
    
    // Ownership
    var ownerId: String,  // "0" = neutral
    
    // Health
    var hp: Int,
    val maxHp: Int,
    
    // Movement
    var targetX: Float?,
    var targetY: Float?,
    var speed: Float,
    
    // Combat
    var attackingTargetId: String?,
    var lastAttackTime: Long,
    
    // AI
    var aiState: AIState,
    var targetEnemyId: String?,
    var lastAbilityTime: Long,
    var abilityData: String,
    
    // Type Specific
    var resourceType: ResourceType?,
    var resourceAmount: Int,
    var unitType: UnitType?
)
```

### PlayerState
```kotlin
data class PlayerState(
    val id: String,
    val name: String,
    
    // Resources
    var memory: Int,
    var cpu: Int,
    
    // Card System
    var deck: MutableList<Card>,
    var hand: MutableList<Card>,
    var discardPile: MutableList<Card>,
    var globalCooldown: Float
)
```

## 🔌 Сетевой протокол

### Packet Types
```kotlin
sealed class Packet

// Client → Server
data class LoginPacket(name: String)
data class CommandPacket(
    commandType: CommandType,
    entityId: String?,
    targetX: Float,
    targetY: Float,
    cardId: String?
)

// Server → Client
data class JoinAckPacket(
    playerId: String,
    mapWidth: Float,
    mapHeight: Float
)

data class StateUpdatePacket(
    entities: List<GameEntity>,
    players: List<PlayerState>,
    serverTime: Long
)

data class GameOverPacket(
    winnerId: String
)
```

### Сериализация
```json
// StateUpdatePacket пример:
{
  "type": "state_update",
  "entities": [
    {
      "id": "abc-123",
      "type": "UNIT",
      "x": 100.5,
      "y": 200.3,
      "ownerId": "player-1",
      "hp": 45,
      "maxHp": 50,
      "unitType": "TANK",
      "aiState": "ATTACKING"
    }
  ],
  "players": [
    {
      "id": "player-1",
      "name": "Player-player-1",
      "memory": 150,
      "cpu": 80,
      "hand": [...],
      "globalCooldown": 0.5
    }
  ],
  "serverTime": 1671234567890
}
```

## 🎨 Визуальная система (Клиент)

### Рендеринг Pipeline
```
1. Clear Screen (Dark Blue/Gray Background)
2. Draw Grid (Faint White Lines)
3. World Space Rendering:
   ├─ Draw Entities (ShapeRenderer Filled)
   │  ├─ Shapes based on type
   │  ├─ Colors based on owner/type
   │  └─ Selection highlight ring
   ├─ Draw HP Bars (ShapeRenderer Filled)
   ├─ Draw Attack Lasers (ShapeRenderer Line)
   └─ Draw Entity Labels (SpriteBatch + Font)
4. UI Space Rendering:
   ├─ Draw Card Hand (ShapeRenderer + SpriteBatch)
   │  ├─ Card backgrounds
   │  ├─ Card borders
   │  ├─ Cooldown overlay
   │  └─ Card text (name, costs)
   ├─ Draw Resource Display (SpriteBatch)
   ├─ Draw Selection Panel (SpriteBatch)
   ├─ Draw Instructions (SpriteBatch)
   └─ Draw Game Over Screen (SpriteBatch)
5. Spawn Radius Visualization (if placing card)
```

### Entity Shapes
```
INSTANCE (Base):
  ▢▢▢▢   Square (Cyan outer)
  ▢▢▢    with inner square (Dark blue)

RESOURCE_NODE:
  ◇      Diamond shape
         (Gold=neutral, Green=friendly, Red=enemy)

FACTORY:
  △      Triangle (Purple)

UNIT (depends on type):
  SCOUT:           ●  Small circle (Light green)
  TANK:            ▢  Square (Dark green)
  RANGED:          △  Triangle (Light blue)
  ALLOCATOR:       ● + gold center
  POLYMORPH:       ▢  Purple square
  LAMBDA_SNIPER:   ▷  Right-pointing triangle
  RECURSIVE_BOMB:  ●● Nested circles (Red-orange)
  ... и т.д.
```

## 🔧 Конфигурация

### Константы игры
```kotlin
// Server
const val TICK_RATE = 60                    // FPS
const val PASSIVE_INCOME_MEMORY = 5         // Per second
const val PASSIVE_INCOME_CPU = 5            // Per second
const val NODE_INCOME = 1                   // Per second per node
const val ALLOCATOR_INCOME = 2              // Per second per allocator
const val GLOBAL_COOLDOWN = 1.5f            // Seconds
const val SPAWN_RANGE = 200f                // Pixels from base
const val ABILITY_COOLDOWN = 3000L          // Milliseconds

// Client
const val WINDOW_WIDTH = 800f
const val WINDOW_HEIGHT = 600f
const val CAMERA_SPEED = 5f
const val HAND_SIZE = 4
```

### Начальные условия
```kotlin
// Player Start
memory = 200
cpu = 100
handSize = 4
deckSize = 30

// Map
resourceNodes = 4 (2 Memory, 2 CPU)
mapSize = 800x600

// Base
hp = 1000
speed = 15f (Very slow)
```

## 🎯 Точки расширения

### Где добавлять новые фичи:

1. **Новые типы юнитов**:
   - `shared/Cards.kt` - добавить CardType, UnitType
   - `shared/Cards.kt` - добавить UnitStats в UnitStatsData
   - `server/GameRoom.kt` - добавить case в spawnUnitByCard
   - `server/GameRoom.kt` - добавить способность в triggerUnitAbility
   - `core/MemoryLeakGame.kt` - добавить визуализацию
   - `server/DeckBuilder.kt` - добавить карту в колоду

2. **Новые игровые режимы**:
   - Создать `GameMode` interface
   - Реализовать `PvEMode`, `TournamentMode` и т.д.
   - Модифицировать GameRoom для поддержки режимов

3. **Система прогрессии**:
   - Создать `PlayerProgression` model в shared
   - Добавить persistence layer (БД)
   - Реализовать unlock system

4. **Улучшенная графика**:
   - Заменить ShapeRenderer на TextureAtlas
   - Добавить ParticleEffect для визуальных эффектов
   - Добавить анимации через Animation класс

## 📈 Масштабирование

### Текущая архитектура:
- **Ограничение**: Один GameRoom = одна игра на всех игроков
- **Capacity**: ~10 игроков одновременно (ограничено одним циклом)

### Для масштабирования:
```kotlin
// Multiple game rooms
class GameLobby {
    private val rooms = ConcurrentHashMap<String, GameRoom>()
    
    fun createRoom(): String {
        val roomId = UUID.randomUUID().toString()
        rooms[roomId] = GameRoom()
        return roomId
    }
    
    fun joinRoom(roomId: String, player: PlayerConnection)
}

// Load balancing
class LoadBalancer {
    fun selectServer(): ServerAddress
    fun distributeLoad(rooms: List<GameRoom>)
}
```

## 🏁 Заключение

Архитектура проекта следует классическому client-server паттерну с авторитетным сервером:

- ✅ **Сервер** владеет истинным состоянием игры
- ✅ **Клиент** только отрисовывает и отправляет команды
- ✅ **Shared** обеспечивает согласованность типов
- ✅ **Модульность** позволяет легко расширять функционал

Этот дизайн обеспечивает:
- Защиту от читеров (логика на сервере)
- Простоту синхронизации (полное состояние каждый тик)
- Легкость добавления новых фич (четкое разделение ответственности)
