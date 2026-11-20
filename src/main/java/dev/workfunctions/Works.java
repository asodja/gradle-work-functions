package dev.workfunctions;

import org.gradle.api.Action;
import org.gradle.api.provider.Provider;

public interface Works {
    /**
     * Registers work and returns its output. Passing the output to another work's input,
     * or to any Gradle API that accepts a provider, adds the dependency.
     */
    <I, O> Provider<O> work(String name, Class<? extends WorkFunction<I, O>> type, Action<? super Inputs<I>> configuration);

    /**
     * Makes the lifecycle task {@code name} produce the given outputs. Creates the task if needed.
     */
    void lifecycle(String name, Provider<?>... outputs);
}
