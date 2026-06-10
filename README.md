# traceweave

[![CircleCI](https://dl.circleci.com/status-badge/img/circleci/LjzPJXeYoL47FCi2q2LtR/D37Xb21s1vNzR8ttawDdZi/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/circleci/LjzPJXeYoL47FCi2q2LtR/D37Xb21s1vNzR8ttawDdZi/tree/master)
[![Maven Central](https://img.shields.io/maven-central/v/dev.one2.traceweave/runtime?label=Maven%20Central&logo=apachemaven&color=blue)](https://central.sonatype.com/namespace/dev.one2.traceweave)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/dev.one2.traceweave?label=Gradle%20Plugin%20Portal&logo=gradle)](https://plugins.gradle.org/plugin/dev.one2.traceweave)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Readable coroutine stack traces, on your terms. A Kotlin compiler plugin + runtime that rebuilds the
caller chain lost at suspension points — only for the functions you point it at, not everywhere.

## The problem

Kotlin coroutines lose caller frames across suspension points. On resume, only the resuming
`invokeSuspend` is left on the JVM stack — the original call chain is gone.

## How it works

Three pieces — you only apply the Gradle plugin, which sets up the other two:

- **Gradle plugin** (`:gradle-plugin`) — the one thing you apply. It wires the compiler plugin into your
  Kotlin/JVM build, pulls in the matching `:runtime` for you, and switches the runtime on for
  Gradle-launched JVMs (tests, `application` run) — so it's ready to use without extra setup.
- **Compile time** (`:compiler`) — wraps each traced suspend call-site in a `try/catch` and bakes in its
  source location (class, method, file, line).
- **Runtime** (`:runtime`) — when an exception flies through that `catch`, `TraceWeave.handle` stitches
  the lost frame back into the stack trace and rethrows.

It stays out of the way: inert until you turn it on, best-effort (a failure never changes your
exception), and `CancellationException` passes straight through.

## Usage

Apply the Gradle plugin — it adds the matching runtime for you:

```kotlin
// build.gradle.kts
plugins {
    id("dev.one2.traceweave")
}
```

**Option 1 — via Gradle config** (no source changes needed):

```kotlin
traceWeave {
    enabled = true                                         // set to false to disable entirely
    classes = listOf("com.example.mypackage")              // FQN prefixes to trace
    excludeClasses = listOf("com.example.mypackage.Noisy") // exclusions win over inclusions
}
```

**Option 2 — via annotation** (per function or class):

```kotlin
@TraceWeave
suspend fun myFunction() { ... }

@TraceWeave
class MyService {
    suspend fun doWork() { ... } // traced because the class is annotated
}
```

That's enough to get readable traces. traceweave is **JVM-only** (Kotlin/Native and Kotlin/JS are out of
scope). For modes, copiers, configuration, limitations, and Kotlin version support, see the
**[detailed guide](docs/detailed.md)**.

## Relation to DeCoroutinator

[DeCoroutinator](https://github.com/Anamorphosee/stacktrace-decoroutinator) solves the same problem,
always-on and globally — full continuation-chain reconstruction everywhere. traceweave is the targeted
alternative: opt-in per function or class, nothing instrumented that you didn't ask for. Need full global
reconstruction? Use DeCoroutinator.

## Why this exists

I only needed readable traces for a specific handful of suspend functions — not everywhere. So I built
something that lets you point at exactly the functions you care about and leaves the rest alone:
targeted, opt-in, nothing instrumented that you didn't ask for. That's it, really. If it's useful to you
too, great 🙂

Maybe something like this already exists and I just didn't look hard enough 😂

## Contributing & roadmap

This is mostly driven by what I need, so updates land as my free time allows. There's no fixed roadmap.

If you want something, open an issue — I'm happy to leave the request open, and I'll get to it when I
can. PRs are welcome too, with two rules:

- they must come with tests, and
- they must not break the existing ones.

## License

[MIT](LICENSE)
