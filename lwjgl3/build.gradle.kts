plugins {
    kotlin("jvm")
    java
}

val gdxVersion = "1.12.1"

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-desktop")
}

tasks.withType<JavaExec> {
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Builds a Fat JAR with all dependencies."
    archiveBaseName.set("memory-leak-client")
    archiveVersion.set("1.0.0")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    dependsOn(configurations.runtimeClasspath)
    
    manifest {
        attributes["Main-Class"] = "com.memoryleak.desktop.DesktopLauncherKt"
    }
    
    from(sourceSets.main.get().output)
    
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the desktop application."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.memoryleak.desktop.DesktopLauncherKt")
    workingDir = file("../assets")
}
