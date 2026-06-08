package dev.one2.traceweave.annotation

/**
 * Marks a suspend function as "visible in coroutine stack traces".
 *
 * The IR plugin wraps every suspend call inside the body of an annotated function in a try/catch
 * that, on the failure path, prepends a synthetic frame for this function (with the real call-site
 * position) to the exception's stack trace and rethrows the same instance. This reconstructs the
 * logical call chain that is otherwise lost across coroutine suspension points.
 *
 * Can be placed on:
 * - an interface method — every implementation (including in other modules) is treated as annotated;
 * - a class/interface — all of its suspend methods are treated as annotated.
 *
 * Retention=BINARY: kept in metadata (so an annotation on an interface method/type is visible when
 * the implementing module is compiled), but not needed via reflection at runtime.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class TraceWeave
