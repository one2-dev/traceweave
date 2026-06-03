plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name = project.name
        description = "Kotlin IR plugin that reconstructs coroutine call chains in exception stack traces"
        url = "https://github.com/one2-dev/trace-weave"
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
            connection = "scm:git:git://github.com/one2-dev/trace-weave.git"
            developerConnection = "scm:git:ssh://github.com/one2-dev/trace-weave.git"
            url = "https://github.com/one2-dev/trace-weave"
        }
    }
}
