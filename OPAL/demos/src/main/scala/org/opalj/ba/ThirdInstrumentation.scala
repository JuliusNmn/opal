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
package ba

import java.io.ByteArrayInputStream
import java.io.File
import java.util.ArrayList
import java.util.Arrays

import org.opalj.log.LogContext
import org.opalj.log.GlobalLogContext
import org.opalj.io.writeAndOpen
import org.opalj.ai.BaseAI
import org.opalj.ai.domain.l0.TypeCheckingDomain
import org.opalj.da.ClassFileReader.ClassFile
import org.opalj.bc.Assembler
import org.opalj.br.ObjectType
import org.opalj.br.MethodDescriptor.JustTakes
import org.opalj.br.analyses.Project
import org.opalj.br.ClassFileRepository
import org.opalj.br.MethodDescriptor
import org.opalj.br.cfg.CFGFactory
import org.opalj.br.instructions.INVOKEVIRTUAL
import org.opalj.br.instructions.DUP
import org.opalj.br.instructions.POP
import org.opalj.br.instructions.GETSTATIC
import org.opalj.br.instructions.SWAP
import org.opalj.br.instructions.RETURN
import org.opalj.br.instructions.IRETURN
import org.opalj.br.instructions.ATHROW
import org.opalj.br.instructions.GOTO
import org.opalj.br.instructions.LoadString
import org.opalj.br.instructions.IFGT
import org.opalj.br.instructions.NEW
import org.opalj.br.instructions.INVOKESPECIAL

/**
 * Demonstrates how to perform an instrumentation where we need more information about the code
 * (here, the (static) type of a value given to a method.
 *
 * @author Michael Eichberg
 */
object ThirdInstrumentation extends App {

    val PrintStreamType = ObjectType("java/io/PrintStream")
    val SystemType = ObjectType("java/lang/System")
    val CollectionType = ObjectType("java/util/Collection")
    val RuntimeExceptionType = ObjectType("java/lang/RuntimeException")
    val PrintlnDescriptor = JustTakes(ObjectType.Object)

    val TheType = ObjectType("org/opalj/ba/SimpleInstrumentationDemo")

    // Let's load the class ( we need the RT Jar to compute appropriate supertypes for
    // local variables and stack values during instrumentation.
    val f = new File(this.getClass.getResource("SimpleInstrumentationDemo.class").getFile)
    val p = Project(f.getParentFile, org.opalj.bytecode.RTJar)
    implicit val classHierarchy = p.classHierarchy // STRICTLY REQUIRED WHEN A StackMapTable NEEDS TO BE COMPUTED!
    val cf = p.classFile(TheType).get
    // let's transform the methods
    val newMethods = for (m ← cf.methods) yield {
        m.body match {
            case None ⇒
                m.copy() // these are native and abstract methods

            case Some(code) ⇒
                val cfg = CFGFactory(code, classHierarchy)
                val lCode = LabeledCode(code)
                var removeDeadCode = false
                if (m.name == "killMe1") {
                    for {
                        (pc, LoadString("kill me")) ← code // the search can be done either based on the original code or the lcode
                    } {
                        val stackDepth = code.stackDepthAt(pc, cfg)
                        val cleanStackAndReturn = new Array[CodeElement[AnyRef]](stackDepth + 1)
                        Arrays.fill(
                            cleanStackAndReturn.asInstanceOf[Array[Object]],
                            0, stackDepth,
                            InstructionElement(POP)
                        )
                        cleanStackAndReturn(stackDepth) = RETURN
                        lCode.insert(pc, InsertionPosition.After, cleanStackAndReturn)
                    }
                    removeDeadCode = true
                } else if (m.name == "killMe2") {
                    for {
                        (pc, LoadString("kill me")) ← code
                    } {
                        // NOTE: when we throw an exception, we don't have to take of the
                        //       size of the stack!
                        lCode.insert(pc, InsertionPosition.After, Seq(
                            NEW(RuntimeExceptionType),
                            DUP,
                            INVOKESPECIAL(RuntimeExceptionType, false, "<init>", MethodDescriptor.NoArgsAndReturnVoid),
                            ATHROW
                        ))
                    }
                    removeDeadCode = true
                }

                // whenever a method is called, we output its signature
                lCode.insert(
                    // Note, we generally don't want to use Before, here!
                    // If we would use "Before" and would have a method like:
                    // do {
                    // } while(...)
                    // It could happen that the output would be printed each time the loop
                    // is evaluated.
                    0, InsertionPosition.At,
                    Seq(
                        GETSTATIC(SystemType, "out", PrintStreamType),
                        LoadString(m.toJava),
                        INVOKEVIRTUAL(PrintStreamType, "println", JustTakes(ObjectType.String))
                    )
                )

                // let's search all "println" calls where the parameter has a specific
                // type (which is statically known)
                lazy val aiResult = BaseAI(m, new TypeCheckingDomain(p, m))

                for {
                    (pc, GETSTATIC(SystemType, "out", _)) ← code
                } {
                    lCode.replace(pc, Seq(GETSTATIC(SystemType, "err", PrintStreamType)))
                }

                for {
                    (pc, INVOKEVIRTUAL(_, "println", PrintlnDescriptor)) ← code
                    if aiResult.operandsArray(pc).head.asDomainReferenceValue.isValueSubtypeOf(CollectionType).isYes
                } {
                    lCode.insert(
                        pc, InsertionPosition.Before,
                        Seq(
                            DUP,
                            GETSTATIC(SystemType, "out", PrintStreamType),
                            SWAP,
                            INVOKEVIRTUAL(PrintStreamType, "println", JustTakes(ObjectType.Object))
                        )
                    )
                }

                // Let's write out whether a value is positive (0...Int.MaxValue) or negative;
                // i.e., let's see how we add conditional logic.
                for ((pc, IRETURN) ← code) {
                    val gtTarget = Symbol(pc+":>")
                    val printlnTarget = Symbol(pc+":println")
                    lCode.insert(
                        pc, InsertionPosition.Before,
                        Seq(
                            DUP, // duplicate the value
                            GETSTATIC(SystemType, "out", PrintStreamType), // receiver
                            SWAP, // the int value is on top now..
                            IFGT(gtTarget),
                            // value is less than 0
                            LoadString("negative"), // top is the parameter, receiver is 2nd top most
                            GOTO(printlnTarget),
                            gtTarget, // this Symbol has to be unique across all instrumentations of this method
                            LoadString("positive"),
                            printlnTarget,
                            INVOKEVIRTUAL(PrintStreamType, "println", JustTakes(ObjectType.String))
                        )
                    )
                }
                if (removeDeadCode) lCode.removedDeadCode()
                val (newCode, _) = lCode.result(cf.version, m)
                m.copy(body = Some(newCode))

        }
    }
    val newCF = cf.copy(methods = newMethods)
    val newRawCF = Assembler(toDA(newCF))

    //
    // THE FOLLOWING IS NOT RELATED TO BYTECODE MANIPULATION, BUT SHOWS ASPECTS OF OPAL WHICH ARE
    // HELPFUL WHEN DOING BYTECODE INSTRUMENTATION.
    //

    // Let's see the old file...
    val oldCFHTML = ClassFile(() ⇒ p.source(TheType).get.openConnection().getInputStream).head.toXHTML(None)
    println("original: "+writeAndOpen(oldCFHTML, "SimpleInstrumentationDemo", ".html"))

    // Let's see the new file...
    val newCFHTML = ClassFile(() ⇒ new ByteArrayInputStream(newRawCF)).head.toXHTML(None)
    val newCFFile = writeAndOpen(newCFHTML, "NewSimpleInstrumentationDemo", ".html")
    println("instrumented: "+newCFFile)

    // Let's test that the new class does what it is expected to do... (we execute the
    // instrumented method)
    val cfr = new ClassFileRepository {
        implicit def logContext: LogContext = GlobalLogContext
        def classFile(objectType: ObjectType): Option[br.ClassFile] = {
            if (TheType == objectType)
                Some(newCF)
            else
                p.classFile(objectType)
        }
    }
    val cl = new ProjectBasedInMemoryClassLoader(cfr)
    val newClass = cl.findClass(TheType.toJava)
    val instance = newClass.newInstance()
    newClass.getMethod("callsToString").invoke(instance)
    newClass.getMethod("playingWithTypes", classOf[Object]).invoke(instance, new ArrayList[AnyRef]())
    newClass.getMethod("returnsValue", classOf[Int]).invoke(instance, new Integer(0))
    newClass.getMethod("returnsValue", classOf[Int]).invoke(instance, new Integer(1))
    newClass.getMethod("endlessLoop").invoke(instance)

    println("expected: org.opalj.ba.SimpleInstrumentationDemo{ public void killMe1() }")
    print("actual:   "); newClass.getMethod("killMe1").invoke(instance)

    newClass.getMethod("killMe2", classOf[Boolean]).invoke(instance, java.lang.Boolean.FALSE)
    try {
        newClass.getMethod("killMe2", classOf[Boolean]).invoke(instance, java.lang.Boolean.TRUE)
    } catch {
        case ite: java.lang.reflect.InvocationTargetException ⇒
            if (!ite.getCause.isInstanceOf[RuntimeException]) {
                Console.err.println("Big Bug!")
            } else {
                Console.out.println("Dead code successfully removedt!")
            }
    }
}