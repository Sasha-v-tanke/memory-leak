plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.7"
    kotlin("plugin.serialization") version "1.9.22"
}

val ktor_version = "2.3.7"
val logback_version = "1.4.14"

dependencies {
    api(project(":shared"))
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
}

application {
    mainClass.set("com.memoryleak.server.ApplicationKt")
}

tasks.register<Jar>("fatJar") {
    group = "build"
    archiveBaseName.set("memory-leak-server")
    archiveVersion.set("1.0.0")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    dependsOn(configurations.runtimeClasspath)
    
    manifest {
        attributes["Main-Class"] = "com.memoryleak.server.ApplicationKt"
    }
    
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
