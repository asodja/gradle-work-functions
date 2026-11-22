package dev.workfunctions;

import java.io.Serializable;

/**
 * A method reference to one input, such as {@code In::version}.
 *
 * <p>The method is generic on purpose: Java accepts a method reference for a generic
 * functional method, but rejects a lambda. The type parameter is unused, because IntelliJ
 * rejects the method reference when the parameter type is a type variable.</p>
 */
public interface Selector<I, T> extends Serializable {
    <X> T get(I input);
}
