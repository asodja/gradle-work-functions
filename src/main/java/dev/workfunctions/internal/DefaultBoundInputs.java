package dev.workfunctions.internal;

import dev.workfunctions.BoundInputs;
import dev.workfunctions.Selector;
import org.gradle.api.Action;
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
    public <T> void value(Selector<I, T> input, Action<? super Provider<T>> action) {
        String name = SelectorResolver.resolve(input, schema).name();
        if (!bindings.containsKey(name)) {
            return;
        }
        Object source = bindings.get(name);
        action.execute(source instanceof Provider<?> provider ? (Provider<T>) provider : providers.provider(() -> (T) source));
    }

    @Override
    public void files(Selector<I, ?> input, Action<? super FileCollection> action) {
        InputSchema.Component component = SelectorResolver.resolve(input, schema);
        if (component.kind() == InputSchema.Kind.VALUE) {
            throw new InvalidUserDataException("Input '" + component.name() + "' of " + schema.type().getName() + " is not a file input.");
        }
        if (!bindings.containsKey(component.name())) {
            return;
        }
        ConfigurableFileCollection files = objects.fileCollection();
        Object source = bindings.get(component.name());
        if (source != null) {
            files.from(source);
        }
        action.execute(files);
    }
}
