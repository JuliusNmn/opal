/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package pointsto

import org.opalj.fpcf.PropertyMetaInformation
import org.opalj.br.analyses.SomeProject
import org.opalj.br.fpcf.properties.pointsto.AllocationSitePointsToSet
import org.opalj.tac.cg.CallGraphKey
import org.opalj.tac.fpcf.analyses.cg.TypeProvider

class AllocationSiteBasedPointsToAnalysis private[analyses] (
        final val project:                        SomeProject,
        final override implicit val typeProvider: TypeProvider
) extends AbstractPointsToAnalysis with AllocationSiteBasedAnalysis

object AllocationSiteBasedPointsToAnalysisScheduler extends AbstractPointsToAnalysisScheduler {

    override val propertyKind: PropertyMetaInformation = AllocationSitePointsToSet
    override val createAnalysis: SomeProject ⇒ AllocationSiteBasedPointsToAnalysis =
        new AllocationSiteBasedPointsToAnalysis(_, CallGraphKey.typeProvider)
}
