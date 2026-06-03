plugins {
  id("trace-weave.conventions")
  id("trace-weave.publish")
}

val kotlinVersion: String by project

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
}
