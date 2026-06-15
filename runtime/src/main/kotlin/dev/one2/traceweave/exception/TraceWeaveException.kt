package dev.one2.traceweave.exception

/**
 * Opt-in contract for exceptions you own. Lets COPY mode reconstruct them with zero configuration and
 * full type/field fidelity, instead of leaning on the built-in copier table or reflection.
 *
 * In COPY mode traceweave calls [copyAsCause] on the in-flight exception, then writes the synthetic
 * frames onto the returned copy. The receiver *is* the original: it returns a copy of itself and chains
 * itself (`this`) as that copy's cause, so the original is left untouched and becomes the copy's cause.
 *
 * Contract:
 * - return a *fresh, writable* instance of the same type, preserving [Throwable.message] (and any
 *   custom fields you care about — they are yours to copy here, which is the main reason to prefer
 *   this over the table/reflection paths that recover only message + cause);
 * - set the cause to `this` (the receiver), normally through the constructor;
 * - transfer suppressed exceptions yourself if you need them — traceweave does not for this path;
 * - do NOT touch the stack trace — traceweave sets it.
 *
 * Only consulted in COPY mode; ignored in INPLACE mode. A throwing or contract-violating
 * implementation (e.g. an unwritable instance) is swallowed best-effort and the original exception is
 * used instead.
 */
interface TraceWeaveException {
  fun copyAsCause(): Throwable
}
