/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package hermes
package queries

import org.opalj.collection.immutable.Chain
import org.opalj.br.MethodDescriptor.JustTakes
import org.opalj.br.MethodDescriptor.NoArgsAndReturnVoid
import org.opalj.br.ObjectType
import org.opalj.hermes.queries.util.APIClassExtension
import org.opalj.hermes.queries.util.APIFeature
import org.opalj.hermes.queries.util.APIFeatureGroup
import org.opalj.hermes.queries.util.APIFeatureQuery
import org.opalj.hermes.queries.util.InstanceAPIMethod
import org.opalj.hermes.queries.util.StaticAPIMethod

/**
 * Extracts calls to the `java.lang.ClassLoader` API.
 *
 * @author Michael Reif
 */
class ClassLoaderAPIUsage(implicit hermes: HermesConfig) extends APIFeatureQuery {

    override val apiFeatures: Chain[APIFeature] = {

        val ClassLoader = ObjectType("java/lang/ClassLoader")

        Chain(
            APIClassExtension("custom ClassLoader implementation", ClassLoader),

            APIFeatureGroup(
                Chain(
                    StaticAPIMethod(ClassLoader, "getSystemClassLoader"),
                    InstanceAPIMethod(ClassLoader, "<init>", NoArgsAndReturnVoid)
                ),
                "Retrieving the SystemClassLoader"
            ),

            APIFeatureGroup(
                Chain(
                    InstanceAPIMethod(ClassLoader, "<init>", JustTakes(ClassLoader)),
                    InstanceAPIMethod(ObjectType.Class, "getClassLoader")
                ),
                "Retrieving some ClassLoader"
            ),

            APIFeatureGroup(
                Chain(
                    InstanceAPIMethod(ClassLoader, "defineClass"),
                    InstanceAPIMethod(ClassLoader, "definePackage")
                ),
                "define new classes/packages"
            ),

            APIFeatureGroup(
                Chain(
                    InstanceAPIMethod(ClassLoader, "getResource"),
                    InstanceAPIMethod(ClassLoader, "getResourceAsStream"),
                    InstanceAPIMethod(ClassLoader, "getResources"),
                    InstanceAPIMethod(ClassLoader, "getSystemResource"),
                    InstanceAPIMethod(ClassLoader, "getSystemResourceAsStream"),
                    InstanceAPIMethod(ClassLoader, "getSystemResources")
                ),
                "accessing resources"
            )
        )
    }
}
