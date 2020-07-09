package org.opalj.fpcf.fixtures.immutability.classes.generic;

import org.opalj.fpcf.properties.class_immutability.DependentImmutableClassAnnotation;
import org.opalj.fpcf.properties.field_immutability.DeepImmutableFieldAnnotation;
import org.opalj.fpcf.properties.field_immutability.DependentImmutableFieldAnnotation;
import org.opalj.fpcf.properties.reference_immutability.ImmutableReferenceEscapesAnnotation;
import org.opalj.fpcf.properties.type_immutability.DependentImmutableTypeAnnotation;

@DependentImmutableTypeAnnotation("")
@DependentImmutableClassAnnotation("")
public final class GenericAndDeepImmutableFields<T1, T2> {

    @DependentImmutableFieldAnnotation(value = "T1", genericString = "T1")
    @ImmutableReferenceEscapesAnnotation("")
    private T1 t1;

    @DependentImmutableFieldAnnotation(value = "T2", genericString = "T2")
    @ImmutableReferenceEscapesAnnotation("")
    private T2 t2;

    @DeepImmutableFieldAnnotation("")
    @ImmutableReferenceEscapesAnnotation("")
    private FinalEmptyClass fec;

    GenericAndDeepImmutableFields(T1 t1, T2 t2, FinalEmptyClass fec){
        this.t1 = t1;
        this.t2 = t2;
        this.fec = fec;
    }
}