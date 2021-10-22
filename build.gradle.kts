plugins {
    kotlin("jvm") version "1.5.31"
    id("fabric-loom") version "0.7.9"
    id("org.jetbrains.compose") version "1.0.0-alpha4-build418"
//    id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "club.eridani"
version = "1.0.0"

repositories {
    mavenCentral()
    maven(url = "https://maven.fabricmc.net/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {


//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    implementation(compose.desktop.currentOs)
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))


    minecraft("com.mojang:minecraft:1.16.5")
    mappings("net.fabricmc:yarn:1.16.5+build.5")
    modImplementation("net.fabricmc:fabric-loader:0.11.2")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.31.0+1.16")
}


val jar by tasks.getting(Jar::class) {
    exclude("**/*.RSA")
    exclude("**/*.SF")
    exclude("LICENSE.txt")
    exclude("**/module-info.class")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}