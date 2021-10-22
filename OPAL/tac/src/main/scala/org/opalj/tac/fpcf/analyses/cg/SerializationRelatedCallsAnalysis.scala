/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package cg

import scala.annotation.tailrec

import org.opalj.collection.immutable.IntTrieSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.EPS
import org.opalj.fpcf.FinalEP
import org.opalj.fpcf.InterimPartialResult
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyComputationResult
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Results
import org.opalj.fpcf.SomeEPS
import org.opalj.value.ValueInformation
import org.opalj.br.analyses.SomeProject
import org.opalj.br.fpcf.BasicFPCFEagerAnalysisScheduler
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.DeclaredMethod
import org.opalj.br.ElementReferenceType
import org.opalj.br.MethodDescriptor
import org.opalj.br.MethodDescriptor.JustReturnsObject
import org.opalj.br.MethodDescriptor.NoArgsAndReturnVoid
import org.opalj.br.ObjectType
import org.opalj.br.ObjectType.{ObjectOutputStream ⇒ ObjectOutputStreamType}
import org.opalj.br.ObjectType.{ObjectInputStream ⇒ ObjectInputStreamType}
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.ProjectInformationKeys
import org.opalj.tac.fpcf.properties.cg.Callees
import org.opalj.tac.fpcf.properties.cg.Callers
import org.opalj.br.Method
import org.opalj.br.ReferenceType
import org.opalj.tac.cg.CallGraphKey
import org.opalj.tac.fpcf.properties.TACAI
import org.opalj.tac.fpcf.properties.TheTACAI

/**
 * Analysis handling the specifics of java.io.ObjectOutputStream.writeObject.
 * This method may invoke writeObject, writeReplace or writeExternal on its parameter.
 *
 * @author Florian Kuebler
 * @author Dominik Helm
 */
class OOSWriteObjectAnalysis private[analyses] (
        override val project:               SomeProject,
        override implicit val typeProvider: TypeProvider
) extends TACAIBasedAPIBasedAnalysis with TypeConsumerAnalysis {

    override val apiMethod: DeclaredMethod = declaredMethods(
        ObjectOutputStreamType,
        "",
        ObjectOutputStreamType,
        "writeObject",
        MethodDescriptor.JustTakesObject
    )

    final val ObjectOutputType = ObjectType("java/io/ObjectOutput")
    final val WriteExternalDescriptor = MethodDescriptor.JustTakes(ObjectOutputType)
    final val WriteObjectDescriptor = MethodDescriptor.JustTakes(ObjectOutputStreamType)

    override def processNewCaller(
        calleeContext:  ContextType,
        callerContext:  ContextType,
        pc:             Int,
        tac:            TACode[TACMethodParameter, V],
        receiverOption: Option[Expr[V]],
        params:         Seq[Option[Expr[V]]],
        tgtVarOption:   Option[V],
        isDirect:       Boolean
    ): ProperPropertyComputationResult = {
        val indirectCalls = new IndirectCalls()

        if (params.nonEmpty && params.head.isDefined) {
            implicit val stmts: Array[Stmt[V]] = tac.stmts
            val param = params.head.get.asVar

            val receiver = persistentUVar(param)
            val parameters = Seq(receiverOption.flatMap(os ⇒ persistentUVar(os.asVar)))

            implicit val state: CGState[ContextType] = new CGState[ContextType](
                callerContext, FinalEP(callerContext.method.definedMethod, TheTACAI(tac)),
            )

            handleOOSWriteObject(callerContext, param, pc, receiver, parameters, indirectCalls)

            returnResult(param, receiver, parameters, indirectCalls)(state)
        } else {
            indirectCalls.addIncompleteCallSite(pc)
            Results(indirectCalls.partialResults(callerContext))
        }
    }

    def c(
        receiverVar: V,
        receiver:    Option[(ValueInformation, IntTrieSet)],
        parameters:  Seq[Option[(ValueInformation, IntTrieSet)]],
        state:       CGState[ContextType]
    )(eps: SomeEPS): ProperPropertyComputationResult = {
        val pc = state.dependersOf(eps.toEPK).head.asInstanceOf[Int]

        // ensures, that we only add new vm reachable methods
        val indirectCalls = new IndirectCalls()

        typeProvider.continuation(receiverVar, eps.asInstanceOf[EPS[Entity, PropertyType]]) {
            newType ⇒
                handleType(newType, state.callContext, pc, receiver, parameters, indirectCalls)
        }(state)

        if (eps.isFinal) {
            state.removeDependee(eps.toEPK)
        } else {
            state.updateDependency(eps)
        }

        returnResult(receiverVar, receiver, parameters, indirectCalls)(state)
    }

    def returnResult(
        receiverVar:   V,
        receiver:      Option[(ValueInformation, IntTrieSet)],
        parameters:    Seq[Option[(ValueInformation, IntTrieSet)]],
        indirectCalls: IndirectCalls
    )(implicit state: CGState[ContextType]): ProperPropertyComputationResult = {
        val results = indirectCalls.partialResults(state.callContext)
        if (state.hasOpenDependencies)
            Results(
                InterimPartialResult(state.dependees, c(receiverVar, receiver, parameters, state)),
                results
            )
        else
            Results(results)
    }

    private[this] def handleOOSWriteObject(
        callContext:   ContextType,
        param:         V,
        callPC:        Int,
        receiver:      Option[(ValueInformation, IntTrieSet)],
        parameters:    Seq[Option[(ValueInformation, IntTrieSet)]],
        indirectCalls: IndirectCalls
    )(implicit state: CGState[ContextType]): Unit = {
        typeProvider.foreachType(
            param,
            typeProvider.typesProperty(
                param, callContext, callPC.asInstanceOf[Entity], state.tac.stmts
            )
        ) { tpe ⇒ handleType(tpe, callContext, callPC, receiver, parameters, indirectCalls) }
    }

    private[this] def handleType(
        tpe:           ReferenceType,
        callContext:   ContextType,
        callPC:        Int,
        receiver:      Option[(ValueInformation, IntTrieSet)],
        parameters:    Seq[Option[(ValueInformation, IntTrieSet)]],
        indirectCalls: IndirectCalls
    ): Unit = {
        if (tpe.isArrayType && !tpe.asArrayType.elementType.isObjectType) {
            indirectCalls.addIncompleteCallSite(callPC)
            return ;
        }

        val paramType =
            if (tpe.isArrayType)
                tpe.asArrayType.elementType.asObjectType
            else tpe.asObjectType

        if (classHierarchy.isSubtypeOf(paramType, ObjectType.Externalizable)) {
            val writeExternalMethod = project.instanceCall(
                paramType,
                paramType,
                "writeExternal",
                WriteExternalDescriptor
            )

            indirectCalls.addCallOrFallback(
                callContext,
                callPC,
                writeExternalMethod,
                ObjectType.Externalizable.packageName,
                ObjectType.Externalizable,
                "writeExternal",
                WriteExternalDescriptor,
                parameters,
                receiver,
                tgt ⇒ typeProvider.expandContext(callContext, tgt, callPC)
            )
        } else {
            val writeObjectMethod = project.specialCall(
                paramType,
                paramType,
                isInterface = false,
                "writeObject",
                WriteObjectDescriptor
            )
            indirectCalls.addCallOrFallback(
                callContext,
                callPC,
                writeObjectMethod,
                ObjectType.Object.packageName,
                ObjectType.Object,
                "writeObject",
                WriteObjectDescriptor,
                parameters,
                receiver,
                tgt ⇒ typeProvider.expandContext(callContext, tgt, callPC)
            )
        }

        val writeReplaceMethod = project.specialCall(
            paramType,
            paramType,
            isInterface = false,
            "writeReplace",
            WriteObjectDescriptor
        )

        indirectCalls.addCallOrFallback(
            callContext,
            callPC,
            writeReplaceMethod,
            ObjectType.Object.packageName,
            ObjectType.Object,
            "writeReplace",
            WriteObjectDescriptor,
            parameters,
            receiver,
            tgt ⇒ typeProvider.expandContext(callContext, tgt, callPC)
        )
    }

}

/**
 * Analysis handling the specifics of java.io.ObjectInputStream.readObject.
 * This method may instantiate new objects and invoke readObject, readResolve, readExternal or
 * validateObject on them.
 *
 * @author Florian Kuebler
 */
class OISReadObjectAnalysis private[analyses] (
        final val project:                        SomeProject,
        final override implicit val typeProvider: TypeProvider
) extends TACAIBasedAPIBasedAnalysis {

    final val ObjectInputValidationType = ObjectType("java/io/ObjectInputValidation")
    final val ObjectInputType = ObjectType("java/io/ObjectInput")

    final val ReadObjectDescriptor = MethodDescriptor.JustTakes(ObjectInputStreamType)
    final val ReadExternalDescriptor = MethodDescriptor.JustTakes(ObjectInputType)

    final val UnknownParam = Seq(None)

    override val apiMethod: DeclaredMethod = declaredMethods(
        ObjectInputStreamType,
        "",
        ObjectInputStreamType,
        "readObject",
        MethodDescriptor.JustReturnsObject
    )

    override def processNewCaller(
        calleeContext:  ContextType,
        callerContext:  ContextType,
        pc:             Int,
        tac:            TACode[TACMethodParameter, V],
        receiverOption: Option[Expr[V]],
        params:         Seq[Option[Expr[V]]],
        tgtVarOption:   Option[V],
        isDirect:       Boolean
    ): ProperPropertyComputationResult = {
        implicit val stmts: Array[Stmt[V]] = tac.stmts

        val calleesAndCallers = new IndirectCalls()

        if (tgtVarOption.isDefined) {
            handleOISReadObject(
                callerContext, tgtVarOption.get, receiverOption, pc, calleesAndCallers
            )

        } else {
            calleesAndCallers.addIncompleteCallSite(pc)
        }

        Results(calleesAndCallers.partialResults(callerContext))
    }

    private[this] def handleOISReadObject(
        context:           ContextType,
        targetVar:         V,
        inputStream:       Option[Expr[V]],
        pc:                Int,
        calleesAndCallers: IndirectCalls
    )(
        implicit
        stmts: Array[Stmt[V]]
    ): Unit = {
        var foundCast = false
        val parameterList = Seq(inputStream.flatMap(is ⇒ persistentUVar(is.asVar)))
        for { Checkcast(_, _, ElementReferenceType(castType)) ← stmts } {
            foundCast = true

            // for each subtype of the type declared at cast we add calls to the relevant methods
            for {
                t ← ch.allSubtypes(castType.asObjectType, reflexive = true)
                cf ← project.classFile(t) // we ignore cases were no class file exists
                if !cf.isInterfaceDeclaration
                if ch.isSubtypeOf(castType, ObjectType.Serializable)
            } {

                if (ch.isSubtypeOf(castType, ObjectType.Externalizable)) {
                    // call to `readExternal`
                    val readExternal = p.instanceCall(t, t, "readExternal", ReadExternalDescriptor)

                    calleesAndCallers.addCallOrFallback(
                        context,
                        pc,
                        readExternal,
                        ObjectType.Externalizable.packageName,
                        ObjectType.Externalizable,
                        "readExternal",
                        ReadExternalDescriptor,
                        parameterList,
                        None,
                        tgt ⇒ typeProvider.expandContext(context, tgt, pc)
                    )

                    // call to no-arg constructor
                    cf.findMethod("<init>", NoArgsAndReturnVoid) foreach { c ⇒
                        calleesAndCallers.addCall(
                            context,
                            pc,
                            typeProvider.expandContext(context, declaredMethods(c), pc),
                            UnknownParam,
                            None
                        )
                    }
                } else {

                    // call to `readObject`
                    val readObjectMethod =
                        p.specialCall(t, t, isInterface = false, "readObject", ReadObjectDescriptor)
                    calleesAndCallers.addCallOrFallback(
                        context, pc, readObjectMethod,
                        ObjectType.Object.packageName,
                        ObjectType.Object,
                        "readObject",
                        ReadObjectDescriptor,
                        parameterList,
                        None,
                        tgt ⇒ typeProvider.expandContext(context, tgt, pc)
                    )

                    // call to first super no-arg constructor
                    val nonSerializableSuperclass = firstNotSerializableSupertype(t)
                    if (nonSerializableSuperclass.isDefined) {
                        val constructor = p.classFile(nonSerializableSuperclass.get).flatMap { cf ⇒
                            cf.findMethod("<init>", NoArgsAndReturnVoid)
                        }
                        // otherwise an exception will thrown at runtime
                        if (constructor.isDefined) {
                            calleesAndCallers.addCall(
                                context,
                                pc,
                                typeProvider.expandContext(
                                    context, declaredMethods(constructor.get), pc
                                ),
                                UnknownParam,
                                None
                            )
                        }
                    }

                    // for the type to be instantiated, we need to call a constructor of the type t
                    // in order to let the instantiated types be correct. Note, that the JVM would
                    // not call the constructor
                    // Note, that we assume that there is a constructor
                    // Note that we have to do a String comparison since methods with ObjectType
                    // descriptors are not sorted consistently across runs
                    val constructor = cf.constructors.map[(String, Method)] { cf ⇒
                        (cf.descriptor.toJava, cf)
                    }.minBy(t ⇒ t._1)._2

                    calleesAndCallers.addCall(
                        context,
                        pc,
                        typeProvider.expandContext(context, declaredMethods(constructor), pc),
                        UnknownParam,
                        None
                    )
                }

                // call to `readResolve`
                val readResolve =
                    p.specialCall(t, t, isInterface = false, "readResolve", JustReturnsObject)
                calleesAndCallers.addCallOrFallback(
                    context, pc, readResolve,
                    ObjectType.Object.packageName,
                    ObjectType.Object,
                    "readResolve",
                    JustReturnsObject,
                    UnknownParam,
                    None,
                    tgt ⇒ typeProvider.expandContext(context, tgt, pc)
                )

                // call to `validateObject`
                if (ch.isSubtypeOf(t, ObjectInputValidationType)) {
                    val validateObject =
                        p.instanceCall(t, t, "validateObject", JustReturnsObject)
                    calleesAndCallers.addCallOrFallback(
                        context, pc, validateObject,
                        ObjectType.Object.packageName,
                        ObjectType.Object,
                        "validateObject",
                        JustReturnsObject,
                        UnknownParam,
                        None,
                        tgt ⇒ typeProvider.expandContext(context, tgt, pc)
                    )
                }
            }
        }

        if (!foundCast) {
            calleesAndCallers.addIncompleteCallSite(pc)
        }
    }

    @tailrec private[this] def firstNotSerializableSupertype(t: ObjectType): Option[ObjectType] = {
        ch.superclassType(t) match {
            case None ⇒ None
            case Some(superType) ⇒
                if (ch.isSubtypeOf(superType, ObjectType.Serializable)) {
                    firstNotSerializableSupertype(superType)
                } else {
                    Some(superType)
                }
        }
    }
}

/**
 * Handles the effect of serialization to the call graph.
 * As an example models the invocation of constructors when `readObject` is called, if there is a
 * cast afterwards.
 *
 * @author Florian Kuebler
 * @author Dominik Helm
 */
class SerializationRelatedCallsAnalysis private[analyses] (
        final val project: SomeProject, typeProvider: TypeProvider
) extends FPCFAnalysis {

    def process(p: SomeProject): PropertyComputationResult = {
        val readObjectAnalysis = new OISReadObjectAnalysis(project, typeProvider)
        val readObjectResult = readObjectAnalysis.registerAPIMethod()
        val writeObjectAnalysis = new OOSWriteObjectAnalysis(project, typeProvider)
        val writeObjectResult = writeObjectAnalysis.registerAPIMethod()
        Results(readObjectResult, writeObjectResult)
    }
}

object SerializationRelatedCallsAnalysisScheduler extends BasicFPCFEagerAnalysisScheduler {

    override def requiredProjectInformation: ProjectInformationKeys =
        Seq(DeclaredMethodsKey) ++ CallGraphKey.typeProvider.requiredProjectInformationKeys

    override def uses: Set[PropertyBounds] =
        PropertyBounds.ubs(Callers, Callees, TACAI) ++ CallGraphKey.typeProvider.usedPropertyKinds

    override def derivesCollaboratively: Set[PropertyBounds] = PropertyBounds.ubs(Callers, Callees)

    override def derivesEagerly: Set[PropertyBounds] = Set.empty

    override def start(p: SomeProject, ps: PropertyStore, i: Null): FPCFAnalysis = {
        val analysis = new SerializationRelatedCallsAnalysis(p, CallGraphKey.typeProvider)
        ps.scheduleEagerComputationForEntity(p)(analysis.process)
        analysis
    }
}

