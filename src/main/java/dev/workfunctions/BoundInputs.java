package dev.workfunctions;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

/**
 * The inputs that a {@code named} block has bound, for a {@link TaskBackedWork} to pass to its task.
 * An action runs only when its input is bound.
 */
public interface BoundInputs<I> {

    /**
     * Passes the bound value, as a provider, so that it stays lazy.
     */
    <T> void value(Selector<I, T> input, Action<? super Provider<T>> action);

    /**
     * Passes the bound files. The collection keeps the task dependencies of what was bound.
     */
    void files(Selector<I, ?> input, Action<? super FileCollection> action);
}
