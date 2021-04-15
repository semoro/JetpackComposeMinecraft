plugins {
    kotlin("jvm") version "1.4.21"
    id("fabric-loom") version "0.7.9"
    id("org.jetbrains.compose") version "0.3.0-build139"
//    id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "club.eridani"
version = "1.0.0"

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://maven.fabricmc.net/")
    maven("http://nexus.devsrsouza.com.br/repository/maven-public/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven(url = "https://dl.bintray.com/animeshz/maven")
}

dependencies {


    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    implementation(compose.desktop.currentOs)
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.github.animeshz:keyboard-kt:0.3.3")


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
