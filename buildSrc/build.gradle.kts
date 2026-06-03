plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("traceWeave") {
            id = "dev.one2.trace-weave"
            implementationClass = "dev.one2.traceweave.gradle.TraceWeavePlugin"
        }
    }
}

val kotlinVersion: String by project
val ktlintVersion: String by project

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:$ktlintVersion")
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.36.0")
}
