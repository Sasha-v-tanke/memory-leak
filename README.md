# Memory Leak - Programming Battle Arena

A real-time strategy game where programming concepts come to life! Build factories, spawn units inspired by programming paradigms, and destroy your opponent's base.

## 🎮 Game Overview

Memory Leak is a card-based RTS where players battle using units themed around programming concepts:
- **OOP Units**: Inheritance, Polymorphism, Encapsulation, Abstraction
- **Functional Units**: Lambda, Recursion, Higher-Order Functions
- **Async Units**: Coroutines, Promises, Deadlocks
- **Network Units**: API Gateway, WebSocket, REST
- **And many more!**

## 📁 Project Structure

```
memory-leak/
├── core/           # Client game logic (LibGDX)
│   └── ui/         # Login, Menu, Game, Deck Selection screens
│   └── network/    # Network client for server communication
├── server/         # Ktor WebSocket server
│   └── game/       # Game room, matchmaking, deck builder
│   └── database/   # PostgreSQL persistence (optional)
├── shared/         # Common models and network packets
├── lwjgl3/         # Desktop launcher
└── docs/           # Documentation
    ├── SERVER_API.txt       # Server API documentation
    ├── UNIT_DESCRIPTIONS.txt # Unit roles and abilities
    └── DATABASE_SCHEMA.txt  # Database schema
```

## 🚀 Prerequisites

1. **Java Development Kit (JDK) 11 or higher**
2. **IntelliJ IDEA** (Recommended) OR **Android Studio** OR **Gradle**

## 🎯 How to Run

### Command Line (Recommended)

1. **Start the Server**:
   ```bash
   ./gradlew :server:run
   ```

2. **Start the Client** (in a new terminal):
   ```bash
   ./gradlew :lwjgl3:run
   ```

3. **Start a Second Client** (for multiplayer):
   ```bash
   ./gradlew :lwjgl3:run
   ```

### IDE (IntelliJ IDEA / Android Studio)

1. Open the project folder
2. Wait for Gradle sync
3. Run `server/src/main/kotlin/com/memoryleak/server/Application.kt`
4. Run `lwjgl3/src/main/kotlin/com/memoryleak/desktop/DesktopLauncher.kt`

**MacOS Note**: Add `-XstartOnFirstThread` to VM options if running client from IDE.

## 🎮 Gameplay

### Game Flow
1. **Login**: Enter username to connect
2. **Main Menu**: View stats, select deck, find match
3. **Deck Selection**: Choose 10 cards from available units
4. **Battle**: Deploy units, capture resources, destroy enemy base!
5. **Game Over**: Return to menu or rematch

### Controls
- **Click card** → **Click map** to deploy unit
- **Click unit** → **Click ground** to move
- **WASD / Arrow Keys**: Move camera
- **ESC**: Cancel card placement

### Victory Condition
Destroy the enemy's **BASE** to win!

## 🏭 Factory System

- **Standard Factory**: Balanced unit production
- **Compiler Factory**: Slower but +10% unit stats
- **Interpreter Factory**: Faster but -15% unit stats
- **Inheritance Factory**: Combine units for upgrades

Factories have production queues - units spawn at the factory with shortest queue time.

## 🃏 Card Categories

| Category | Units | Theme |
|----------|-------|-------|
| Basic | Scout, Tank, Ranged, Healer | Classic RTS units |
| Process | Allocator, GC, Basic Process | Memory management |
| OOP | Inheritance, Polymorphism, Encapsulation, Abstraction | Object-oriented concepts |
| Reflection | Spy, Injector, Dispatcher | Metaprogramming |
| Async | Coroutine, Promise, Deadlock | Concurrency |
| Functional | Lambda, Recursive, Higher-Order | FP patterns |
| Network | API Gateway, WebSocket, REST | Web technologies |
| Storage | Cache, Indexer, Transaction | Data systems |
| Memory | Pointer, Buffer | Low-level concepts |
| Safety | Assert, Static Cast, Dynamic Cast | Type safety |
| Concurrency | Mutex, Semaphore, Thread Pool | Threading |

## 📊 Player Statistics

The game tracks:
- Total games, wins, losses
- Units created/killed
- Factories built
- Cards played
- Total play time

## 🗄️ Database (Optional)

Enable PostgreSQL persistence with environment variables:
```bash
DB_ENABLED=true
DB_HOST=localhost
DB_PORT=5432
DB_NAME=memoryleak
DB_USER=postgres
DB_PASSWORD=postgres
```

See `docs/DATABASE_SCHEMA.txt` for full schema.

## 📖 Documentation

- **Server API**: `docs/SERVER_API.txt` - WebSocket protocol
- **Unit Guide**: `docs/UNIT_DESCRIPTIONS.txt` - Unit abilities and roles
- **Database**: `docs/DATABASE_SCHEMA.txt` - Persistence schema

## 🔧 Troubleshooting

| Issue | Solution |
|-------|----------|
| `gradlew: no such file` | Run `gradle wrapper` first |
| Exit code 1 (macOS) | Add `-XstartOnFirstThread` to VM options |
| Can't connect | Ensure server is running on port 8080 |
| Build fails | Run `./gradlew clean` then rebuild |

## 🏗️ Tech Stack

- **Client**: LibGDX (Kotlin)
- **Server**: Ktor (Kotlin)
- **Database**: PostgreSQL + Exposed ORM
- **Network**: WebSocket with JSON serialization
