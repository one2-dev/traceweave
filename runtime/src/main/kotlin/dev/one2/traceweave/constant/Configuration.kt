package dev.one2.traceweave.constant

// System-property contract for the zero-config Gradle path (the plugin sets [PROP_ENABLED]) plus the
// slf4j logger name that identifies traceweave's own diagnostics.
internal object Configuration {
  const val PROP_PREFIX = "traceweave."
  const val PROP_MODE = PROP_PREFIX + "mode"
  const val PROP_ENABLED = PROP_PREFIX + "enabled"
  const val LOGGER_NAME = "dev.one2.traceweave"
}
