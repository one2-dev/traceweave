package dev.one2.traceweave.logging

import dev.one2.traceweave.constant.Configuration
import org.slf4j.LoggerFactory

// slf4j facade for traceweave's own diagnostics. No-op until the host app provides a binding.
internal val logger = LoggerFactory.getLogger(Configuration.LOGGER_NAME)
