import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group="me.joypri"
version="0.0.1"

plugins {
    // For use as subproject, got the following error message
    // request for plugin already on the classpath must not include a version
    kotlin("jvm") // version "1.5.31"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("script-runtime"))
    implementation("org.reflections:reflections:0.9.12")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
}

tasks.test {
	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
	}
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}
