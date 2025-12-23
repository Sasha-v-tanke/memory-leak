package com.memoryleak.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.memoryleak.core.MemoryLeakApp

fun main() {
    println("--- LAUNCHING MEMORY LEAK CLIENT ---")
    val config = Lwjgl3ApplicationConfiguration()
    config.setForegroundFPS(60)
    config.setTitle("Memory Leak - Programming Battle Arena")
    config.setWindowedMode(800, 600)
    Lwjgl3Application(MemoryLeakApp(), config)
}
