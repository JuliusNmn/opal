/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package cg

import scala.reflect.runtime.universe.runtimeMirror

import scala.collection.JavaConverters._

import org.opalj.log.LogContext
import org.opalj.log.OPALLogger.error
import org.opalj.fpcf.PropertyStore
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.ProjectInformationKey
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.cg.InitialEntryPointsKey
import org.opalj.br.analyses.ProjectInformationKeys
import org.opalj.br.analyses.cg.CallBySignatureKey
import org.opalj.br.analyses.cg.IsOverridableMethodKey
import org.opalj.br.fpcf.FPCFAnalysesManagerKey
import org.opalj.br.fpcf.FPCFAnalysisScheduler
import org.opalj.br.fpcf.PropertyStoreKey
import org.opalj.tac.fpcf.analyses.LazyTACAIProvider
import org.opalj.tac.fpcf.analyses.cg.CallGraphAnalysisScheduler
import org.opalj.tac.fpcf.analyses.cg.TypeProvider

/**
 * An abstract [[org.opalj.br.analyses.ProjectInformationKey]] to compute a [[CallGraph]].
 * Uses the call graph analyses modules specified in the config file under the key
 * "org.opalj.tac.cg.CallGraphKey.modules".
 *
 * @author Florian Kuebler
 */
trait CallGraphKey extends ProjectInformationKey[CallGraph, Nothing] {

    private[this] val CallBySignatureConfigKey = "org.opalj.br.analyses.cg.callBySignatureResolution"

    /**
     * Lists the call graph specific schedulers that must be run to compute the respective call
     * graph.
     */
    protected def callGraphSchedulers(
        project: SomeProject
    ): Traversable[FPCFAnalysisScheduler]

    override def requirements(project: SomeProject): ProjectInformationKeys = {
        setupTypeProvider(project)

        Seq(
            DeclaredMethodsKey,
            InitialEntryPointsKey,
            IsOverridableMethodKey,
            PropertyStoreKey,
            FPCFAnalysesManagerKey
        ) ++
            requiresCallBySignatureKey(project) ++
            CallGraphAnalysisScheduler.requiredProjectInformation ++
            callGraphSchedulers(project).flatMap(_.requiredProjectInformation) ++
            registeredAnalyses(project).flatMap(_.requiredProjectInformation)
    }

    protected[this] def registeredAnalyses(project: SomeProject): Seq[FPCFAnalysisScheduler] = {
        implicit val logContext = project.logContext
        val config = project.config

        // TODO use FPCFAnaylsesRegistry here
        config.getStringList(
            "org.opalj.tac.cg.CallGraphKey.modules"
        ).asScala.flatMap(resolveAnalysisRunner(_))
    }

    override def compute(project: SomeProject): CallGraph = {
        setupTypeProvider(project)

        implicit val typeProvider: TypeProvider = CallGraphKey._typeProvider.get
        implicit val ps: PropertyStore = project.get(PropertyStoreKey)

        val manager = project.get(FPCFAnalysesManagerKey)

        // TODO make TACAI analysis configurable
        var analyses: List[FPCFAnalysisScheduler] =
            List(
                LazyTACAIProvider
            )

        analyses ::= CallGraphAnalysisScheduler
        analyses ++= callGraphSchedulers(project)
        analyses ++= registeredAnalyses(project)

        manager.runAll(analyses)

        val cg = new CallGraph()
        CallGraphKey._callGraph = Some(cg)
        cg
    }

    private[this] def resolveAnalysisRunner(
        className: String
    )(implicit logContext: LogContext): Option[FPCFAnalysisScheduler] = {
        val mirror = runtimeMirror(getClass.getClassLoader)
        try {
            val module = mirror.staticModule(className)
            import mirror.reflectModule
            Some(reflectModule(module).instance.asInstanceOf[FPCFAnalysisScheduler])
        } catch {
            case sre: ScalaReflectionException ⇒
                error("call graph", s"cannot find analysis scheduler $className", sre)
                None
            case cce: ClassCastException ⇒
                error("call graph", "analysis scheduler class is invalid", cce)
                None
        }
    }

    private[this] def requiresCallBySignatureKey(p: SomeProject): ProjectInformationKeys = {
        val config = p.config
        if (config.hasPath(CallBySignatureConfigKey)
            && config.getBoolean(CallBySignatureConfigKey)) {
            return Seq(CallBySignatureKey);
        }
        Seq.empty
    }

    def getTypeProvider(project: SomeProject): TypeProvider

    final def setupTypeProvider(project: SomeProject): Unit = {
        CallGraphKey._typeProvider = Some(getTypeProvider(project))
    }
}

object CallGraphKey extends ProjectInformationKey[CallGraph, Nothing] {
    private var _callGraph: Option[CallGraph] = None
    private var _typeProvider: Option[TypeProvider] = None

    override def requirements(project: SomeProject): ProjectInformationKeys = Seq.empty

    override def compute(project: SomeProject): CallGraph = {
        _callGraph.get
    }

    def typeProvider: TypeProvider = {
        _typeProvider.get
    }
}