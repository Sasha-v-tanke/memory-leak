plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.7"
    kotlin("plugin.serialization") version "1.9.22"
}

val ktor_version = "2.3.7"
val logback_version = "1.4.14"
val exposed_version = "0.45.0"
val hikari_version = "5.1.0"
val postgres_version = "42.7.1"

dependencies {
    api(project(":shared"))
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    
    // PostgreSQL & Database
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
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
