plugins {
    java
    jacoco
    application
    kotlin("jvm") version Versions.kotlin
    id("application")
    id("org.sonarqube") version "3.1.1"
    id("io.gitlab.arturbosch.detekt") version Versions.detekt
    id("org.jetbrains.dokka") version Versions.dokka
    id("io.wusa.semver-git-plugin") version "2.3.7"
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
            incrementer = "MINOR_INCREMENTER"
            formatter = org.gradle.api.Transformer<Any, io.wusa.Info> { info ->
                "${info.version.major}.${info.version.minor}.${info.version.patch}"
            }
        }
        branch {
            regex = "develop"
            incrementer = "PATCH_INCREMENTER"
            formatter = defaultVersionFormatter
        }
        branch {
            regex = ".+"
            incrementer = "NO_VERSION_INCREMENTER"
            formatter = defaultVersionFormatter
        }
    }
}

val sourceVersion = JavaVersion.VERSION_11
val targetVersion = JavaVersion.VERSION_11

kotlin.sourceSets["main"].kotlin.srcDirs("src")
kotlin.sourceSets["test"].kotlin.srcDirs("test")

sourceSets["main"].resources.srcDirs("resources")
sourceSets["test"].resources.srcDirs("testresources")

java {
    sourceCompatibility = sourceVersion
    targetCompatibility = targetVersion
}

tasks.compileJava {
    sourceCompatibility = sourceVersion.toString()
    targetCompatibility = targetVersion.toString()
}

tasks.compileKotlin {
    sourceCompatibility = sourceVersion.toString()
    targetCompatibility = targetVersion.toString()
    kotlinOptions {
        jvmTarget = targetVersion.toString()
    }
}

tasks.compileTestKotlin {
    sourceCompatibility = sourceVersion.toString()
    targetCompatibility = targetVersion.toString()
    kotlinOptions {
        jvmTarget = targetVersion.toString()
    }
}

tasks.detekt {
    config.from("detekt-config.yml")
    jvmTarget = targetVersion.toString()
}

tasks.test {
    useJUnitPlatform {
        if (!System.getenv("NOPERF").isNullOrBlank()) {
            excludeTags = setOf("PerfTest")
        } else {
            maxHeapSize = "4G"
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
    implementation("io.ktor:ktor-server-cio:${Versions.ktor}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor}")

    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("io.github.microutils:kotlin-logging:2.0.4")
    implementation("io.ktor:ktor-network-tls-certificates:${Versions.ktor}")
    implementation("io.ktor:ktor-auth:${Versions.ktor}")
    implementation("io.ktor:ktor-auth-jwt:${Versions.ktor}")
    implementation("io.ktor:ktor-jackson:${Versions.ktor}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:${Versions.jackson}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:${Versions.jackson}")
    implementation("org.rocksdb:rocksdbjni:${Versions.rocksDB}")
    implementation("com.google.guava:guava:${Versions.guava}")
    implementation("org.mapdb:mapdb:3.0.8")

    testImplementation("com.natpryce:hamkrest:1.8.0.1")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktor}") {
        exclude(group = "junit", module = "junit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.junit}")
    testImplementation("org.awaitility:awaitility:${Versions.awaitility}")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Versions.junit}")
}
