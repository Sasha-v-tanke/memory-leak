package com.memoryleak.server.game

import com.memoryleak.shared.model.*
import java.util.UUID

object DeckBuilder {
    // The permanent factory card that's always in hand
    private val permanentFactoryCards = mutableMapOf<String, Card>()  // playerId -> factory card
    
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
        
        // No factory cards in main deck - they go to permanent hand slot
        
        deck.shuffle()
        return deck
    }
    
    fun createDeckFromSelection(selectedTypes: List<String>): MutableList<Card> {
        val deck = mutableListOf<Card>()
        
        // Add selected cards (up to 10, duplicates allowed) - NO factory cards in deck
        selectedTypes.filter { !it.startsWith("BUILD_") }.take(10).forEach { typeName ->
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
    
    /**
     * Create and store the permanent factory card for a player.
     * This card is always in hand slot 0 and never consumed.
     */
    fun initializePermanentFactoryCard(player: PlayerState) {
        val factoryCard = Card(
            id = "FACTORY_${player.id}",
            type = CardType.BUILD_FACTORY,
            memoryCost = 100,
            cpuCost = 0,
            productionTime = 0f
        )
        permanentFactoryCards[player.id] = factoryCard
        
        // Add factory card to hand (slot 0)
        player.hand.add(0, factoryCard)
    }
    
    /**
     * Draw a card from deck.
     * @param keepFactoryCard if true, factory card was just played and should be returned to hand
     */
    fun drawCard(player: PlayerState) {
        // Ensure factory card is always in hand (slot 0)
        val factoryCard = permanentFactoryCards[player.id]
        if (factoryCard != null && !player.hand.any { it.type.isFactoryCard() }) {
            player.hand.add(0, factoryCard)
        }
        
        // Count non-factory cards in hand
        val nonFactoryCards = player.hand.count { !it.type.isFactoryCard() }
        if (nonFactoryCards >= 4) return // Hand is full (4 regular + 1 factory = 5)
        
        // Clean up any factory cards that somehow got into deck (safety)
        player.deck.removeAll { it.type.isFactoryCard() }
        
        if (player.deck.isEmpty()) {
            // Reshuffle discard pile (excluding factory cards)
            val nonFactoryDiscard = player.discardPile.filter { !it.type.isFactoryCard() }
            if (nonFactoryDiscard.isEmpty()) return // No cards left
            player.deck.addAll(nonFactoryDiscard)
            player.discardPile.removeAll { it.type.isFactoryCard() || it in nonFactoryDiscard.toSet() }
            player.deck.shuffle()
        }
        
        if (player.deck.isEmpty()) return
        
        // Draw the first card (now guaranteed to be non-factory)
        val cardToDraw = player.deck.removeAt(0)
        player.hand.add(cardToDraw)
    }
    
    fun playCard(player: PlayerState, cardId: String): Card? {
        val card = player.hand.find { it.id == cardId } ?: return null
        player.hand.remove(card)
        
        // Factory cards don't go to discard - they stay available
        if (!card.type.isFactoryCard()) {
            player.discardPile.add(card)
        }
        
        return card
    }
}
