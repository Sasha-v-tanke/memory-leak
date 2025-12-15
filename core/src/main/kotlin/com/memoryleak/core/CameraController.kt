package com.memoryleak.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.OrthographicCamera

object CameraController {
    private const val CAMERA_SPEED = 300f
    
    fun handleMovement(camera: OrthographicCamera, deltaTime: Float) {
        var moved = false
        
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.position.y += CAMERA_SPEED * deltaTime
            moved = true
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.position.y -= CAMERA_SPEED * deltaTime
            moved = true
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.position.x -= CAMERA_SPEED * deltaTime
            moved = true
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.position.x += CAMERA_SPEED * deltaTime
            moved = true
        }
        
        // Clamp camera to reasonable bounds
        camera.position.x = camera.position.x.coerceIn(100f, 1200f)
        camera.position.y = camera.position.y.coerceIn(100f, 700f)
        
        if (moved) {
            camera.update()
        }
    }
}
