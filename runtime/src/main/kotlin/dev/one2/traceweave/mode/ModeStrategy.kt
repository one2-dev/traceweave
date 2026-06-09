package dev.one2.traceweave.mode

/**
 * How a lost coroutine caller frame is reconstructed. The handler delegates to the active strategy's
 * [weave]; the two built-ins are [InplaceMode] and [CopyMode], carried by [Mode.strategy].
 *
 * This is a public extension point: implement it and pass it to `configure { strategy = ... }` to
 * replace how traceweave handles a caught exception with your own logic, instead of the built-in modes.
 */
interface ModeStrategy {
  /**
   * Reconstructs the lost caller frame ([declaringClass].[methodName] at [fileName]:[lineNumber]) for
   * [error] and returns the exception to actually rethrow -- the same instance (mutated), a fresh copy,
   * or whatever your implementation decides. Called from the plugin-generated catch block; runs under
   * the handler's best-effort guard, so throwing here just falls back to the original exception.
   */
  fun weave(
    error: Throwable,
    declaringClass: String,
    methodName: String,
    fileName: String,
    lineNumber: Int,
  ): Throwable
}
