package dev.one2.traceweave.constant

internal object Inplace {
  // How far around the target index we look for an identical frame before inserting. A nonzero radius
  // collapses duplicates contributed by other sources (coroutine recovery, DebugProbes) that land a
  // position or two off from ours, not just an exact hit at the insertion index.
  const val DEDUP_RADIUS = 1
}
