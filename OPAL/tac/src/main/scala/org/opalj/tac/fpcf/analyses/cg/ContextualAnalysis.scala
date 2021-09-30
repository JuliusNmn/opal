/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package cg

import org.opalj.tac.fpcf.properties.cg.Context

trait ContextualAnalysis {
    type ContextType <: Context
}
