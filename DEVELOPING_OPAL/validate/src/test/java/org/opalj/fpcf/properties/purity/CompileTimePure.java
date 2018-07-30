/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.properties.purity;

import org.opalj.fpcf.FPCFAnalysis;
import org.opalj.fpcf.analyses.L0PurityAnalysis;
import org.opalj.fpcf.analyses.purity.L1PurityAnalysis;
import org.opalj.fpcf.analyses.purity.L2PurityAnalysis;
import org.opalj.fpcf.properties.PropertyValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to state that the annotated method is compile time pure.
 *
 * @author Dominik Helm
 */
@PropertyValidator(key = "Purity", validator = CompileTimePureMatcher.class)
@Documented
@Retention(RetentionPolicy.CLASS)
public @interface CompileTimePure {

    /**
     * A short reasoning of this property.
     */
    String value(); // default = "N/A";

    Class<? extends FPCFAnalysis>[] analyses() default { L2PurityAnalysis.class };

    EP[] eps() default {};

    boolean negate() default false;
}
