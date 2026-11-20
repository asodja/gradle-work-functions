package dev.workfunctions.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;

import java.io.File;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Values {

    private Values() {
    }

    static void checkType(String workName, InputSchema.Component component, Object value) {
        Class<?> expected = MethodType.methodType(component.type()).wrap().returnType();
        if (!expected.isInstance(value)) {
            throw new InvalidUserDataException("Input '" + component.name() + "' of work '" + workName + "' expects " + component.type().getSimpleName()
                + " but got " + value.getClass().getSimpleName() + ".");
        }
    }

    /**
     * Turns a value into a form Gradle can snapshot. Records are not serializable by default,
     * so they become maps of their components.
     */
    static Object snapshotForm(Object value) {
        if (value instanceof Record record) {
            Map<String, Object> components = new LinkedHashMap<>();
            components.put("@type", record.getClass().getName());
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                components.put(component.getName(), snapshotForm(read(record, component)));
            }
            return components;
        }
        if (value instanceof List<?> list) {
            List<Object> elements = new ArrayList<>();
            list.forEach(element -> elements.add(snapshotForm(element)));
            return elements;
        }
        if (value instanceof Set<?> set) {
            Set<Object> elements = new LinkedHashSet<>();
            set.forEach(element -> elements.add(snapshotForm(element)));
            return elements;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> entries = new LinkedHashMap<>();
            map.forEach((key, element) -> entries.put(snapshotForm(key), snapshotForm(element)));
            return entries;
        }
        if (value instanceof FileSystemLocation || value instanceof FileCollection || value instanceof File || value instanceof Path) {
            throw new InvalidUserDataException("A value input holds a file: " + value + ". Files must be direct inputs.");
        }
        return value;
    }

    private static Object read(Record record, RecordComponent component) {
        try {
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + component.getName() + " of " + record.getClass().getName(), e);
        }
    }

    // A named class, not a lambda, so the configuration cache can store it.
    static final class ToSnapshotForm implements Transformer<Object, Object> {
        @Override
        public Object transform(Object value) {
            return snapshotForm(value);
        }
    }
}
