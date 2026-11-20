package dev.workfunctions.internal;

/**
 * Binding by accessor name, for the Kotlin property-reference extensions.
 */
public interface InputsInternal {
    void bindAccessor(String accessorName, Object source);
}
