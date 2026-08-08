plugins {
    kotlin("jvm") version "1.9.23"
    `maven-publish`
    `java-library`
}

group   = "com.github.amarwave"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // WebSocket + HTTP client
    api("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON (Android-safe — no Gson dependency needed for core)
    // org.json is bundled in Android; for JVM we add it explicitly
    implementation("org.json:json:20240303")

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(8)
}

tasks.test {
    useJUnit()
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            groupId    = "com.github.amarwave"
            artifactId = "amarwave-kotlin"
            version    = project.version.toString()

            pom {
                name.set("AmarWave Kotlin SDK")
                description.set("Real-time WebSocket client for AmarWave — Kotlin/Android SDK")
                url.set("https://github.com/amarwave/amarwace-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("fnnaeem1881")
                        name.set("Mehedi Hasan")
                        email.set("mehedinaeem66@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/amarwave/amarwace-kotlin.git")
                    url.set("https://github.com/amarwave/amarwace-kotlin")
                }
            }
        }
    }
}
