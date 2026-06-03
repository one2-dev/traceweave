package dev.one2.traceweave.gradle

open class TraceWeaveExtension {
  var enabled: Boolean = true
  var classes: List<String> = emptyList()
  var excludeClasses: List<String> = emptyList()
}
