# traceweave — detailed guide

The README covers the basics. This document goes deeper: the modes, how COPY mode rebuilds an exception,
the full runtime configuration surface, known limitations, and Kotlin version compatibility.

traceweave reconstructs lost coroutine caller frames at runtime. The compiler plugin only generates the
plumbing; *how* a caught exception is turned into a readable stack trace is decided at runtime by the
active **mode**.

## Overview

For every traced suspend call-site the compiler plugin generates the same thing — a `try/catch` whose
catch block rethrows the result of a single runtime call:

```kotlin
catch (e: Throwable) { throw TraceWeave.handle(e, "com.x.C", "m", "C.kt", 42) }
```

The call-site coordinates (`ClassName`, `methodName`, `fileName`, `lineNumber`) are baked in at compile
time, because at a suspension point the real JVM stack is already truncated and they cannot be recovered
later. The compiler is **mode-agnostic**: it never bakes a mode into the bytecode, so the mode can be
changed (or traceweave switched off entirely) without recompiling.

`TraceWeave.handle` is **inert until configured**. Until the application calls `configure { … }` or a
`traceweave.*` system property activates it, `handle` returns the original exception untouched — no frame
insertion, no copy, no mutation. The whole call runs best-effort under a `try/catch`: if anything goes
wrong while weaving, the original exception is returned unchanged and the failure is logged, never
propagated. `CancellationException` (structured concurrency) and `VirtualMachineError` (don't allocate
while the VM is already failing) are always passed straight through.

## Modes

The active mode is one of three. `INPLACE` is the default.

### INPLACE

Prepends the synthetic caller frame directly onto the **original** exception's stack trace and rethrows
the same instance. The exception's type and identity are preserved, so `catch (e: MyException)` keeps
working everywhere. As the exception unwinds through nested traced call-sites, each outer caller is
inserted one position lower, preserving callee-to-caller order. A per-instance counter (a
weak-keyed, thread-safe map — coroutines hop threads) tracks the insertion position. Insertion is
conflict-tolerant: if an identical frame already sits next to the insertion point — whether put there by
traceweave, by coroutine recovery, or by `DebugProbes` — it is skipped, so frames don't double up.

### COPY

Writes the synthetic frame onto a **fresh copy of the same type** and rethrows the copy; the original is
left untouched and becomes the copy's `cause`. The copy's own stack trace is seeded with the real
throw-site (the top frames of the original, up to the first coroutine-machinery frame) so it is
self-sufficient, while the full original trace stays available through the cause chain. A sentinel marker
frame (`--- @TraceWeave ---`) is written into the copy; on the next unwind level traceweave recognises
the copy by that marker and simply appends the next frame, so a whole chain produces exactly one copy.
Suppressed exceptions are carried over to the copy. If no copy can be made (see resolution order below),
COPY falls back to returning the original untouched.

### CUSTOM

Hands control to a `ModeStrategy` you supply yourself (see *Runtime configuration* below). Use it when
neither built-in behavior fits — for example to defer to `DebugProbes` or kotlinx coroutine recovery on
your own terms.

### Mode matrix

|                       | INPLACE                         | COPY                                            |
|-----------------------|---------------------------------|-------------------------------------------------|
| Where frames go       | into the original               | into a copy (same type)                         |
| Original exception    | mutated, rethrown               | untouched, becomes the `cause`                  |
| Resulting trace       | real + synthetic interleaved    | throw-site seed + synthetic; full original in cause |
| Level tracking        | per-instance position counter   | marker frame in the trace (append-only)         |
| Global state          | the counter map                 | none                                            |
| If it can't proceed   | passes through (no frames)      | passes through (no frames; warns once per type) |
| Type identity         | always preserved                | always preserved (copy of the same class)       |
| Selected by           | `Mode.INPLACE` / `traceweave.mode=inplace` | `Mode.COPY` / `traceweave.mode=copy` |

## How COPY rebuilds an exception

To make a copy of the same type, COPY tries the following in order and uses the first that succeeds:

1. **`TraceWeaveException`** — if the exception implements this interface, traceweave calls
   `copyWithCause(original)` on it. This is the zero-config path for exception types you own: the type
   guarantees its own copy, no registration needed, and you copy any custom fields yourself.
2. **A registered copier** — a copier contributed for that exact type via `TraceWeave.register` or a
   `TraceWeaveCopierProvider` service. This is the path for third-party types you don't own.
3. **The built-in copier table** — hardcoded copiers for common JDK/Kotlin exceptions
   (`IllegalStateException`, `IOException`, `NumberFormatException`, and many more), with no reflection.
4. **Reflection** — an opt-in fallback that probes the type's constructors (`(String, Throwable)`,
   `(String)`, `(Throwable)`, `()`) to rebuild it. Off by default.
5. **Pass-through** — if none apply, the original exception is returned unchanged.

Suppressed exceptions are transferred to the copy automatically for the table and reflection paths; for
the interface and registered-copier paths, transferring suppressed exceptions and custom fields is the
copier's responsibility.

**Known limitation — custom fields.** The table and reflection paths recover only `message` and `cause`.
A rich exception type with custom fields (`errorCode`, `httpStatus`, …) loses those fields on those two
paths. To preserve them, implement `TraceWeaveException` on your own types, or register a copier for
third-party ones — both let you copy every field yourself.

## Runtime configuration

There are two independent configuration surfaces, split by ownership.

### Policy — `configure { }` (owned by the application)

The global policy: which mode runs, and whether reflection copying is allowed. This is
application-owned, intended to be set once at startup, and replaces any previous policy (last call wins).
A second call logs a soft warning rather than failing, since tests legitimately reconfigure.

Every option of the builder, with its default:

```kotlin
TraceWeave.configure {
    mode = Mode.INPLACE         // INPLACE (default) | COPY | CUSTOM
    strategy = null             // a custom ModeStrategy; when set, overrides `mode` (implies CUSTOM)
    reflectionCopy = false      // allow COPY's reflection fallback. Default: false
}
```

A minimal call just picks a mode:

```kotlin
TraceWeave.configure {
    mode = Mode.COPY
    reflectionCopy = true       // allow COPY's reflection fallback; off by default
}
```

To take full control, supply your own `ModeStrategy`. Setting a `strategy` overrides `mode` entirely
(equivalent to `Mode.CUSTOM`):

```kotlin
TraceWeave.configure {
    strategy = object : ModeStrategy {
        override fun weave(
            error: Throwable,
            declaringClass: String,
            methodName: String,
            fileName: String,
            lineNumber: Int,
        ): Throwable {
            // do whatever you like with the exception, then return what to rethrow
            return error
        }
    }
}
```

The built-in `InplaceMode` and `CopyMode` are themselves `ModeStrategy` implementations, so a custom
strategy is a first-class peer, not a second-class hook.

### Copier registry — `register` / service providers (owned by anyone)

A separate, additive surface for contributing copiers for specific exception types. It is safe to call
from anywhere, including libraries: it never replaces policy and never activates traceweave — a
contributed copier stays inert until the application selects COPY mode.

```kotlin
TraceWeave.register<MyDomainException> { original ->
    MyDomainException(original.message, original)
}

TraceWeave.unregister<MyDomainException>()   // drop it again
```

A library can ship copiers for its own types declaratively, with no code in the consuming application, by
implementing `TraceWeaveCopierProvider` and registering it under
`META-INF/services/dev.one2.traceweave.copier.TraceWeaveCopierProvider`. Use `TraceWeave.entry` to build
the provider's map entries in a type-safe way. An explicit `TraceWeave.register` call from the
application always overrides a provider's copier for the same type; if two providers contribute a copier
for the same type, the first discovered wins and the duplicate is logged.

### System properties

For the zero-config Gradle path, policy can come from system properties instead of code. The full set:

```properties
traceweave.enabled=true          # activation gate; absent or false -> inert. Default: false
traceweave.mode=inplace          # inplace | copy. Default: inplace
traceweave.copy.reflection=false # allow COPY's reflection fallback. Default: false
```

- `traceweave.enabled` — the activation gate. The Gradle plugin sets this for Gradle-launched JVMs (tests
  and `application` runs); absent or `false` means traceweave stays inert.
- `traceweave.mode` — `inplace` or `copy`. `CUSTOM` cannot be selected this way: a strategy can only be
  supplied in code.
- `traceweave.copy.reflection` — `true` to allow COPY's reflection fallback.

Pass them however you launch the JVM, e.g. `-Dtraceweave.mode=copy`.

Precedence: `configure { }` > system property > inert default. An explicit `configure` call in code
always wins over properties.

### Diagnostics

The runtime logs through a static slf4j logger named `dev.one2.traceweave`. It is a no-op unless your
application has an slf4j binding on the classpath, it is not configurable, and it is not part of policy —
if you want traceweave's diagnostics, add an slf4j binding. The hot path stays silent on success and only
logs rare, suspicious events (a swallowed best-effort failure, an unsupported copy, a duplicate copier
provider).

Compile-time diagnostics ride Gradle's own log levels rather than a custom flag: a per-module summary is
reported at `--info` and per-function detail at `--debug`. With the default log level the plugin is
silent.

## Known limitations

**Duplicate frames in non-suspending chains.** A coroutine function that never actually suspends (no
`delay`, no IO) still has its real JVM frames on the stack, so a synthetic frame can land next to a real
one — this is an INPLACE concern, since INPLACE inserts into the existing trace.

INPLACE *minimizes* duplicates rather than guaranteeing none: each call-site is wrapped only once at
compile time, and INPLACE skips a frame that already sits next to the insertion point. To narrow the
window further, keep the traced scope tight — annotate the functions where missing frames actually
matter, not whole packages:

```kotlin
@TraceWeave
suspend fun importantService() { ... }    // traced
suspend fun fastNoSuspendHelper() { ... } // not traced, no duplicate risk
```

To *eliminate* duplicates entirely, use **COPY** mode. COPY never mutates the original trace; it builds a
fresh copy whose stack starts from the throw-site seed plus traceweave's own frames, so the real JVM
frames can't interleave with synthetic ones. The full original trace stays available through the copy's
`cause`. The trade-off is that the rethrown exception is a copy (same type, original chained as cause)
rather than the same instance — see [Modes](#modes).

## Kotlin version compatibility

The plugin uses Kotlin's IR compiler API, which is not stable and can change between Kotlin releases. It
is currently tested and known to work with **Kotlin 2.3.0**. Other versions may or may not work.

## Why this matters

kotlinx coroutine recovery is "partial" precisely because it only knows how to make a reflective copy and
gives up on everything else. COPY mode adds the registered-copier and owned-interface layers, so for the
types you actually care about, reconstruction is complete and type-preserving. INPLACE is the simple
default with a guaranteed identity. That is the trade traceweave offers against recovery — "not partial
when you need it" — while staying opt-in and lightweight compared to always-on, global reconstruction.

**JVM-only.** traceweave targets the JVM, on par with the strongest comparable tool: DeCoroutinator is
JVM/Android only as well, since the problem itself — frames lost on the JVM stack at a suspension point —
is inherently a JVM concern. Kotlin/Native and Kotlin/JS are out of scope.
