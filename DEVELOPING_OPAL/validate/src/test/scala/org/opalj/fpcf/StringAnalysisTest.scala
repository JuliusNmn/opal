/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf

import java.io.File
import java.net.URL

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import org.opalj.collection.immutable.ConstArray
import org.opalj.br.analyses.Project
import org.opalj.br.Annotation
import org.opalj.br.Method
import org.opalj.br.cfg.CFG
import org.opalj.br.Annotations
import org.opalj.br.fpcf.properties.StringConstancyProperty
import org.opalj.br.fpcf.FPCFAnalysesManagerKey
import org.opalj.br.fpcf.cg.properties.ReflectionRelatedCallees
import org.opalj.br.fpcf.cg.properties.SerializationRelatedCallees
import org.opalj.br.fpcf.cg.properties.StandardInvokeCallees
import org.opalj.br.fpcf.cg.properties.ThreadRelatedIncompleteCallSites
import org.opalj.ai.fpcf.analyses.LazyL0BaseAIAnalysis
import org.opalj.tac.DefaultTACAIKey
import org.opalj.tac.Stmt
import org.opalj.tac.TACStmts
import org.opalj.tac.VirtualMethodCall
import org.opalj.tac.fpcf.analyses.cg.reflection.TriggeredReflectionRelatedCallsAnalysis
import org.opalj.tac.fpcf.analyses.cg.RTACallGraphAnalysisScheduler
import org.opalj.tac.fpcf.analyses.cg.TriggeredFinalizerAnalysisScheduler
import org.opalj.tac.fpcf.analyses.cg.TriggeredSerializationRelatedCallsAnalysis
import org.opalj.tac.fpcf.analyses.string_analysis.InterproceduralStringAnalysis
import org.opalj.tac.fpcf.analyses.string_analysis.LazyInterproceduralStringAnalysis
import org.opalj.tac.fpcf.analyses.string_analysis.LazyLocalStringAnalysis
import org.opalj.tac.fpcf.analyses.string_analysis.LocalStringAnalysis
import org.opalj.tac.fpcf.analyses.string_analysis.V
import org.opalj.tac.fpcf.analyses.TriggeredSystemPropertiesAnalysis
import org.opalj.tac.fpcf.analyses.cg.LazyCalleesAnalysis
import org.opalj.tac.fpcf.analyses.cg.TriggeredStaticInitializerAnalysis
import org.opalj.tac.fpcf.analyses.cg.TriggeredThreadRelatedCallsAnalysis
import org.opalj.tac.fpcf.analyses.TACAITransformer
import org.opalj.tac.fpcf.analyses.cg.TriggeredInstantiatedTypesAnalysis
import org.opalj.tac.fpcf.analyses.cg.TriggeredLoadedClassesAnalysis

/**
 * @param fqTestMethodsClass The fully-qualified name of the class that contains the test methods.
 * @param nameTestMethod The name of the method from which to extract DUVars to analyze.
 * @param filesToLoad Necessary (test) files / classes to load. Note that this list should not
 *                    include "StringDefinitions.class" as this class is loaded by default.
 */
sealed class StringAnalysisTestRunner(
        val fqTestMethodsClass: String,
        val nameTestMethod:     String,
        val filesToLoad:        List[String]
) extends PropertiesTest {

    private val fqStringDefAnnotation =
        "org.opalj.fpcf.properties.string_analysis.StringDefinitionsCollection"

    /**
     * @return Returns all relevant project files (NOT including library files) to run the tests.
     */
    def getRelevantProjectFiles: Array[File] = {
        val necessaryFiles = Array(
            "properties/string_analysis/StringDefinitions.class"
        ) ++ filesToLoad
        val basePath = System.getProperty("user.dir")+
            "/DEVELOPING_OPAL/validate/target/scala-2.12/test-classes/org/opalj/fpcf/"

        necessaryFiles.map { filePath ⇒ new File(basePath + filePath) }
    }

    /**
     * Extracts [[org.opalj.tac.UVar]]s from a set of statements. The locations of the UVars are
     * identified by the argument to the very first call to LocalTestMethods#analyzeString.
     *
     * @param cfg The control flow graph from which to extract the UVar, usually derived from the
     *            method that contains the call(s) to LocalTestMethods#analyzeString.
     * @return Returns the arguments of the LocalTestMethods#analyzeString as a DUVars list in the
     *         order in which they occurred in the given statements.
     */
    def extractUVars(cfg: CFG[Stmt[V], TACStmts[V]]): List[V] = {
        cfg.code.instructions.filter {
            case VirtualMethodCall(_, declClass, _, name, _, _, _) ⇒
                declClass.toJavaClass.getName == fqTestMethodsClass && name == nameTestMethod
            case _ ⇒ false
        }.map(_.asVirtualMethodCall.params.head.asVar).toList
    }

    /**
     * Takes an annotation and checks if it is a
     * [[org.opalj.fpcf.properties.string_analysis.StringDefinitions]] annotation.
     *
     * @param a The annotation to check.
     * @return True if the `a` is of type StringDefinitions and false otherwise.
     */
    def isStringUsageAnnotation(a: Annotation): Boolean =
        a.annotationType.toJavaClass.getName == fqStringDefAnnotation

    /**
     * Extracts a `StringDefinitions` annotation from a `StringDefinitionsCollection` annotation.
     * Make sure that you pass an instance of `StringDefinitionsCollection` and that the element at
     * the given index really exists. Otherwise an exception will be thrown.
     *
     * @param a The `StringDefinitionsCollection` to extract a `StringDefinitions` from.
     * @param index The index of the element from the `StringDefinitionsCollection` annotation to
     *              get.
     * @return Returns the desired `StringDefinitions` annotation.
     */
    def getStringDefinitionsFromCollection(a: Annotations, index: Int): Annotation =
        a.head.elementValuePairs(1).value.asArrayValue.values(index).asAnnotationValue.annotation

    def determineEAS(
        p: Project[URL],
        ps: PropertyStore,
        allMethodsWithBody: ConstArray[Method],
    ): Traversable[((V, Method), String ⇒ String, List[Annotation])] = {
        // We need a "method to entity" matching for the evaluation (see further below)
        val m2e = mutable.HashMap[Method, Entity]()

        val tacProvider = p.get(DefaultTACAIKey)
        allMethodsWithBody.filter {
            _.runtimeInvisibleAnnotations.foldLeft(false)(
                (exists, a) ⇒ exists || isStringUsageAnnotation(a)
            )
        } foreach { m ⇒
            extractUVars(tacProvider(m).cfg).foreach { uvar ⇒
                if (!m2e.contains(m)) {
                    m2e += m → ListBuffer(uvar)
                } else {
                    m2e(m).asInstanceOf[ListBuffer[V]].append(uvar)
                }
                ps.force((uvar, m), StringConstancyProperty.key)
            }
        }

        // As entity, we need not the method but a tuple (DUVar, Method), thus this transformation
        val eas = methodsWithAnnotations(p).filter(am ⇒ m2e.contains(am._1)).flatMap { am ⇒
            m2e(am._1).asInstanceOf[ListBuffer[V]].zipWithIndex.map {
                case (duvar, index) ⇒
                    Tuple3(
                        (duvar, am._1),
                        { s: String ⇒ s"${am._2(s)} (#$index)" },
                        List(getStringDefinitionsFromCollection(am._3, index))
                    )
            }
        }

        eas
    }

}

/**
 * Tests whether the [[LocalStringAnalysis]] works correctly with respect to some well-defined
 * tests.
 *
 * @author Patrick Mell
 */
class LocalStringAnalysisTest extends PropertiesTest {

    describe("the org.opalj.fpcf.LocalStringAnalysis is started") {
        val runner = new StringAnalysisTestRunner(
            LocalStringAnalysisTest.fqTestMethodsClass,
            LocalStringAnalysisTest.nameTestMethod,
            LocalStringAnalysisTest.filesToLoad
        )
        val p = Project(runner.getRelevantProjectFiles, Array[File]())

        val manager = p.get(FPCFAnalysesManagerKey)
        val (ps, _) = manager.runAll(LazyLocalStringAnalysis)
        val testContext = TestContext(p, ps, List(new LocalStringAnalysis(p)))

        LazyLocalStringAnalysis.init(p, ps)
        LazyLocalStringAnalysis.schedule(ps, null)

        val eas = runner.determineEAS(p, ps, p.allMethodsWithBody)

        testContext.propertyStore.shutdown()
        validateProperties(testContext, eas, Set("StringConstancy"))
        ps.waitOnPhaseCompletion()
    }

}

object LocalStringAnalysisTest {

    val fqTestMethodsClass = "org.opalj.fpcf.fixtures.string_analysis.LocalTestMethods"
    // The name of the method from which to extract DUVars to analyze
    val nameTestMethod = "analyzeString"
    // Files to load for the runner
    val filesToLoad = List(
        "fixtures/string_analysis/LocalTestMethods.class"
    )

}

/**
 * Tests whether the [[LocalStringAnalysis]] works correctly with respect to some well-defined
 * tests.
 *
 * @author Patrick Mell
 */
class InterproceduralStringAnalysisTest extends PropertiesTest {

    describe("the org.opalj.fpcf.InterproceduralStringAnalysis is started") {
        val runner = new StringAnalysisTestRunner(
            InterproceduralStringAnalysisTest.fqTestMethodsClass,
            InterproceduralStringAnalysisTest.nameTestMethod,
            InterproceduralStringAnalysisTest.filesToLoad
        )
        val p = Project(runner.getRelevantProjectFiles, Array[File]())

        val manager = p.get(FPCFAnalysesManagerKey)
        val (ps, analyses) = manager.runAll(
            TACAITransformer,
            LazyL0BaseAIAnalysis,
            RTACallGraphAnalysisScheduler,
            TriggeredStaticInitializerAnalysis,
            TriggeredLoadedClassesAnalysis,
            TriggeredFinalizerAnalysisScheduler,
            TriggeredThreadRelatedCallsAnalysis,
            TriggeredSerializationRelatedCallsAnalysis,
            TriggeredReflectionRelatedCallsAnalysis,
            TriggeredSystemPropertiesAnalysis,
            TriggeredInstantiatedTypesAnalysis,
            LazyCalleesAnalysis(Set(
                StandardInvokeCallees,
                SerializationRelatedCallees,
                ReflectionRelatedCallees,
                ThreadRelatedIncompleteCallSites
            )),
            LazyInterproceduralStringAnalysis
        )

        val testContext = TestContext(
            p, ps, List(new InterproceduralStringAnalysis(p)) ++ analyses.map(_._2)
        )
        LazyInterproceduralStringAnalysis.init(p, ps)
        LazyInterproceduralStringAnalysis.schedule(ps, null)

        val eas = runner.determineEAS(p, ps, p.allMethodsWithBody)
        testContext.propertyStore.shutdown()
        validateProperties(testContext, eas, Set("StringConstancy"))
        ps.waitOnPhaseCompletion()
    }

}

object InterproceduralStringAnalysisTest {

    val fqTestMethodsClass = "org.opalj.fpcf.fixtures.string_analysis.InterproceduralTestMethods"
    // The name of the method from which to extract DUVars to analyze
    val nameTestMethod = "analyzeString"
    // Files to load for the runner
    val filesToLoad = List(
        "fixtures/string_analysis/InterproceduralTestMethods.class"
    )

}