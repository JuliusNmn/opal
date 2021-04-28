/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.fixtures.immutability.fields;

import org.opalj.br.fpcf.analyses.L0FieldImmutabilityAnalysis;
import org.opalj.br.fpcf.analyses.L0TypeImmutabilityAnalysis;
import org.opalj.fpcf.properties.immutability.classes.DeepImmutableClass;
import org.opalj.fpcf.properties.immutability.classes.ShallowImmutableClass;
import org.opalj.fpcf.properties.immutability.fields.DeepImmutableField;
import org.opalj.fpcf.properties.immutability.fields.MutableField;
import org.opalj.fpcf.properties.immutability.fields.ShallowImmutableField;
import org.opalj.fpcf.properties.immutability.references.ImmutableFieldReference;
import org.opalj.fpcf.properties.immutability.types.MutableType;
import org.opalj.tac.fpcf.analyses.L1FieldImmutabilityAnalysis;
import org.opalj.tac.fpcf.analyses.L2FieldImmutabilityAnalysis;
import org.opalj.tac.fpcf.analyses.immutability.L1ClassImmutabilityAnalysis;
import org.opalj.tac.fpcf.analyses.immutability.L1TypeImmutabilityAnalysis;
import org.opalj.tac.fpcf.analyses.immutability.L3FieldImmutabilityAnalysis;

@MutableType(value = "class is extensible",
        analyses = {L0TypeImmutabilityAnalysis.class, L1TypeImmutabilityAnalysis.class})
@DeepImmutableClass(value = "Because it has only Deep Immutable Field Types",
        analyses = L1ClassImmutabilityAnalysis.class)
public class ClassWithdirectlySetDeepImmutableField {

    @DeepImmutableField(value = "Immutable Reference and Immutable Field Type",
            analyses = L3FieldImmutabilityAnalysis.class)
    @ShallowImmutableField(value = "can not handle transitive immutability",
            analyses = {L1FieldImmutabilityAnalysis.class, L2FieldImmutabilityAnalysis.class})
    @MutableField(value = "can not handle effective immutability",
            analyses = L0FieldImmutabilityAnalysis.class)
    @ImmutableFieldReference("effective immutable field")
    private FinalEmptyClass name = new FinalEmptyClass();
}