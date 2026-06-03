plugins {
  id("trace-weave.conventions")
}

val kotlinVersion: String by project

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
}
