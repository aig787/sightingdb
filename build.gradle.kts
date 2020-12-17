plugins {
    java
    jacoco
    application
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.serialization") version Versions.kotlin
    id("application")
    id("org.sonarqube") version "3.0"
    id("io.gitlab.arturbosch.detekt") version "1.12.0"
    id("org.jetbrains.dokka") version Versions.kotlin
    id("io.wusa.semver-git-plugin") version "2.3.0"
    id("com.adarshr.test-logger") version "2.1.1"
}

repositories {
    mavenLocal()
    jcenter()
}

group = "com.devo.sightingdb"
version = semver.info

application {
    mainClass.set("com.devo.sightingdb.ApplicationKt")
}

val defaultVersionFormatter = org.gradle.api.Transformer<Any, io.wusa.Info> { info ->
    "${info.version.major}.${info.version.minor}.${info.version.patch}+build.${info.count}.sha.${info.shortCommit}"
}

semver {
    initialVersion = "0.0.0"
    branches {
        branch {
            regex = "master"
            incrementer = "MINOR_VERSION_INCREMENTER"
            formatter = org.gradle.api.Transformer<Any, io.wusa.Info> { info ->
                "${info.version.major}.${info.version.minor}.${info.version.patch}"
            }
        }
        branch {
            regex = "develop"
            incrementer = "PATCH_VERSION_INCREMENTER"
            formatter = defaultVersionFormatter
        }
        branch {
            regex = ".+"
            incrementer = "NO_VERSION_INCREMENTER"
            formatter = defaultVersionFormatter
        }
    }
}

val javaVersion = JavaVersion.VERSION_1_8.toString()

kotlin.sourceSets["main"].kotlin.srcDirs("src")
kotlin.sourceSets["test"].kotlin.srcDirs("test")

sourceSets["main"].resources.srcDirs("resources")
sourceSets["test"].resources.srcDirs("testresources")

tasks.compileKotlin {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    kotlinOptions {
        jvmTarget = javaVersion
    }
}

tasks.compileTestKotlin {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    kotlinOptions {
        jvmTarget = javaVersion
    }
}

tasks.detekt {
    jvmTarget = javaVersion
}

tasks.test {
    useJUnitPlatform {
        if (!System.getenv("NOPERF").isNullOrBlank()) {
            excludeTags = setOf("PerfTest")
        } else {
            maxHeapSize = "2G"
        }
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.startScripts {
    doLast {
        val contents = unixScript.readText()
        val classpath = contents.split("\n").find { it.startsWith("CLASSPATH") }!!
        val updatedClasspath = classpath.replace("CLASSPATH=", "CLASSPATH=\$CLASSPATH:")
        unixScript.writeText(contents.replace(classpath, updatedClasspath))
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.0.1")
    implementation("io.ktor:ktor-server-cio:${Versions.ktor}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor}")

    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("io.github.microutils:kotlin-logging:1.8.3")
    implementation("io.ktor:ktor-network-tls-certificates:${Versions.ktor}")
    implementation("io.ktor:ktor-client-core:${Versions.ktor}")
    implementation("io.ktor:ktor-client-core-jvm:${Versions.ktor}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktor}")
    implementation("io.ktor:ktor-serialization:${Versions.ktor}")
    implementation("org.rocksdb:rocksdbjni:${Versions.rocksDB}")
    implementation("com.google.guava:guava:${Versions.guava}")
    implementation("org.mapdb:mapdb:3.0.8")

    testImplementation("com.natpryce:hamkrest:1.7.0.3")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktor}") {
        exclude(group = "junit", module = "junit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.junit}")
    testImplementation("org.awaitility:awaitility:${Versions.awaitility}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Versions.junit}")
}
