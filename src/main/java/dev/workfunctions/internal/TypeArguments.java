package dev.workfunctions.internal;

import dev.workfunctions.WorkFunction;
import org.gradle.api.InvalidUserDataException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

final class TypeArguments {

    private TypeArguments() {
    }

    /**
     * Returns the input and output classes of a work function type.
     */
    static Class<?>[] of(Class<?> functionType) {
        return of(functionType, WorkFunction.class);
    }

    /**
     * Returns the classes that {@code type} passes as type arguments to {@code target}.
     */
    static Class<?>[] of(Class<?> type, Class<?> target) {
        Type[] arguments = find(type, target, Map.of());
        if (arguments == null) {
            throw new InvalidUserDataException(type.getName() + " must implement " + target.getSimpleName() + " with type arguments.");
        }
        Class<?>[] classes = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            classes[i] = rawClass(arguments[i], type);
        }
        return classes;
    }

    private static Type[] find(Type type, Class<?> target, Map<TypeVariable<?>, Type> bindings) {
        Class<?> raw;
        if (type instanceof ParameterizedType parameterized) {
            raw = (Class<?>) parameterized.getRawType();
            TypeVariable<?>[] variables = raw.getTypeParameters();
            Map<TypeVariable<?>, Type> resolved = new HashMap<>(bindings);
            for (int i = 0; i < variables.length; i++) {
                Type argument = parameterized.getActualTypeArguments()[i];
                resolved.put(variables[i], argument instanceof TypeVariable<?> ? bindings.get(argument) : argument);
            }
            if (raw == target) {
                Type[] arguments = new Type[variables.length];
                for (int i = 0; i < variables.length; i++) {
                    arguments[i] = resolved.get(variables[i]);
                }
                return arguments;
            }
            bindings = resolved;
        } else if (type instanceof Class<?> clazz && clazz != target) {
            raw = clazz;
        } else {
            return null;
        }
        for (Type implemented : raw.getGenericInterfaces()) {
            Type[] found = find(implemented, target, bindings);
            if (found != null) {
                return found;
            }
        }
        Type superclass = raw.getGenericSuperclass();
        return superclass == null ? null : find(superclass, target, bindings);
    }

    private static Class<?> rawClass(Type type, Class<?> functionType) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        throw new InvalidUserDataException("Cannot determine the type arguments of " + functionType.getName() + ".");
    }
}
