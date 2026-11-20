package dev.workfunctions;

import org.gradle.api.provider.Provider;

/**
 * Binds the inputs of one work function. Nothing is read while binding.
 */
public interface Inputs<I> {
    /**
     * Binds an input to a provider of exactly the input type.
     */
    <T> void set(Selector<I, T> input, Provider<T> source);

    /**
     * Binds an input to a constant, a file, a file collection, or a provider of a subtype.
     */
    <T> InputTarget<T> bind(Selector<I, T> input);
}
