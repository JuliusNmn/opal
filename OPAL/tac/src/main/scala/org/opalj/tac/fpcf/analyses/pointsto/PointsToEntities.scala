/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package pointsto

import org.opalj.br.DeclaredMethod
import org.opalj.tac.common.DefinitionSite

case class ArrayEntity[ElementType](element: ElementType)
case class MethodExceptions(dm: DeclaredMethod)
case class CallExceptions(defSite: DefinitionSite)

object UnsafeFakeField