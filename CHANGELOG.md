# Changelog

All notable changes to traceweave are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1]

A maintenance release: moves the build to Kotlin 2.4.0 and refreshes dependencies, with no changes to
the runtime API or behavior. The compiler plugin still compiles and passes its tests on both Kotlin
2.3.0 and 2.4.0.

### Changed

- **Kotlin 2.3.0 → 2.4.0.** The `:compiler` plugin is now built against the Kotlin 2.4.0 IR compiler API
  while remaining compatible with 2.3.0.
- **Refreshed dependencies** — kotlinx-coroutines `1.11.0`, slf4j `2.0.18`, logback `1.5.34`,
  ktlint-gradle `14.2.0`, Gradle plugin-publish `2.1.0`.

### Added

- **`docs/ir-compiler.md`** — tracks the IR compiler API deprecations the plugin deliberately keeps using
  for cross-version (2.3.0/2.4.0) compatibility, and the policy for when a removal forces a migration and
  a minimum-version bump.

## [0.1.0]

The runtime grew from a single fixed behavior into a configurable, pluggable one — while staying inert
until you turn it on.

### Added

- **Modes.** The reconstruction behavior is now selectable:
  - `INPLACE` (default) — weaves the lost caller frames into the original exception, preserving its
    identity and type. Now conflict-tolerant: a frame already next to the insertion point (from
    traceweave, coroutine recovery, or `DebugProbes`) is skipped, so frames don't double up.
  - `COPY` — rethrows a fresh copy of the same type with the original chained as its `cause`, building a
    clean reconstructed trace (a labelled, configurable throw-site seed, a `--- @TraceWeave ---` marker,
    then the throw leaf and each caller). Seed depth is configurable via `copySeedFrames` (default 1).
  - `CUSTOM` — hand control to your own `ModeStrategy`.
- **Single entry point `TraceWeave`** — policy (`configure { }`), the `handle()` hot path, and the copier
  registry, all in one place. Inert until configured: `handle` passes exceptions through untouched until
  a policy is set.
- **Configuration.** `TraceWeave.configure { mode / strategy / reflectionCopy / copySeedFrames }`, plus a
  zero-config bootstrap from system properties (`traceweave.enabled`, `traceweave.mode`,
  `traceweave.copy.reflection`, `traceweave.copy.seedFrames`) for the Gradle path. Precedence:
  `configure { }` > system property > inert default.
- **Copier registry for COPY** — rebuild exception types with full type/field fidelity:
  - `TraceWeaveException` interface for types you own (zero-config copy contract).
  - `TraceWeave.register` / `unregister` for third-party types.
  - A `TraceWeaveCopierProvider` `ServiceLoader` SPI so libraries can ship copiers declaratively.
  - A built-in copier table for common Java/Kotlin exceptions, plus an opt-in reflection fallback.
- **Diagnostics.** Runtime logging through a static slf4j logger (`dev.one2.traceweave`, no-op without a
  binding); compile-time diagnostics ride Gradle log levels (`--info` summary, `--debug` per-function).
- **Gradle plugin** now auto-adds the matching `:runtime` and activates it for Gradle-launched JVMs
  (tests, `application` run), so the zero-config path works without an explicit `configure { }` call.
- **Docs & samples** — a detailed guide (`docs/detailed.md`) and a runnable `:samples` showcase that
  prints the same trace under each mode for side-by-side comparison.

### Changed

- Generated code now routes through the single `TraceWeave.handle(e, …)` call, making the compiler
  mode-agnostic: the mode can change (or traceweave be switched off) without recompiling.

## [0.0.1]

Initial release.

### Added

- Targeted, compile-time reconstruction of the coroutine caller frames lost at suspension points: a
  Kotlin/JVM IR compiler plugin wraps each selected suspend call-site and bakes in its source location,
  and the runtime stitches that frame back into the stack trace on the way out.
- Opt-in per function or class via the `@TraceWeave` annotation, or by package/class FQN prefixes — only
  the code you point at is instrumented, nothing else.
- A Gradle plugin (`dev.one2.traceweave`) that wires the compiler plugin into a Kotlin/JVM build.
- Published to Maven Central (`:runtime`, `:compiler`) and the Gradle Plugin Portal (`:gradle-plugin`).
- JVM-only (Kotlin/Native and Kotlin/JS are out of scope).
