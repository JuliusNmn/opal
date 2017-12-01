/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2017
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj
package fpcf
package analyses
package escape

import org.opalj.ai.Domain
import org.opalj.ai.ValueOrigin
import org.opalj.ai.AIResult
import org.opalj.br.AllocationSite
import org.opalj.br.ExceptionHandlers
import org.opalj.br.VirtualMethod
import org.opalj.br.analyses.FormalParameter
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.FormalParametersKey
import org.opalj.br.analyses.AllocationSitesKey
//import org.opalj.br.analyses.FormalParameters
//import org.opalj.br.analyses.AllocationSites
import org.opalj.br.cfg.CFG
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.fpcf.properties._
import org.opalj.tac.DUVar
import org.opalj.tac.DefaultTACAIKey
import org.opalj.tac.Stmt
import org.opalj.tac.TACode
import org.opalj.tac.Parameters
import org.opalj.tac.TACMethodParameter
//import org.opalj.util.PerformanceEvaluation.time
//import org.opalj.log.OPALLogger.info

/**
 * A very simple flow-sensitive inter-procedural escape analysis.
 *
 * @author Florian Kuebler
 */
class InterProceduralEscapeAnalysis private (
        final val project: SomeProject
) extends AbstractEscapeAnalysis {

    override def entityEscapeAnalysis(
        e:        Entity,
        defSite:  ValueOrigin,
        uses:     IntTrieSet,
        code:     Array[Stmt[V]],
        params:   Parameters[TACMethodParameter],
        cfg:      CFG,
        handlers: ExceptionHandlers,
        aiResult: AIResult,
        m:        VirtualMethod
    ): AbstractEntityEscapeAnalysis =
        new InterProceduralEntityEscapeAnalysis(
            e,
            defSite,
            uses,
            code,
            params,
            cfg,
            handlers,
            aiResult,
            formalParameters,
            virtualFormalParameters,
            m,
            propertyStore,
            project
        )

    /**
     * Determine whether the given entity ([[AllocationSite]] or [[FormalParameter]]) escapes
     * its method.
     */
    def determineEscape(e: Entity): PropertyComputationResult = {
        e match {
            case as @ AllocationSite(m, pc, _) ⇒
                val TACode(params, code, cfg, handlers, _) = tacaiProvider(m)

                val index = code indexWhere { stmt ⇒ stmt.pc == pc }

                if (index != -1)
                    findUsesOfASAndAnalyze(as, index, code, params, cfg, handlers)
                else /* the allocation site is part of dead code */ ImmediateResult(e, NoEscape)

            case FormalParameter(m, _) if m.body.isEmpty ⇒ RefineableResult(e, AtMost(NoEscape))
            case FormalParameter(m, -1) ⇒
                val TACode(params, code, cfg, handlers, _) = project.get(DefaultTACAIKey)(m)
                val param = params.thisParameter
                doDetermineEscape(e, param.origin, param.useSites, code, params, cfg, handlers, aiProvider(m), m.asVirtualMethod)
            case FormalParameter(m, i) if m.descriptor.parameterType(-i - 2).isBaseType ⇒
                RefineableResult(e, AtMost(NoEscape))
            case FormalParameter(m, i) ⇒
                val TACode(params, code, cfg, handlers, _) = project.get(DefaultTACAIKey)(m)
                val param = params.parameter(i)
                doDetermineEscape(e, param.origin, param.useSites, code, params, cfg, handlers, aiProvider(m), m.asVirtualMethod)
        }
    }
}

object InterProceduralEscapeAnalysis extends FPCFAnalysisRunner {

    type V = DUVar[Domain#DomainValue]

    override def derivedProperties: Set[PropertyKind] = Set(EscapeProperty)

    override def usedProperties: Set[PropertyKind] = Set.empty

    def start(project: SomeProject, propertyStore: PropertyStore): FPCFAnalysis = {
        /*implicit val logContext = project.logContext
        time {
            SimpleEscapeAnalysis.start(project)
            propertyStore.waitOnPropertyComputationCompletion(
                resolveCycles = false,
                useFallbacksForIncomputableProperties = false
            )
        } { t ⇒ info("progress", s"simple escape analysis took ${t.toSeconds}") }*/

        VirtualCallAggregatingEscapeAnalysis.start(project)

        val analysis = new InterProceduralEscapeAnalysis(project)

        //val fps = propertyStore.context[FormalParameters].formalParameters.filter(propertyStore(_, EscapeProperty.key).p.isRefineable)
        //val ass = propertyStore.context[AllocationSites].allocationSites.filter(propertyStore(_, EscapeProperty.key).p.isRefineable)
        val fps = project.get(FormalParametersKey).formalParameters
        val ass = project.get(AllocationSitesKey).allocationSites

        propertyStore.scheduleForEntities(fps ++ ass)(analysis.determineEscape)
        analysis
    }
}
