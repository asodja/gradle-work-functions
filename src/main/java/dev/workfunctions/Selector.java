package dev.workfunctions;

import java.io.Serializable;

/**
 * A method reference to one input, such as {@code In::version}.
 *
 * <p>The method is generic on purpose: Java accepts a method reference for a generic
 * functional method, but rejects a lambda.</p>
 */
public interface Selector<I, T> extends Serializable {
    <S extends I> T get(S input);
}
