# traceweave

[![CircleCI](https://dl.circleci.com/status-badge/img/circleci/LjzPJXeYoL47FCi2q2LtR/D37Xb21s1vNzR8ttawDdZi/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/circleci/LjzPJXeYoL47FCi2q2LtR/D37Xb21s1vNzR8ttawDdZi/tree/master)
[![Maven Central](https://img.shields.io/maven-central/v/dev.one2.traceweave/runtime?label=Maven%20Central&logo=apachemaven&color=blue)](https://central.sonatype.com/namespace/dev.one2.traceweave)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/dev.one2.traceweave?label=Gradle%20Plugin%20Portal&logo=gradle)](https://plugins.gradle.org/plugin/dev.one2.traceweave)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Kotlin compiler IR plugin that reconstructs coroutine call chains in exception stack traces.

## The problem

Kotlin coroutines lose caller frames across suspension points. On resume, only the resuming
`invokeSuspend` is physically on the JVM stack, and the original call chain is gone.

## How it works

**Compile time.** The IR plugin walks every traced function and wraps each suspend call-site in a
`try/catch`. The call-site coordinates (`ClassName`, `methodName`, `fileName`, `lineNumber`) are
resolved at compile time and baked directly into the generated catch block.

**On exception.** The catch block calls `insertCoroutineFrame()` from the runtime module, which
inserts a synthetic `StackTraceElement` into the exception's stack trace at the right position,
then rethrows. A `WeakHashMap<Throwable, depth>` tracks how many frames have already been inserted
for each exception, so as it unwinds through nested traced call-sites each outer frame lands one
position deeper, preserving callee-to-caller order without any markers in the frames.
`CancellationException` is passed through untouched to not interfere with structured concurrency.

**Tail-call frames.** The `try/catch` is also a side-effect fix for Kotlin's tail-call optimization:
the compiler cannot eliminate a tail call that is inside a `try` block, so frames that would
otherwise be invisible reappear automatically.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `:runtime` | `dev.one2.traceweave:runtime` | `@TraceWeave` annotation + `insertCoroutineFrame()` |
| `:compiler` | `dev.one2.traceweave:compiler` | Kotlin IR compiler plugin |
| `:gradle-plugin` | `dev.one2.traceweave:gradle-plugin` | Gradle plugin (`dev.one2.traceweave`) |

## Usage

Add the dependency and apply the Gradle plugin:

```kotlin
// build.gradle.kts
plugins {
    id("dev.one2.traceweave")
}

dependencies {
    implementation("dev.one2.traceweave:runtime:<version>")
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


## Known limitations

**Duplicate frames in non-suspending call chains.** The runtime inserts frames based on a depth
counter without analyzing whether a gap in the trace actually exists. For coroutine functions that
never reach a real suspension point (no `delay`, no IO, etc.) the underlying JVM frames are already
on the stack when the exception is thrown, and the Kotlin state machine can also visit the same
catch block more than once. The result is that synthetic frames may appear alongside the real JVM
frames, duplicating some entries.

The best way to avoid this is to keep the traced scope narrow. Instead of tracing entire packages,
annotate only the functions where missing frames actually matter:

```kotlin
@TraceWeave
suspend fun importantService() { ... }    // traced
suspend fun fastNoSuspendHelper() { ... } // not traced, no duplicate risk
```

## Kotlin version compatibility

The plugin uses Kotlin's IR compiler API, which is not stable and can change between Kotlin releases.
It is currently tested and known to work with **Kotlin 2.3.0**. Other versions may or may not work.


## Relation to DeCoroutinator

[DeCoroutinator](https://github.com/Anamorphosee/stacktrace-decoroutinator) solves the same problem
and also supports compile-time transformation. The difference is scope: DeCoroutinator is a thorough
solution with multiple integration modes, full continuation-chain reconstruction, and global coverage.
traceweave is intentionally minimal: opt-in per class, no runtime machinery, does its job and stops.
If you need always-on full reconstruction, use DeCoroutinator.

## Why this exists

Debugging coroutines with a stack trace that stops at `invokeSuspend` gets old fast. DeCoroutinator
solves this well, but it works globally and reconstructs the full coroutine call chain everywhere.
I only needed readable traces for a specific handful of suspend functions in my codebase. Everything
else didn't matter.

So I built something that lets you point at exactly the functions you care about and leaves the rest
alone. That's it, really. If it's useful to you too, great 🙂

Maybe something like this already exists and I just didn't look hard enough 😂

## License

[MIT](LICENSE)
