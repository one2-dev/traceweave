package dev.one2.traceweave

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
  // FQN of the @TraceWeave annotation the plugin looks for (fully qualified to disambiguate from the
  // TraceWeave runtime object in this same package).
  val ANNOTATION_FQN: String = dev.one2.traceweave.annotation.TraceWeave::class.java.name

  // FQN of the object holding the entry point and the name of the function on it, that the plugin
  // generates calls to. Both are read from the real declarations, so a rename or move is picked up.
  val HANDLER_CLASS_FQN: String = TraceWeave::class.java.name
  val HANDLE_NAME: String = TraceWeave::handle.name
}
