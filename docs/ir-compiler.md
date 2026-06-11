# IR compiler API compatibility & deprecations

The `:compiler` module is a Kotlin IR compiler plugin. It builds against Kotlin's **IR compiler API**,
which JetBrains explicitly treats as *unstable* — signatures can be deprecated and later removed between
Kotlin releases. This document tracks the deprecated API we currently rely on, so a future Kotlin bump
that *removes* one of these doesn't catch us by surprise.

## Compatibility policy

- traceweave aims to compile and run on a **range** of recent Kotlin versions (currently **2.3.0 and
  2.4.0**), not just the newest one.
- A deprecation warning is **not** a reason to migrate immediately. We keep using a deprecated API as
  long as it still works on every Kotlin version we support — even if a newer replacement exists — to
  avoid splitting the source by version.
- We migrate **only when forced**: i.e. when the deprecated API is actually *removed* from a Kotlin
  version we want to support. At that point we migrate to the replacement and, if the replacement is not
  available on the older versions, **raise the minimum supported Kotlin version** and note it in the
  README badge and `docs/detailed.md`.
- The trade is deliberate: a few compile-time warnings now, in exchange for one source tree that works
  across the supported range.

## Why we don't just adopt the replacement

The suggested replacement (`finderForBuiltins()` / `finderForSource(fromFile)`) was introduced in a
newer Kotlin than our floor: on **2.3.0** the calls below are **not** deprecated and the finder API may
not be present. Switching to it would break the 2.3.0 build. So the deprecated form is precisely what
keeps a single source tree compiling on both 2.3.0 and 2.4.0.

## Currently-tracked deprecations

All in `compiler/src/main/kotlin/dev/one2/traceweave/compiler/TraceWeaveIrExtension.kt`, inside
`generate()`:

| API | Location | Deprecated since | Used for | Suggested replacement |
|---|---|---|---|---|
| `IrPluginContext.referenceFunctions(CallableId)` | `TraceWeaveIrExtension.kt` ~L106 | Kotlin 2.4.0 | Resolve the `TraceWeave.handle` runtime function symbol that traced call-sites are wrapped with | `finderForBuiltins()` / `finderForSource(fromFile)` |
| `IrPluginContext.referenceClass(ClassId)` | `TraceWeaveIrExtension.kt` ~L109 | Kotlin 2.4.0 | Resolve the `TraceWeave` singleton object (dispatch receiver for the `handle()` call) | `finderForBuiltins()` / `finderForSource(fromFile)` |

Both still compile and pass `:compiler:test` on 2.3.0 and 2.4.0 as of this writing — they only emit
`w: ... is deprecated` warnings on 2.4.0.

## When one of these is removed

1. `:compiler:test` (or `:compiler:compileKotlin`) will fail to compile against the new Kotlin, not just
   warn.
2. Migrate the affected call to the finder API (`finderForBuiltins()` for the runtime symbols).
3. If the finder API isn't available on the current floor (2.3.0), bump the **minimum supported Kotlin
   version**, and update the Kotlin badge in `README.md` and the compatibility note in
   `docs/detailed.md`.
4. Add a row to the table above if new deprecations appear, or strike removed ones.
