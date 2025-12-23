package com.memoryleak.server.game

import com.memoryleak.shared.model.*
import java.util.UUID

object DeckBuilder {
    fun createDefaultDeck(): MutableList<Card> {
        val deck = mutableListOf<Card>()
        
        // === LEGACY UNITS (for balance) ===
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_SCOUT, 30, 20, productionTime = 2f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_TANK, 80, 40, productionTime = 5f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_RANGED, 50, 60, productionTime = 3f))
        
        // === BASIC PROCESSES ===
        repeat(2) { deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_ALLOCATOR, 40, 30, productionTime = 2f)) }
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_GARBAGE_COLLECTOR, 50, 40, productionTime = 3f))
        repeat(2) { deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_BASIC_PROCESS, 35, 25, productionTime = 2f)) }
        
        // === OOP UNITS ===
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_INHERITANCE_DRONE, 60, 45, productionTime = 3f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_POLYMORPH_WARRIOR, 85, 60, productionTime = 4f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_ENCAPSULATION_SHIELD, 100, 50, productionTime = 5f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_ABSTRACTION_AGENT, 45, 35, productionTime = 3f))
        
        // === REFLECTION & METAPROGRAMMING ===
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_REFLECTION_SPY, 30, 25, productionTime = 2f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_CODE_INJECTOR, 70, 55, productionTime = 4f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_DYNAMIC_DISPATCHER, 65, 50, productionTime = 3f))
        
        // === ASYNC & PARALLELISM ===
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_COROUTINE_ARCHER, 75, 65, productionTime = 4f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_PROMISE_KNIGHT, 90, 70, productionTime = 5f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_DEADLOCK_TRAP, 40, 40, productionTime = 2f))
        
        // === FUNCTIONAL PROGRAMMING ===
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_LAMBDA_SNIPER, 100, 80, productionTime = 6f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_RECURSIVE_BOMB, 55, 50, productionTime = 3f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_HIGHER_ORDER_COMMANDER, 80, 65, productionTime = 5f))
        
        // === NETWORK & COMMUNICATION ===
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_API_GATEWAY, 70, 55, productionTime = 4f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_WEBSOCKET_SCOUT, 35, 30, productionTime = 2f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_RESTFUL_HEALER, 70, 60, productionTime = 4f))
        
        // === STORAGE UNITS ===
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_CACHE_RUNNER, 25, 20, productionTime = 1f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_INDEXER, 50, 45, productionTime = 3f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_TRANSACTION_GUARD, 75, 60, productionTime = 4f))
        
        // === BUILDING (always available) ===
        deck.add(Card(UUID.randomUUID().toString(), CardType.BUILD_FACTORY, 100, 0, productionTime = 0f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.BUILD_COMPILER_FACTORY, 150, 50, productionTime = 0f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.BUILD_INTERPRETER_FACTORY, 80, 30, productionTime = 0f))
        
        deck.shuffle()
        return deck
    }
    
    fun createDeckFromSelection(selectedTypes: List<String>): MutableList<Card> {
        val deck = mutableListOf<Card>()
        
        // Always add factory cards
        deck.add(Card(UUID.randomUUID().toString(), CardType.BUILD_FACTORY, 100, 0, productionTime = 0f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.BUILD_COMPILER_FACTORY, 150, 50, productionTime = 0f))
        deck.add(Card(UUID.randomUUID().toString(), CardType.BUILD_INTERPRETER_FACTORY, 80, 30, productionTime = 0f))
        
        // Add selected cards (up to 10, duplicates allowed)
        selectedTypes.take(10).forEach { typeName ->
            try {
                val cardType = CardType.valueOf(typeName)
                val definition = UnitStatsData.getCardDefinition(cardType)
                if (definition != null) {
                    repeat(2) {  // Add 2 copies of each selected card
                        deck.add(Card(
                            UUID.randomUUID().toString(),
                            cardType,
                            definition.memoryCost,
                            definition.cpuCost,
                            productionTime = definition.productionTime
                        ))
                    }
                }
            } catch (e: Exception) {
                // Skip invalid card types
            }
        }
        
        // Fill with basic cards if deck is too small
        while (deck.size < 20) {
            deck.add(Card(UUID.randomUUID().toString(), CardType.SPAWN_BASIC_PROCESS, 35, 25, productionTime = 2f))
        }
        
        deck.shuffle()
        return deck
    }
    
    fun drawCard(player: PlayerState) {
        if (player.hand.size >= 4) return // Hand is full
        
        if (player.deck.isEmpty()) {
            // Reshuffle discard pile
            if (player.discardPile.isEmpty()) return // No cards left
            player.deck.addAll(player.discardPile)
            player.discardPile.clear()
            player.deck.shuffle()
        }
        
        val card = player.deck.removeAt(0)
        player.hand.add(card)
    }
    
    fun playCard(player: PlayerState, cardId: String): Card? {
        val card = player.hand.find { it.id == cardId } ?: return null
        player.hand.remove(card)
        player.discardPile.add(card)
        return card
    }
}
