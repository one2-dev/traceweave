package dev.one2.traceweave.handler

import dev.one2.traceweave.annotation.TraceWeave

/**
 * Coordinates the traceweave IR compiler plugin uses to emit calls into this runtime. They are
 * derived from the real declarations -- not duplicated as strings -- so a rename or package move is
 * picked up automatically and a mistyped FQN is impossible.
 *
 * Owned by the runtime on purpose: the compiler plugin exists to serve this runtime, so `:compiler`
 * depends on `:runtime` and reads the contract from here. Public only because `:compiler` is a
 * separate module; it is not part of the application-facing API.
 */
object TraceWeaveContract {
  // FQN of the @TraceWeave annotation the plugin looks for.
  val ANNOTATION_FQN: String = TraceWeave::class.java.name

  // Package and name of the handle() entry point the plugin generates calls to. The package is read
  // from this object's own class (it lives beside handle()); the name from the function reference.
  val HANDLE_PACKAGE: String = TraceWeaveContract::class.java.name.substringBeforeLast('.')
  val HANDLE_NAME: String = ::handle.name
}
