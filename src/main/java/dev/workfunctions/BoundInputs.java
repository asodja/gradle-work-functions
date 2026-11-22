package dev.workfunctions;

import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

/**
 * The inputs of task-backed work, as they were bound. Values stay lazy.
 */
public interface BoundInputs<I> {

    /**
     * Returns the bound value of an input. The provider has no value when the input is not bound.
     */
    <T> Provider<T> provider(Selector<I, T> input);

    /**
     * Returns the bound files of a file input. The collection is empty when the input is not bound.
     */
    FileCollection files(Selector<I, ?> input);
}
