package dev.workfunctions;

import org.gradle.api.provider.Provider;

public interface InputTarget<T> {
    void to(T value);

    void to(Provider<? extends T> source);
}
