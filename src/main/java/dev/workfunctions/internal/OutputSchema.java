package dev.workfunctions.internal;

import dev.workfunctions.OutputName;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

final class OutputSchema {

    record Output(String name, boolean directory, String fileName) {
    }

    private final Class<?> type;
    private final List<Output> outputs;

    private OutputSchema(Class<?> type, List<Output> outputs) {
        this.type = type;
        this.outputs = outputs;
    }

    static OutputSchema of(Class<?> type, Class<?> functionType, String workName) {
        List<Output> outputs = new ArrayList<>();
        if (type == Directory.class || type == RegularFile.class) {
            OutputName name = functionType.getAnnotation(OutputName.class);
            outputs.add(new Output("output", type == Directory.class, name == null ? workName : name.value()));
        } else if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                Class<?> componentType = component.getType();
                if (componentType != Directory.class && componentType != RegularFile.class) {
                    throw new InvalidUserDataException("Output '" + component.getName() + "' of " + type.getName() + " must be a Directory or a RegularFile.");
                }
                OutputName name = component.getAnnotation(OutputName.class);
                outputs.add(new Output(component.getName(), componentType == Directory.class, name == null ? component.getName() : name.value()));
            }
        } else {
            throw new InvalidUserDataException("Output type " + type.getName() + " must be a Directory, a RegularFile, or a record of those.");
        }
        return new OutputSchema(type, outputs);
    }

    boolean isSingle() {
        return !type.isRecord();
    }

    List<Output> outputs() {
        return outputs;
    }

    Object instantiate(List<Object> locations) throws ReflectiveOperationException {
        if (isSingle()) {
            return locations.get(0);
        }
        Class<?>[] parameterTypes = outputs.stream().map(output -> output.directory() ? Directory.class : RegularFile.class).toArray(Class<?>[]::new);
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(locations.toArray());
    }
}
