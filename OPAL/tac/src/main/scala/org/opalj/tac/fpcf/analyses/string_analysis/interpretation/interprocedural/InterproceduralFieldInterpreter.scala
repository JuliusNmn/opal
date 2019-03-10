/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.tac.fpcf.analyses.string_analysis.interpretation.interprocedural

import scala.collection.mutable.ListBuffer

import org.opalj.fpcf.FinalEP
import org.opalj.fpcf.InterimResult
import org.opalj.fpcf.ProperOnUpdateContinuation
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Result
import org.opalj.br.analyses.FieldAccessInformation
import org.opalj.br.fpcf.properties.StringConstancyProperty
import org.opalj.br.fpcf.properties.string_definition.StringConstancyInformation
import org.opalj.br.fpcf.properties.string_definition.StringConstancyLevel
import org.opalj.tac.GetField
import org.opalj.tac.fpcf.analyses.string_analysis.V
import org.opalj.tac.fpcf.analyses.string_analysis.interpretation.AbstractStringInterpreter
import org.opalj.tac.fpcf.analyses.string_analysis.InterproceduralComputationState
import org.opalj.tac.fpcf.analyses.string_analysis.InterproceduralStringAnalysis

/**
 * The `InterproceduralFieldInterpreter` is responsible for processing [[GetField]]s. In this
 * implementation, there is currently only primitive support for fields, i.e., they are not analyzed
 * but a constant [[org.opalj.br.fpcf.properties.string_definition.StringConstancyInformation]]
 * is returned (see [[interpret]] of this class).
 *
 * @see [[AbstractStringInterpreter]]
 *
 * @author Patrick Mell
 */
class InterproceduralFieldInterpreter(
        state:                  InterproceduralComputationState,
        exprHandler:            InterproceduralInterpretationHandler,
        ps:                     PropertyStore,
        fieldAccessInformation: FieldAccessInformation,
        c:                      ProperOnUpdateContinuation
) extends AbstractStringInterpreter(state.tac.cfg, exprHandler) {

    override type T = GetField[V]

    /**
     * Currently, fields are not interpreted. Thus, this function always returns a list with a
     * single element consisting of
     * [[org.opalj.br.fpcf.properties.string_definition.StringConstancyLevel.DYNAMIC]],
     * [[org.opalj.br.fpcf.properties.string_definition.StringConstancyType.APPEND]] and
     * [[org.opalj.br.fpcf.properties.string_definition.StringConstancyInformation.UnknownWordSymbol]].
     *
     * @note For this implementation, `defSite` plays a role!
     *
     * @see [[AbstractStringInterpreter.interpret]]
     */
    override def interpret(instr: T, defSite: Int): ProperPropertyComputationResult = {
        val defSitEntity: Integer = defSite
        if (!InterproceduralStringAnalysis.isSupportedType(instr.declaredFieldType)) {
            // Unknown type => Cannot further approximate
            return Result(instr, StringConstancyProperty.lb)
        }

        var hasInit = false
        val results = ListBuffer[ProperPropertyComputationResult]()
        fieldAccessInformation.writeAccesses(instr.declaringClass, instr.name).foreach {
            case (m, pcs) ⇒ pcs.foreach { pc ⇒
                if (m.name == "<init>") {
                    hasInit = true
                }
                val (tacEps, tac) = getTACAI(ps, m, state)
                val nextResult = if (tacEps.isRefinable) {
                    InterimResult(
                        instr,
                        StringConstancyProperty.lb,
                        StringConstancyProperty.ub,
                        state.dependees,
                        c
                    )
                } else {
                    tac match {
                        case Some(methodTac) ⇒
                            val stmt = methodTac.stmts(methodTac.pcToIndex(pc))
                            val entity = (stmt.asPutField.value.asVar, m)
                            val eps = ps(entity, StringConstancyProperty.key)
                            eps match {
                                case FinalEP(e, p) ⇒ Result(e, p)
                                case _ ⇒
                                    state.dependees = eps :: state.dependees
                                    // We need some mapping from an entity to an index in order for
                                    // the processFinalP to find an entry. We cannot use the given
                                    // def site as this would mark the def site as finalized even
                                    // though it might not be. Thus, we use -1 as it is a safe dummy
                                    // value
                                    state.appendToVar2IndexMapping(entity._1, -1)
                                    InterimResult(
                                        entity,
                                        StringConstancyProperty.lb,
                                        StringConstancyProperty.ub,
                                        state.dependees,
                                        c
                                    )
                            }
                        case _ ⇒
                            // No TAC available
                            Result(defSitEntity, StringConstancyProperty.lb)
                    }
                }
                results.append(nextResult)
            }
        }

        if (results.isEmpty) {
            // No methods, which write the field, were found => Field could either be null or
            // any value
            val possibleStrings = "(^null$|"+StringConstancyInformation.UnknownWordSymbol+")"
            val sci = StringConstancyInformation(
                StringConstancyLevel.DYNAMIC, possibleStrings = possibleStrings
            )
            state.appendToFpe2Sci(
                defSitEntity, StringConstancyProperty.lb.stringConstancyInformation
            )
            Result(defSitEntity, StringConstancyProperty(sci))
        } else {
            // If all results are final, determine all possible values for the field. Otherwise,
            // return some intermediate result to indicate that the computation is not yet done
            if (results.forall(_.isInstanceOf[Result])) {
                // No init is present => append a `null` element to indicate that the field might be
                // null; this behavior could be refined by only setting the null element if no
                // statement is guaranteed to be executed prior to the field read
                if (!hasInit) {
                    results.append(Result(
                        instr, StringConstancyProperty(StringConstancyInformation.getNullElement)
                    ))
                }
                val resultScis = results.map {
                    StringConstancyProperty.extractFromPPCR(_).stringConstancyInformation
                }
                val finalSci = StringConstancyInformation.reduceMultiple(resultScis)
                state.appendToFpe2Sci(defSitEntity, finalSci)
                Result(defSitEntity, StringConstancyProperty(finalSci))
            } else {
                results.find(!_.isInstanceOf[Result]).get
            }
        }
    }

}