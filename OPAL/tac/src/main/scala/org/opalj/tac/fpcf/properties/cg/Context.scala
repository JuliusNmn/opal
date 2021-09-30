/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package properties
package cg

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock

import scala.collection.mutable.OpenHashMap

import org.opalj.br.DeclaredMethod

/**
 * Provides the context in which a method was invoked or an object was allocated.
 *
 * @author Dominik Helm
 */
trait Context {
    val hasContext: Boolean = true

    /** The method itself */
    def method: DeclaredMethod

    /** An identifier for the context */
    def id: Int
}

/**
 *  Represents unknown contexts.
 */
case object NoContext extends Context {
    override val hasContext: Boolean = false

    override def method: DeclaredMethod = throw new UnsupportedOperationException()

    val id = -1
}

/**
 * A simple context that provides the bare minumum for context-insensitive analyses.
 */
case class SimpleContext(method: DeclaredMethod) extends Context {
    override def id = method.id
}

/**
 * A context that includes a call string
 */
class CallStringContext private (
        val id:         Int,
        val method:     DeclaredMethod,
        val callString: List[(DeclaredMethod, Int)]
) extends Context

object CallStringContext {

    @volatile private var id2Context = new Array[CallStringContext](32768)
    private val context2id = new OpenHashMap[(DeclaredMethod, List[(DeclaredMethod, Int)]), CallStringContext]()

    private val nextId = new AtomicInteger(1)
    private val rwLock = new ReentrantReadWriteLock();

    def apply(id: Int): CallStringContext = {
        id2Context(id)
    }

    def apply(
        method:     DeclaredMethod,
        callString: List[(DeclaredMethod, Int)]
    ): CallStringContext = {
        val key = (method, callString)

        val readLock = rwLock.readLock()
        readLock.lock()
        try {
            val contextO = context2id.get(key)
            if (contextO.isDefined) {
                return contextO.get;
            }
        } finally {
            readLock.unlock()
        }

        val writeLock = rwLock.writeLock()
        writeLock.lock()
        try {
            val contextO = context2id.get(key)
            if (contextO.isDefined) {
                return contextO.get;
            }

            val context = new CallStringContext(nextId.getAndIncrement(), method, callString)
            context2id.put(key, context)
            val curMap = id2Context
            if (context.id < curMap.length) {
                curMap(context.id) = context
            } else {
                val newMap = java.util.Arrays.copyOf(curMap, curMap.length * 2)
                newMap(context.id) = context
                id2Context = newMap
            }
            context
        } finally {
            writeLock.unlock()
        }
    }
}