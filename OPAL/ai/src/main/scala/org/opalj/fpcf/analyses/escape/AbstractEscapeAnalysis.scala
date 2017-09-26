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
import org.opalj.ai.domain.RecordDefUse
import org.opalj.br.Method
import org.opalj.collection.immutable.IntArraySet
import org.opalj.tac.DUVar
import org.opalj.tac.TACMethodParameter
import org.opalj.tac.Parameters
import org.opalj.tac.Stmt
import org.opalj.tac.DefaultTACAIKey
import org.opalj.tac.TACode

/**
 * A trait for different implementations of escape analyses. Provides a factory method for the
 * concrete analysis of a single entity and a method to determine the escape information.
 *
 * The control-flow is intended to be: Client calls determineEscape. This method extracts the
 * information for the given entity and calls doDetermineEscape.
 *
 * @author Florian Kuebler
 */
trait AbstractEscapeAnalysis extends FPCFAnalysis {
    type V = DUVar[(Domain with RecordDefUse)#DomainValue]

    /**
     * A factory method for the concrete [[AbstractEntityEscapeAnalysis]]. Every concrete escape
     * analysis must define its corresponding entity analysis.
     */
    protected def entityEscapeAnalysis(
        e:       Entity,
        defSite: ValueOrigin,
        uses:    IntArraySet,
        code:    Array[Stmt[V]],
        params:  Parameters[TACMethodParameter],
        m:       Method
    ): AbstractEntityEscapeAnalysis

    /**
     * Creates a new entity escape analysis using [[entityEscapeAnalysis]] and calls
     * [[AbstractEntityEscapeAnalysis.doDetermineEscape()]] to compute the escape state.
     */
    protected final def doDetermineEscape(
        e:       Entity,
        defSite: ValueOrigin,
        uses:    IntArraySet,
        code:    Array[Stmt[V]],
        params:  Parameters[TACMethodParameter],
        m:       Method
    ): PropertyComputationResult = {
        entityEscapeAnalysis(e, defSite, uses, code, params, m).doDetermineEscape()
    }

    /**
     * Extracts information from the given entity and should call [[doDetermineEscape]] afterwards.
     * For some entities a result might be returned immediately.
     */
    def determineEscape(e: Entity): PropertyComputationResult

    val tacai: (Method) ⇒ TACode[TACMethodParameter, DUVar[(Domain with RecordDefUse)#DomainValue]] = project.get(DefaultTACAIKey)
}