package dev.workfunctions;

/**
 * Binds the inputs of one work function. Nothing is read while binding.
 */
public interface Inputs<I> {
    /**
     * Selects the input to bind, with a method reference such as {@code In::version}.
     */
    <T> InputTarget<T> bind(Selector<I, T> input);
}
