/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.tac.fpcf.analyses.string_analysis.interpretation

import org.opalj.br.cfg.CFG
import org.opalj.br.fpcf.properties.string_definition.StringConstancyInformation
import org.opalj.br.fpcf.properties.string_definition.StringConstancyLevel
import org.opalj.br.fpcf.properties.string_definition.StringConstancyType
import org.opalj.tac.GetStatic
import org.opalj.tac.Stmt
import org.opalj.tac.TACStmts
import org.opalj.tac.fpcf.analyses.string_analysis.V

/**
 * The `IntraproceduralGetStaticInterpreter` is responsible for processing
 * [[org.opalj.tac.GetStatic]]s in an intraprocedural fashion. Thus, they are not analyzed but a
 * fixed [[StringConstancyInformation]] is returned (see [[interpret]]).
 *
 * @see [[AbstractStringInterpreter]]
 *
 * @author Patrick Mell
 */
class IntraproceduralGetStaticInterpreter(
        cfg:         CFG[Stmt[V], TACStmts[V]],
        exprHandler: IntraproceduralInterpretationHandler
) extends AbstractStringInterpreter(cfg, exprHandler) {

    override type T = GetStatic

    /**
     * Currently, this type is not interpreted. Thus, this function always returns a list with a
     * single element consisting of [[StringConstancyLevel.DYNAMIC]],
     * [[StringConstancyType.APPEND]] and [[StringConstancyInformation.UnknownWordSymbol]].
     *
     * @see [[AbstractStringInterpreter.interpret]]
     */
    override def interpret(instr: T): List[StringConstancyInformation] =
        List(StringConstancyInformation(
            StringConstancyLevel.DYNAMIC,
            StringConstancyType.APPEND,
            StringConstancyInformation.UnknownWordSymbol
        ))

}