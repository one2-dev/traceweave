package dev.one2.traceweave.compiler

import dev.one2.traceweave.TraceWeaveContract
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.Collections
import java.util.IdentityHashMap

// Resolved from the runtime's own declarations (see TraceWeaveContract) -- no hardcoded FQN strings.
private val TRACE_WEAVE_FQN = FqName(TraceWeaveContract.ANNOTATION_FQN)
private val HANDLER_CLASS = ClassId.topLevel(FqName(TraceWeaveContract.HANDLER_CLASS_FQN))
private val HANDLE = Name.identifier(TraceWeaveContract.HANDLE_NAME)

/**
 * A function is traced if ALL of the following hold:
 * 1. Its declaring class FQN does NOT match any [excluded] prefix (exclusion wins).
 * 2. At least one of:
 *    - `@TraceWeave` is on the function itself,
 *    - `@TraceWeave` is on its declaring class/interface,
 *    - its declaring class FQN (or package FQN for top-level) matches a [prefixes] entry,
 *    - any overridden function/type (transitively) is traced — so annotating an interface method or
 *      the interface itself propagates to all implementations, including cross-module ones.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrFunction.isTraceFrame(
  prefixes: List<String>,
  excluded: List<String>,
): Boolean {
  if (excluded.isNotEmpty() && ownerFqn().matchesAnyPrefix(excluded)) {
    return false
  }
  if (hasAnnotation(TRACE_WEAVE_FQN)) {
    return true
  }
  if (prefixes.isNotEmpty() && ownerFqn().matchesAnyPrefix(prefixes)) {
    return true
  }
  val owner = this.parent
  if (owner is IrClass && owner.hasAnnotation(TRACE_WEAVE_FQN)) {
    return true
  }
  val simple = this as? IrSimpleFunction ?: return false
  return simple.overriddenSymbols.any { it.owner.isTraceFrame(prefixes, excluded) }
}

/** Declaring class FQN for a member, or the package FQN for a top-level function. */
private fun IrFunction.ownerFqn(): String {
  val owner = this.parent
  return if (owner is IrClass) {
    owner.kotlinFqName.asString()
  } else {
    getPackageFragment().packageFqName.asString()
  }
}

private fun String.matchesAnyPrefix(prefixes: List<String>): Boolean = prefixes.any { this == it || startsWith("$it.") }

class TraceWeaveIrExtension(
  private val prefixes: List<String>,
  private val excluded: List<String>,
  private val messages: MessageCollector,
) : IrGenerationExtension {
  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    val handler =
      pluginContext
        .referenceFunctions(CallableId(HANDLER_CLASS, HANDLE))
        .singleOrNull() ?: return // runtime module not on the classpath — plugin is a no-op
    // handle() lives on the TraceWeave object, so calls need the singleton instance as dispatch receiver.
    val handlerObject = pluginContext.referenceClass(HANDLER_CLASS) ?: return

    // A traced function and its nested traced lambdas/local-funs are visited by separate passes, so in
    // include mode the same suspend call can be reached twice. Identity-track woven calls so each is
    // wrapped at most once (the outer pass wins) -- no double frame.
    val woven: MutableSet<IrCall> = Collections.newSetFromMap(IdentityHashMap())
    var functionCount = 0
    var callSiteCount = 0

    moduleFragment.acceptVoid(
      object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
          element.acceptChildrenVoid(this)
        }

        override fun visitFunction(declaration: IrFunction) {
          if (declaration.isTraceFrame(prefixes, excluded)) {
            val wrapper = SuspendCallWrapper(pluginContext, handler, handlerObject, declaration, woven)
            declaration.body?.transformChildrenVoid(wrapper)
            if (wrapper.wovenCount > 0) {
              functionCount++
              callSiteCount += wrapper.wovenCount
              // Per-function detail goes to LOGGING (Gradle `--debug`); the build's log level decides
              // whether it shows, so there is no plugin flag of our own.
              messages.report(
                CompilerMessageSeverity.LOGGING,
                "[traceweave] instrumented ${declaration.kotlinFqName.asString()} (${wrapper.wovenCount} call site(s))",
              )
            }
          }
          declaration.acceptChildrenVoid(this)
        }
      },
    )

    if (functionCount > 0) {
      // The summary goes to INFO (Gradle `--info`): a concise stat without the per-function noise.
      val module = moduleFragment.name.asString()
      messages.report(
        CompilerMessageSeverity.INFO,
        "[traceweave] $functionCount function(s), $callSiteCount call site(s) instrumented in $module",
      )
    }
  }
}

private class SuspendCallWrapper(
  private val pluginContext: IrPluginContext,
  private val handler: IrSimpleFunctionSymbol,
  private val handlerObject: IrClassSymbol,
  private val enclosing: IrFunction,
  private val woven: MutableSet<IrCall>,
) : IrElementTransformerVoid() {
  // How many suspend call-sites this pass actually wrapped (skips already-woven ones don't count).
  var wovenCount = 0
    private set

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun visitCall(expression: IrCall): IrExpression {
    expression.transformChildrenVoid()
    if (!expression.symbol.owner.isSuspend) {
      return expression
    }
    if (!woven.add(expression)) {
      // Already wrapped by an enclosing traced pass -- a second wrap would add a duplicate frame.
      return expression
    }
    wovenCount++
    return wrap(expression)
  }

  private fun wrap(call: IrCall): IrExpression {
    val builder = DeclarationIrBuilder(pluginContext, enclosing.symbol, call.startOffset, call.endOffset)
    val throwableType = pluginContext.irBuiltIns.throwableType
    val nothingType = pluginContext.irBuiltIns.nothingType

    val catchVar =
      buildVariable(
        parent = enclosing,
        startOffset = call.startOffset,
        endOffset = call.endOffset,
        origin = IrDeclarationOrigin.CATCH_PARAMETER,
        name = Name.identifier("e"),
        type = throwableType,
      )

    val declaringClass = enclosing.declaringClassName()
    val methodName = enclosing.name.asString()
    val fileEntry = enclosing.file.fileEntry
    val fileName = fileEntry.name.substringAfterLast('/').substringAfterLast('\\')
    val lineNumber = fileEntry.getLineNumber(call.startOffset) + 1

    val catchBody =
      builder.irBlock(resultType = nothingType) {
        +IrThrowImpl(
          call.startOffset,
          call.endOffset,
          nothingType,
          irCall(handler).apply {
            // arguments[0] is the dispatch receiver (the TraceWeave object); the value params follow.
            arguments[0] = irGetObject(handlerObject)
            arguments[1] = irGet(catchVar)
            arguments[2] = irString(declaringClass)
            arguments[3] = irString(methodName)
            arguments[4] = irString(fileName)
            arguments[5] = irInt(lineNumber)
          },
        )
      }

    return IrTryImpl(call.startOffset, call.endOffset, call.type).apply {
      tryResult = call
      catches +=
        IrCatchImpl(call.startOffset, call.endOffset, catchVar).apply {
          result = catchBody
        }
      finallyExpression = null
    }
  }

  private fun IrFunction.declaringClassName(): String {
    val owner = this.parent
    if (owner is IrClass) {
      return owner.kotlinFqName.asString()
    }
    val pkg = this.getPackageFragment().packageFqName.asString()
    val simpleFile =
      this.file.fileEntry.name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .removeSuffix(".kt")
    val facade = simpleFile.replaceFirstChar { it.uppercaseChar() } + "Kt"
    return if (pkg.isEmpty()) {
      facade
    } else {
      "$pkg.$facade"
    }
  }
}
