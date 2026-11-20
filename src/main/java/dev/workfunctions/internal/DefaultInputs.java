package dev.workfunctions.internal;

import dev.workfunctions.InputTarget;
import dev.workfunctions.Inputs;
import dev.workfunctions.Selector;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

final class DefaultInputs<I> implements Inputs<I>, InputsInternal {

    private final String workName;
    private final InputSchema schema;
    private final Map<String, Object> bindings = new LinkedHashMap<>();

    DefaultInputs(String workName, InputSchema schema) {
        this.workName = workName;
        this.schema = schema;
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
        InputSchema.Component component = schema.byAccessor(accessorName);
        if (component == null) {
            throw new InvalidUserDataException(accessorName + " is not an input of " + schema.type().getName() + ".");
        }
        bind(component, source);
    }

    private void bind(InputSchema.Component component, Object source) {
        if (source == null) {
            bindings.remove(component.name());
            return;
        }
        if (!(source instanceof Provider) && !(source instanceof FileCollection)) {
            Values.checkType(workName, component, source);
        }
        bindings.put(component.name(), source);
    }
}
