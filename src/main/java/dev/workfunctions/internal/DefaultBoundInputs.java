package dev.workfunctions.internal;

import dev.workfunctions.BoundInputs;
import dev.workfunctions.Selector;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.Map;

final class DefaultBoundInputs<I> implements BoundInputs<I> {

    private final InputSchema schema;
    private final Map<String, Object> bindings;
    private final ProviderFactory providers;
    private final ObjectFactory objects;

    DefaultBoundInputs(InputSchema schema, Map<String, Object> bindings, ProviderFactory providers, ObjectFactory objects) {
        this.schema = schema;
        this.bindings = bindings;
        this.providers = providers;
        this.objects = objects;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Provider<T> provider(Selector<I, T> input) {
        Object source = bindings.get(SelectorResolver.resolve(input, schema).name());
        if (source instanceof Provider<?> provider) {
            return (Provider<T>) provider;
        }
        return providers.provider(() -> (T) source);
    }

    @Override
    public FileCollection files(Selector<I, ?> input) {
        InputSchema.Component component = SelectorResolver.resolve(input, schema);
        if (component.kind() == InputSchema.Kind.VALUE) {
            throw new InvalidUserDataException("Input '" + component.name() + "' of " + schema.type().getName() + " is not a file input.");
        }
        ConfigurableFileCollection files = objects.fileCollection();
        Object source = bindings.get(component.name());
        if (source != null) {
            files.from(source);
        }
        return files;
    }
}
