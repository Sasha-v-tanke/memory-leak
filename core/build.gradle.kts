plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.22"
}

val gdxVersion = "1.12.1"

dependencies {
    api(project(":shared"))
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-box2d:$gdxVersion")
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7") // Using CIO for desktop client
    implementation("io.ktor:ktor-client-websockets:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

}
