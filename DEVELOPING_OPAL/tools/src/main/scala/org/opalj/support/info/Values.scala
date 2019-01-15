/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.support.info

import java.net.URL

import org.opalj.fpcf.Entity
import org.opalj.fpcf.EPS
import org.opalj.br.fpcf.FPCFAnalysesManagerKey
import org.opalj.br.Method
import org.opalj.br.analyses.BasicReport
import org.opalj.br.analyses.DefaultOneStepAnalysis
import org.opalj.br.analyses.Project
import org.opalj.br.Field
import org.opalj.ai.fpcf.analyses.EagerLBFieldValuesAnalysis
import org.opalj.ai.fpcf.analyses.EagerLBMethodReturnValuesAnalysis
import org.opalj.ai.fpcf.properties.FieldValue
import org.opalj.ai.fpcf.properties.MethodReturnValue

/**
 * Computes information regarding the values stored in fields and returned by methods.
 *
 * @author Michael Eichberg
 */
object Values extends DefaultOneStepAnalysis {

    override def title: String = "Values stored in fields and returned by methods"

    override def description: String = {
        "Provides information about the values returned by methods and those stored in fields."
    }

    override def doAnalyze(
                              project:       Project[URL],
                              parameters:    Seq[String],
                              isInterrupted: () ⇒ Boolean
                          ): BasicReport = {

        implicit val classHierarchy = project.classHierarchy

        val (ps,_)= project.get(FPCFAnalysesManagerKey).runAll(
            EagerLBFieldValuesAnalysis,
            EagerLBMethodReturnValuesAnalysis
        )

        val fieldValues: List[EPS[Entity, FieldValue]] = ps.entities(FieldValue.key).toList

        val mFields =
            fieldValues
                // we are deriving more precise lower bounds => eps.lb
                .map(eps ⇒ eps.e.asInstanceOf[Field].toJava(" => "+eps.lb.value.toString))
                .sorted
                .mkString("Field Values:\n\t", "\n\t", s"\n(Overall: ${fieldValues.size})")

        val methodReturnValues: List[EPS[Entity, MethodReturnValue]] = {
            ps.entities(MethodReturnValue.key).toList
        }

        val objectValuesReturningMethodsCount =
            project.allMethodsWithBody.filter(_.returnType.isObjectType).size

        val mMethods =
            methodReturnValues
                // we are deriving more precise lower bounds => eps.lb
                .map(eps ⇒ eps.e.asInstanceOf[Method].toJava(" => "+eps.lb.returnValue))
                .sorted
                .mkString(
                    "Method Return Values:\n\t",
                    "\n\t",
                    s"\n(Overall: ${methodReturnValues.size}/$objectValuesReturningMethodsCount)"
                )

        BasicReport(mFields+"\n\n"+mMethods)
    }
}
