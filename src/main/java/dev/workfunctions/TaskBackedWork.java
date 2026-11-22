package dev.workfunctions;

import org.gradle.api.Task;
import org.gradle.api.provider.Provider;

/**
 * Work that an existing task type does, such as {@code JavaCompile}.
 *
 * <p>It has no {@code run}. It configures the task from the inputs and the assigned output location.
 * The inputs of the task decide caching, so every input of {@code I} must be passed to the task.</p>
 */
public interface TaskBackedWork<I, O, T extends Task> {
    void configure(T task, BoundInputs<I> input, Provider<O> output);
}
