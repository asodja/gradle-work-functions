package dev.workfunctions;

import org.gradle.api.Task;
import org.gradle.api.provider.Provider;

/**
 * Describes a task of type {@code T} as work with the output {@code O}.
 *
 * <p>The task is registered and configured elsewhere. {@link WorkRegistry#named} finds it by name.
 * Implementations must be stateless. Gradle creates them and may inject services.</p>
 */
public interface TaskBackedWork<O, T extends Task> extends Work<O> {

    /**
     * Returns the output of the task. It must come from an output property of the task,
     * so that it carries the dependency on the task.
     */
    Provider<O> output(T task);
}
