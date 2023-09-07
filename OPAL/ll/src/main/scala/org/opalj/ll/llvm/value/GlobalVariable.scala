/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package ll
package llvm
package value

import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM.LLVMGetInitializer

/**
 * Represents a LLVM global variable.
 *
 * @param ref reference to a LLVM global variable
 *
 * @author Marc Clement
 */
case class GlobalVariable(ref: LLVMValueRef) extends Value(ref: LLVMValueRef) {
    def initializer: Value = Value(LLVMGetInitializer(ref)).get
}