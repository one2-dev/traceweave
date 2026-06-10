# samples

A runnable showcase of what traceweave does to a coroutine stack trace. Just run `main` and read the
output — each scenario throws through the same suspend call chain (`main → loadUser → fetchFromDb`, which
suspends on `delay`) and prints the resulting stack trace, so you can compare them side by side.

```bash
./gradlew :samples:run
```

This module wires the local `:compiler` and `:runtime` directly (no Gradle plugin, no publishing) and
traces the `dev.one2.traceweave.samples` package — so it always exercises the code in this repo.

## Scenarios

The `main` loop reconfigures traceweave before each run and prints the trace:

- **UNCONFIGURED** — traceweave is left inert, so this is the baseline: what the trace looks like when
  traceweave is not active (not applied, or before `TraceWeave.configure { }` is called). The caller
  chain lost at the suspension point is simply gone; you only see `fetchFromDb`'s `invokeSuspend` plus
  the coroutine machinery.
- **INPLACE** — the caller frames are woven back into the **original** exception: you get
  `fetchFromDb → loadUser → main` on the same instance.
- **COPY** — a fresh copy of the same type is rethrown (the original is kept as its `cause`). The trace
  reads as a clean reconstruction: a labelled seed (`--- @TraceWeave ---.(by cause seed)`), then the
  reconstruction marker (`--- @TraceWeave ---.(weaved)`), then the throw leaf and each caller.
- **COPY (no seed)** — the same, but with `copySeedFrames = 0`, so the copy's trace starts right at the
  marker.
- **CUSTOM** — a user-supplied `ModeStrategy`, to show the hook is entirely under your control.

For the full story on modes, the copier registry, configuration and limitations, see the
[detailed guide](../docs/detailed.md).
