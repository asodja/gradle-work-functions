package dev.workfunctions.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class InputSchema {

    enum Kind { VALUE, FILE, DIRECTORY, FILES }

    record Component(String name, Method accessor, Kind kind, boolean optional, boolean hasDefault) {
        Class<?> type() {
            return accessor.getReturnType();
        }

        <A extends Annotation> A annotation(Class<A> annotationType) {
            return accessor.getAnnotation(annotationType);
        }
    }

    private final Class<?> type;
    private final List<Component> components;
    private final boolean kotlinDataClass;

    private InputSchema(Class<?> type, List<Component> components, boolean kotlinDataClass) {
        this.type = type;
        this.components = components;
        this.kotlinDataClass = kotlinDataClass;
    }

    static InputSchema of(Class<?> type) {
        List<Component> components = new ArrayList<>();
        // Checked before records: a Kotlin record is also a data class, and only this path knows its default values.
        List<KotlinTypes.Parameter> parameters = KotlinTypes.dataClassParameters(type);
        if (parameters != null) {
            for (KotlinTypes.Parameter parameter : parameters) {
                components.add(component(type, parameter.getName(), parameter.getGetter(), parameter.getHasDefault()));
            }
        } else if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                components.add(component(type, component.getName(), component.getAccessor(), false));
            }
        } else if (type.isInterface()) {
            Method[] methods = type.getMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0) {
                    components.add(component(type, inputName(method.getName()), method, method.isDefault()));
                }
            }
        } else {
            throw new InvalidUserDataException("Input type " + type.getName() + " must be a record, an interface or a Kotlin data class.");
        }
        return new InputSchema(type, components, parameters != null);
    }

    Class<?> type() {
        return type;
    }

    List<Component> components() {
        return components;
    }

    Component byAccessor(String accessorName) {
        for (Component component : components) {
            if (component.accessor().getName().equals(accessorName)) {
                return component;
            }
        }
        return null;
    }

    Object instantiate(Map<String, Object> values) throws ReflectiveOperationException {
        if (kotlinDataClass) {
            return KotlinTypes.construct(type, values);
        }
        if (type.isRecord()) {
            Class<?>[] parameterTypes = components.stream().map(Component::type).toArray(Class<?>[]::new);
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(components.stream().map(component -> values.get(component.name())).toArray());
        }
        InvocationHandler handler = (proxy, method, arguments) -> {
            Component component = byAccessor(method.getName());
            if (component == null || method.getParameterCount() != 0) {
                return switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> type.getSimpleName() + values;
                };
            }
            if (!values.containsKey(component.name()) && method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method);
            }
            return values.get(component.name());
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Component component(Class<?> owner, String name, Method accessor, boolean hasDefault) {
        String description = "Input '" + name + "' of " + owner.getName();
        Kind kind = kindOf(accessor.getGenericReturnType(), description, true);
        boolean optional = isNullable(accessor.getAnnotations())
            || isNullable(accessor.getAnnotatedReturnType())
            || KotlinTypes.isNullable(owner, accessor.getName());
        if (optional && accessor.getReturnType().isPrimitive()) {
            throw new InvalidUserDataException(description + " is primitive and cannot be optional.");
        }
        return new Component(name, accessor, kind, optional, hasDefault);
    }

    private static Kind kindOf(Type type, String description, boolean topLevel) {
        Class<?> raw = type instanceof ParameterizedType parameterized ? (Class<?>) parameterized.getRawType() : type instanceof Class<?> clazz ? clazz : Object.class;
        if (Provider.class.isAssignableFrom(raw)) {
            throw new InvalidUserDataException(description + " must be a plain type, not a " + raw.getSimpleName() + ". Bind a provider to it instead.");
        }
        if (raw == File.class || Path.class.isAssignableFrom(raw)) {
            throw new InvalidUserDataException(description + " must be a RegularFile, Directory or FileCollection, not a " + raw.getSimpleName() + ".");
        }
        boolean fileType = FileSystemLocation.class.isAssignableFrom(raw) || FileCollection.class.isAssignableFrom(raw);
        if (fileType && !topLevel) {
            throw new InvalidUserDataException(description + " holds files inside a nested value. Files must be direct inputs.");
        }
        if (raw == RegularFile.class) {
            return Kind.FILE;
        }
        if (raw == Directory.class) {
            return Kind.DIRECTORY;
        }
        if (FileCollection.class.isAssignableFrom(raw)) {
            return Kind.FILES;
        }
        if (fileType) {
            throw new InvalidUserDataException(description + " must be a RegularFile, Directory or FileCollection, not a " + raw.getSimpleName() + ".");
        }
        if (raw.isRecord()) {
            for (RecordComponent nested : raw.getRecordComponents()) {
                kindOf(nested.getGenericType(), description, false);
            }
        }
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                kindOf(argument, description, false);
            }
        }
        return Kind.VALUE;
    }

    private static String inputName(String methodName) {
        for (String prefix : List.of("get", "is")) {
            if (methodName.length() > prefix.length() && methodName.startsWith(prefix) && Character.isUpperCase(methodName.charAt(prefix.length()))) {
                return Character.toLowerCase(methodName.charAt(prefix.length())) + methodName.substring(prefix.length() + 1);
            }
        }
        return methodName;
    }

    private static boolean isNullable(AnnotatedType type) {
        return isNullable(type.getAnnotations());
    }

    private static boolean isNullable(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getSimpleName().equals("Nullable")) {
                return true;
            }
        }
        return false;
    }
}
