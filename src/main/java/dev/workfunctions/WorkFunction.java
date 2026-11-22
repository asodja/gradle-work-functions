package dev.workfunctions;

/**
 * A unit of work: reads the input {@code I} and writes the output {@code O}.
 *
 * <p>{@code I} is a record or an interface whose components are the inputs.
 * {@code O} is a {@code Directory}, a {@code RegularFile}, or a record of those.
 * Implementations must be stateless. Gradle creates them and may inject services.</p>
 */
public interface WorkFunction<I, O> extends Work<O> {
    void run(I input, O output) throws Exception;
}
