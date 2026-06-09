package dev.one2.traceweave

import dev.one2.traceweave.TraceWeave.entry
import dev.one2.traceweave.copier.TraceWeaveCopierProvider

// A type the application "does not own": no built-in copier and not TraceWeaveException, so COPY can
// only reconstruct it once a copier is registered for it.
class ThirdPartyException(
  message: String?,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

// Carries which copier produced it, so tests can tell a provider copy from an app-override copy.
class ProviderSuppliedException(
  message: String?,
  val copiedBy: String = "original",
  cause: Throwable? = null,
) : RuntimeException(message, cause)

// Discovered via META-INF/services. Ships a copier for ProviderSuppliedException with no app code.
class ProviderA : TraceWeaveCopierProvider {
  override fun copiers() =
    mapOf(
      entry(ProviderSuppliedException::class.java) { original ->
        ProviderSuppliedException(original.message, copiedBy = "provider", cause = original)
      },
    )
}

// A second provider claiming the same type, so the duplicate-provider path can be exercised.
class ProviderB : TraceWeaveCopierProvider {
  override fun copiers() =
    mapOf(
      entry(ProviderSuppliedException::class.java) { original ->
        ProviderSuppliedException(original.message, copiedBy = "provider", cause = original)
      },
    )
}
