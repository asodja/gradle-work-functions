package dev.workfunctions.internal;

import dev.workfunctions.OutputName;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OutputSchema {

    record Output(String name, boolean directory, String fileName) {
    }

    private final Class<?> type;
    private final List<Output> outputs;
    private final boolean kotlinDataClass;

    private OutputSchema(Class<?> type, List<Output> outputs, boolean kotlinDataClass) {
        this.type = type;
        this.outputs = outputs;
        this.kotlinDataClass = kotlinDataClass;
    }

    static OutputSchema of(Class<?> type, Class<?> functionType, String workName) {
        List<Output> outputs = new ArrayList<>();
        List<KotlinTypes.Parameter> parameters = KotlinTypes.dataClassParameters(type);
        OutputName parameterName = outputParameterName(functionType);
        if (type == Directory.class || type == RegularFile.class) {
            outputs.add(new Output("output", type == Directory.class, parameterName == null ? workName : parameterName.value()));
        } else if (parameterName != null) {
            throw new InvalidUserDataException("@OutputName on the output parameter of " + functionType.getName() + " only applies to a single output. Put it on the components of " + type.getSimpleName() + ".");
        } else if (parameters != null) {
            for (KotlinTypes.Parameter parameter : parameters) {
                outputs.add(output(type, parameter.getName(), parameter.getGetter().getReturnType(), parameter.annotation(OutputName.class)));
            }
        } else if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                outputs.add(output(type, component.getName(), component.getType(), component.getAnnotation(OutputName.class)));
            }
        } else {
            throw new InvalidUserDataException("Output type " + type.getName() + " must be a Directory, a RegularFile, a record of those, or a Kotlin data class of those.");
        }
        return new OutputSchema(type, outputs, parameters != null);
    }

    boolean isSingle() {
        return type == Directory.class || type == RegularFile.class;
    }

    List<Output> outputs() {
        return outputs;
    }

    Object instantiate(List<Object> locations) throws ReflectiveOperationException {
        if (isSingle()) {
            return locations.get(0);
        }
        if (kotlinDataClass) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < outputs.size(); i++) {
                values.put(outputs.get(i).name(), locations.get(i));
            }
            return KotlinTypes.construct(type, values);
        }
        Class<?>[] parameterTypes = outputs.stream().map(output -> output.directory() ? Directory.class : RegularFile.class).toArray(Class<?>[]::new);
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(locations.toArray());
    }

    private static Output output(Class<?> owner, String name, Class<?> outputType, OutputName fileName) {
        if (outputType != Directory.class && outputType != RegularFile.class) {
            throw new InvalidUserDataException("Output '" + name + "' of " + owner.getName() + " must be a Directory or a RegularFile.");
        }
        return new Output(name, outputType == Directory.class, fileName == null ? name : fileName.value());
    }

    private static OutputName outputParameterName(Class<?> functionType) {
        for (Method method : functionType.getMethods()) {
            if (method.getName().equals("run") && method.getParameterCount() == 2 && !method.isBridge()) {
                OutputName name = method.getParameters()[1].getAnnotation(OutputName.class);
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }
}
