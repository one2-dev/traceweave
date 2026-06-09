package dev.one2.traceweave.copier

/**
 * Service-provider hook that lets a library ship copiers for its own exception types declaratively,
 * without any code in the consuming application. Implementations are discovered through the JDK
 * [java.util.ServiceLoader] from `META-INF/services/dev.one2.traceweave.copier.TraceWeaveCopierProvider`
 * and feed the same registry as [TraceWeave.register].
 *
 * This is the copier surface only: a provider can contribute copiers but cannot touch application-owned
 * policy (mode, logger, flags) and never activates traceweave — its contribution stays inert until the
 * application selects COPY mode.
 *
 * Priority: an explicit [TraceWeave.register] call from the application overrides a provider's
 * copier for the same type. If two providers contribute a copier for the same type, the first one
 * discovered wins and the duplicate is logged.
 *
 * Each copier follows the same contract as [TraceWeave.register]: return a fresh, writable instance of the
 * type preserving `message`, chain the original as cause, transfer suppressed/custom fields yourself,
 * and do not touch the stack trace — traceweave sets it.
 */
interface TraceWeaveCopierProvider {
  fun copiers(): Map<Class<out Throwable>, ExceptionCopier>
}
