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
package seq

private[seq] sealed abstract class QualifiedTask extends (() ⇒ Unit) {
    def isLazy: Boolean
}

// -------------------------------------------------------------------------------------------------
//
// EAGER TASKS
//
// -------------------------------------------------------------------------------------------------

private[seq] sealed abstract class EagerTask extends QualifiedTask {
    final def isLazy: Boolean = false
}

private[seq] case class EagerPropertyComputationTask[E <: Entity](
        ps: PropertyStore,
        e:  E,
        pc: PropertyComputation[E]
) extends EagerTask {

    override def apply(): Unit = ps.handleResult(pc(e), wasLazilyTriggered = false)
}

private[seq] case class EagerOnFinalUpdateComputationTask[E <: Entity, P <: Property](
        ps: PropertyStore,
        r:  FinalEP[E, P],
        c:  OnUpdateContinuation
) extends EagerTask {

    override def apply(): Unit = ps.handleResult(c(r), wasLazilyTriggered = false)
}

private[seq] case class EagerOnUpdateComputationTask[E <: Entity, P <: Property](
        ps:  PropertyStore,
        epk: EPK[E, P],
        c:   OnUpdateContinuation
) extends EagerTask {

    override def apply(): Unit = {
        // get the most current pValue when the depender
        // is eventually evaluated; the effectiveness
        // of this check depends on the scheduling strategy(!)
        val pValue = ps(epk)
        val eps = EPS(epk.e, pValue.lb, pValue.ub)
        ps.handleResult(c(eps), wasLazilyTriggered = false)
    }
}

// -------------------------------------------------------------------------------------------------
//
// LAZY TASKS
//
// -------------------------------------------------------------------------------------------------

private[seq] sealed abstract class LazyTask extends QualifiedTask {
    final def isLazy: Boolean = true
}

private[seq] case class LazyPropertyComputationTask[E <: Entity](
        ps: PropertyStore,
        e:  E,
        pc: PropertyComputation[E]
) extends LazyTask {
    override def apply(): Unit = {
        // TODO check if required
        ps.handleResult(pc(e), wasLazilyTriggered = true)
    }
}

private[seq] case class LazyOnFinalUpdateComputationTask[E <: Entity, P <: Property](
        ps: PropertyStore,
        r:  FinalEP[E, P],
        c:  OnUpdateContinuation
) extends LazyTask {

    override def apply(): Unit = {
        // TODO check if required
        ps.handleResult(c(r), wasLazilyTriggered = true)
    }
}

private[seq] case class LazyOnUpdateComputationTask[E <: Entity, P <: Property](
        ps:  PropertyStore,
        epk: EPK[E, P],
        c:   OnUpdateContinuation
) extends LazyTask {

    override def apply(): Unit = {
        // get the most current pValue when the depender
        // is eventually evaluated; the effectiveness
        // of this check depends on the scheduling strategy(!)
        val pValue = ps(epk)
        // TODO check if required
        val eps = EPS(epk.e, pValue.lb, pValue.ub)
        ps.handleResult(c(eps), wasLazilyTriggered = true)
    }
}

// -------------------------------------------------------------------------------------------------
//
// FACTORIES FOR EAGER/LAZY TASKS
//
// -------------------------------------------------------------------------------------------------

private[seq] object OnFinalUpdateComputationTask {

    def apply[E <: Entity, P <: Property](
        ps:            PropertyStore,
        e:             E,
        p:             P,
        c:             OnUpdateContinuation,
        performLazily: Boolean
    ): QualifiedTask = {
        apply(ps, FinalEP(e, p), c, performLazily)
    }

    def apply[E <: Entity, P <: Property](
        ps:            PropertyStore,
        r:             FinalEP[E, P],
        c:             OnUpdateContinuation,
        performLazily: Boolean
    ): QualifiedTask = {
        if (performLazily) {
            new LazyOnFinalUpdateComputationTask(ps, r, c)
        } else {
            new EagerOnFinalUpdateComputationTask(ps, r, c)
        }
    }

}

private[seq] object OnUpdateComputationTask {

    def apply[E <: Entity, P <: Property](
        ps:            PropertyStore,
        epk:           EPK[E, P],
        c:             OnUpdateContinuation,
        performLazily: Boolean
    ): QualifiedTask = {
        if (performLazily) {
            new LazyOnUpdateComputationTask(ps, epk, c)
        } else {
            new EagerOnUpdateComputationTask(ps, epk, c)
        }
    }

}
