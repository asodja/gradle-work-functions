package dev.workfunctions.internal;

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;

import java.util.function.BiFunction;

/**
 * Binding by accessor name, for the Kotlin property-reference extensions.
 */
public interface InputsInternal {
    void bindAccessor(String accessorName, Object source);

    void updateAccessor(String accessorName, Transformer<?, ?> transform);

    void updateAccessor(String accessorName, Provider<?> other, BiFunction<?, ?, ?> combine);

    void rebindAccessor(String accessorName, Transformer<?, ?> transform);
}
