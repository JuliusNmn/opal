/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package pointsto

import scala.collection.mutable.ArrayBuffer

import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.EPK
import org.opalj.fpcf.EPS
import org.opalj.fpcf.FinalEP
import org.opalj.fpcf.FinalP
import org.opalj.fpcf.InterimEUBP
import org.opalj.fpcf.InterimPartialResult
import org.opalj.fpcf.InterimUBP
import org.opalj.fpcf.NoResult
import org.opalj.fpcf.PartialResult
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyComputationResult
import org.opalj.fpcf.PropertyKind
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Results
import org.opalj.fpcf.SomeEPS
import org.opalj.fpcf.UBPS
import org.opalj.br.analyses.SomeProject
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.FPCFTriggeredAnalysisScheduler
import org.opalj.br.fpcf.cg.properties.Callers
import org.opalj.br.fpcf.pointsto.properties.TypeBasedPointsToSet
import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.ObjectType
import org.opalj.br.analyses.VirtualFormalParameters
import org.opalj.br.analyses.VirtualFormalParametersKey
import org.opalj.br.fpcf.cg.properties.NoCallers
import org.opalj.br.DefinedMethod

/**
 * Applies the impact of preconfigured native methods to the points-to analysis.
 *
 * TODO: example
 * TODO: refer to the config file
 *
 * @author Florian Kuebler
 */
class ConfiguredNativeMethodsPointsToAnalysis private[analyses] (
        final val project: SomeProject
) extends FPCFAnalysis {

    private[this] implicit val declaredMethods: DeclaredMethods = p.get(DeclaredMethodsKey)
    private[this] implicit val virtualFormalParameters: VirtualFormalParameters = p.get(VirtualFormalParametersKey)

    private[this] val nativeMethodData: Map[DeclaredMethod, Option[Array[PointsToRelation]]] = {
        ConfiguredNativeMethods.reader.read(
            p.config, "org.opalj.fpcf.analyses.ConfiguredNativeMethodsAnalysis"
        ).nativeMethods.map { v ⇒ (v.method, v.pointsTo) }.toMap
    }

    def analyze(dm: DeclaredMethod): PropertyComputationResult = {
        (propertyStore(dm, Callers.key): @unchecked) match {
            case FinalP(NoCallers) ⇒
                // nothing to do, since there is no caller
                return NoResult;

            case eps: EPS[_, _] ⇒
                if (eps.ub eq NoCallers) {
                    // we can not create a dependency here, so the analysis is not allowed to create
                    // such a result
                    throw new IllegalStateException("illegal immediate result for callers")
                }
            // the method is reachable, so we analyze it!
        }

        // we only allow defined methods
        if (!dm.hasSingleDefinedMethod) return NoResult

        val method = dm.definedMethod

        if (method.isNative && nativeMethodData.contains(dm))
            return handleNativeMethod(dm.asDefinedMethod);

        NoResult
    }

    private[this] def handleNativeMethod(dm: DefinedMethod): PropertyComputationResult = {
        val nativeMethodDataOpt = nativeMethodData(dm)
        if (nativeMethodDataOpt.isEmpty)
            return NoResult;

        val data = nativeMethodDataOpt.get

        val results = ArrayBuffer.empty[ProperPropertyComputationResult]

        // for each configured points to relation, add all points-to info from the rhs to the lhs
        for (PointsToRelation(lhs, rhs) ← data) {
            rhs match {
                case asd: AllocationSiteDescription ⇒
                    val pts = UIDSet(ObjectType(asd.instantiatedType))
                    results += createPartialResultOpt(lhs.entity, pts).get
                case _ ⇒
                    val pointsToEOptP = propertyStore(rhs.entity, TypeBasedPointsToSet.key)

                    // the points-to set associated with the rhs
                    val pts: UIDSet[ObjectType] =
                        if (pointsToEOptP.hasUBP)
                            pointsToEOptP.ub.types
                        else
                            UIDSet.empty

                    // only create a partial result if there is some information to apply
                    // partial result that updates the points-to information
                    val prOpt = createPartialResultOpt(lhs.entity, pts)

                    // if the rhs is not yet final, we need to get updated if it changes
                    if (pointsToEOptP.isRefinable) {
                        results += InterimPartialResult(prOpt, Some(pointsToEOptP), c(lhs.entity, pointsToEOptP))
                    } else if (prOpt.isDefined) {
                        results += prOpt.get
                    }
            }
        }
        Results(results)
    }

    private[this] def createPartialResultOpt(lhs: Entity, newPointsTo: UIDSet[ObjectType]) = {
        if (newPointsTo.nonEmpty) {
            Some(PartialResult[Entity, TypeBasedPointsToSet](lhs, TypeBasedPointsToSet.key, {
                case InterimUBP(ub: TypeBasedPointsToSet) ⇒
                    // here we assert that updated returns the identity if pts is already contained
                    val newUB = ub.updated(newPointsTo)
                    if (newUB eq ub) {
                        None
                    } else {
                        Some(InterimEUBP(lhs, newUB))
                    }
                case _: EPK[Entity, TypeBasedPointsToSet] ⇒
                    Some(InterimEUBP(lhs, TypeBasedPointsToSet(newPointsTo)))

                case fep: FinalEP[Entity, TypeBasedPointsToSet] ⇒
                    throw new IllegalStateException(s"unexpected final value $fep")
            }))
        } else
            None
    }

    private[this] def c(
        lhs: Entity, rhsEOptP: EOptionP[Entity, TypeBasedPointsToSet]
    )(eps: SomeEPS): ProperPropertyComputationResult = eps match {
        case UBPS(rhsUB: TypeBasedPointsToSet, rhsIsFinal) ⇒
            // there is no change, but still a dependency, just return this continuation
            if (rhsEOptP.hasUBP && (rhsEOptP.ub eq rhsUB) && eps.isRefinable) {
                InterimPartialResult(Some(eps), c(lhs, eps.asInstanceOf[EPS[Entity, TypeBasedPointsToSet]]))
            } else {
                val pr = PartialResult[Entity, TypeBasedPointsToSet](lhs, TypeBasedPointsToSet.key, {
                    case InterimUBP(lhsUB: TypeBasedPointsToSet) ⇒
                        val seenTypes = if (rhsEOptP.hasUBP) rhsEOptP.ub.numElements else 0
                        val newTypes = rhsUB.dropOldestTypes(seenTypes).toSet

                        // here we assert that updated returns the identity if pts is already contained
                        val newUB = lhsUB.updated(newTypes)
                        if (newUB eq lhsUB) {
                            None
                        } else {
                            Some(InterimEUBP(lhs, newUB))
                        }

                    case _: EPK[Entity, TypeBasedPointsToSet] ⇒
                        Some(InterimEUBP(lhs, TypeBasedPointsToSet(rhsUB.types)))

                    case fep: FinalEP[Entity, TypeBasedPointsToSet] ⇒
                        throw new IllegalStateException(s"unexpected final value $fep")
                })

                if (rhsIsFinal) {
                    pr
                } else {
                    InterimPartialResult(
                        Some(pr), Some(eps), c(lhs, eps.asInstanceOf[EPS[Entity, TypeBasedPointsToSet]])
                    )
                }
            }
        case _ ⇒
            throw new IllegalArgumentException(s"unexpected update $eps")
    }
}

object ConfiguredNativeMethodsPointsToAnalysisScheduler extends FPCFTriggeredAnalysisScheduler {
    override type InitializationData = Null

    override def uses: Set[PropertyBounds] = PropertyBounds.ubs(
        Callers,
        TypeBasedPointsToSet
    )

    override def derivesCollaboratively: Set[PropertyBounds] = PropertyBounds.ubs(TypeBasedPointsToSet)

    override def derivesEagerly: Set[PropertyBounds] = Set.empty

    override def init(p: SomeProject, ps: PropertyStore): Null = {
        null
    }

    override def beforeSchedule(p: SomeProject, ps: PropertyStore): Unit = {}

    override def register(
        p: SomeProject, ps: PropertyStore, unused: Null
    ): ConfiguredNativeMethodsPointsToAnalysis = {
        val analysis = new ConfiguredNativeMethodsPointsToAnalysis(p)
        // register the analysis for initial values for callers (i.e. methods becoming reachable)
        ps.registerTriggeredComputation(Callers.key, analysis.analyze)
        analysis
    }

    override def afterPhaseScheduling(ps: PropertyStore, analysis: FPCFAnalysis): Unit = {}

    override def afterPhaseCompletion(
        p:        SomeProject,
        ps:       PropertyStore,
        analysis: FPCFAnalysis
    ): Unit = {}

    /**
     * Specifies the kind of the properties that will trigger the analysis to be registered.
     */
    override def triggeredBy: PropertyKind = Callers
}