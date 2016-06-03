/* License (BSD Style License):
*  Copyright (c) 2009, 2011
*  Software Technology Group
*  Department of Computer Science
*  Technische Universität Darmstadt
*  All rights reserved.
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions are met:
*
*  - Redistributions of source code must retain the above copyright notice,
*    this list of conditions and the following disclaimer.
*  - Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*  - Neither the name of the Software Technology Group or Technische 
*    Universität Darmstadt nor the names of its contributors may be used to 
*    endorse or promote products derived from this software without specific 
*    prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
*  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
*  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
*  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
*  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
*  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
*  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
*  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
*  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
*  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
*  POSSIBILITY OF SUCH DAMAGE.
*/
package org.opalj
package da

import scala.xml.Node

/**
 * @author Michael Eichberg
 * @author Wael Alkhatib
 * @author Isbel Isbel
 * @author Noorulla Sharief
 */
trait TypeAnnotationPath {

    def toXHTML(implicit cp: Constant_Pool): Node
}

case object TypeAnnotationDirectlyOnType extends TypeAnnotationPath {
    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_path">DirectlyOnType</span>
    }
}

trait TypeAnnotationPathElement {
    def toXHTML(implicit cp: Constant_Pool): Node
}

case class TypeAnnotationPathElements(
        path: IndexedSeq[TypeAnnotationPathElement]
) extends TypeAnnotationPath {

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_path">Path:{ path.map(_.toXHTML(cp)) }]</span>
    }
}

/**
 * The `type_path_kind` was `0` (and the type_argument_index was also `0`).
 */
case object TypeAnnotationDeeperInArrayType extends TypeAnnotationPathElement {

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_path">DeeperInArrayType</span>
    }
}

case object TypeAnnotationDeeperInNestedType extends TypeAnnotationPathElement {

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_path">DeeperInNestedType</span>
    }
}

case object TypeAnnotationOnBoundOfWildcardType extends TypeAnnotationPathElement {

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_path">OnBoundOfWildcardType</span>
    }
}

case class TypeAnnotationOnTypeArgument(
        type_argument_index: Int
) extends TypeAnnotationPathElement {

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_path">OnTypeArgument: { type_argument_index }</span>
    }
}

