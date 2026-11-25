package dev.workfunctions.internal;

import dev.workfunctions.InputTarget;
import dev.workfunctions.Inputs;
import dev.workfunctions.Selector;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

final class DefaultInputs<I> implements Inputs<I>, InputsInternal {

    private final String workName;
    private final InputSchema schema;
    private final ProviderFactory providers;
    private final Map<String, Object> bindings = new LinkedHashMap<>();

    DefaultInputs(String workName, InputSchema schema, ProviderFactory providers) {
        this.workName = workName;
        this.schema = schema;
        this.providers = providers;
    }

    Map<String, Object> bindings() {
        return bindings;
    }

    @Override
    public <T> InputTarget<T> bind(Selector<I, T> input) {
        return new InputTarget<>(this, SelectorResolver.resolve(input, schema).accessor().getName());
    }

    @Override
    public void bindAccessor(String accessorName, Object source) {
        bind(component(accessorName), source);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void updateAccessor(String accessorName, Transformer<?, ?> transform) {
        InputSchema.Component component = component(accessorName);
        Object current = boundSource(component);
        if (current instanceof FileCollection) {
            // A file collection is lazy itself, so the transform runs now and keeps the result a file collection.
            bind(component, ((Transformer) transform).transform(current));
        } else {
            bindings.put(component.name(), asProvider(current).map((Transformer) transform));
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void updateAccessor(String accessorName, Provider<?> other, BiFunction<?, ?, ?> combine) {
        InputSchema.Component component = component(accessorName);
        bindings.put(component.name(), asProvider(boundSource(component)).zip((Provider) other, (BiFunction) combine));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void rebindAccessor(String accessorName, Transformer<?, ?> transform) {
        InputSchema.Component component = component(accessorName);
        Object source = ((Transformer) transform).transform(asProvider(bindings.get(component.name())));
        if (source == null) {
            throw new InvalidUserDataException("Rebinding input '" + component.name() + "' of work '" + workName + "' returned null. Return a provider.");
        }
        bindings.put(component.name(), source);
    }

    private InputSchema.Component component(String accessorName) {
        InputSchema.Component component = schema.byAccessor(accessorName);
        if (component == null) {
            throw new InvalidUserDataException(accessorName + " is not an input of " + schema.type().getName() + ".");
        }
        return component;
    }

    private Object boundSource(InputSchema.Component component) {
        Object source = bindings.get(component.name());
        if (source == null) {
            throw new InvalidUserDataException("Input '" + component.name() + "' of work '" + workName + "' is not bound, so there is no value to update. Bind it first.");
        }
        return source;
    }

    private Provider<?> asProvider(Object source) {
        if (source instanceof Provider<?> provider) {
            return provider;
        }
        if (source instanceof FileCollection files) {
            // The elements provider knows the tasks that build the files. A provider of a constant would lose them.
            return files.getElements().map(new Constant(files));
        }
        return providers.provider(new Constant(source));
    }

    private void bind(InputSchema.Component component, Object source) {
        if (source == null) {
            // Kept as an entry, so that a later block can clear an earlier binding.
            bindings.put(component.name(), null);
            return;
        }
        if (!(source instanceof Provider) && !(source instanceof FileCollection)) {
            Values.checkType(workName, component, source);
        }
        bindings.put(component.name(), source);
    }

    // A named class, not a lambda, so the configuration cache can store it. A null value makes the provider absent.
    private record Constant(Object value) implements Callable<Object>, Transformer<Object, Object> {
        @Override
        public Object call() {
            return value;
        }

        @Override
        public Object transform(Object ignored) {
            return value;
        }
    }
}
