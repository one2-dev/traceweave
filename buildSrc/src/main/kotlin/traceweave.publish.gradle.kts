plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()

    // Sign only when a signing key is actually available (the Central publish job provides one).
    // This keeps unsigned local/CI-build publishing — e.g. to the test-repo consumed by the
    // gradle-plugin functional tests — working without a GPG key configured.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
    ) {
        signAllPublications()
    }

    pom {
        name = project.name
        description = "Kotlin IR plugin that reconstructs coroutine call chains in exception stack traces"
        url = "https://github.com/one2-dev/traceweave"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "VladDrake"
                name = "Vladyslav Horshkov"
                email = "s1egdrake@gmail.com"
            }
        }
        scm {
            connection = "scm:git:git://github.com/one2-dev/traceweave.git"
            developerConnection = "scm:git:ssh://github.com/one2-dev/traceweave.git"
            url = "https://github.com/one2-dev/traceweave"
        }
    }
}
