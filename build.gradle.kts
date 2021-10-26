plugins {
    kotlin("jvm") version "1.5.31"
    id("fabric-loom") version "0.10.43"
    id("org.jetbrains.compose") version "1.0.0-alpha4-build411"
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


    minecraft("com.mojang:minecraft:1.17.1")
    mappings("net.fabricmc:yarn:1.17.1+build.63")
    modImplementation("net.fabricmc:fabric-loader:0.12.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.41.0+1.17")
}


val jar by tasks.getting(Jar::class) {
    exclude("**/*.RSA")
    exclude("**/*.SF")
    exclude("LICENSE.txt")
    exclude("**/module-info.class")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn", "-Xlambdas=indy")
}