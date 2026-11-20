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
        Type[] arguments = find(functionType, Map.of());
        if (arguments == null) {
            throw new InvalidUserDataException(functionType.getName() + " must implement WorkFunction with type arguments.");
        }
        return new Class<?>[]{rawClass(arguments[0], functionType), rawClass(arguments[1], functionType)};
    }

    private static Type[] find(Type type, Map<TypeVariable<?>, Type> bindings) {
        Class<?> raw;
        if (type instanceof ParameterizedType parameterized) {
            raw = (Class<?>) parameterized.getRawType();
            TypeVariable<?>[] variables = raw.getTypeParameters();
            Map<TypeVariable<?>, Type> resolved = new HashMap<>(bindings);
            for (int i = 0; i < variables.length; i++) {
                Type argument = parameterized.getActualTypeArguments()[i];
                resolved.put(variables[i], argument instanceof TypeVariable<?> ? bindings.get(argument) : argument);
            }
            if (raw == WorkFunction.class) {
                return new Type[]{resolved.get(variables[0]), resolved.get(variables[1])};
            }
            bindings = resolved;
        } else if (type instanceof Class<?> clazz && clazz != WorkFunction.class) {
            raw = clazz;
        } else {
            return null;
        }
        for (Type implemented : raw.getGenericInterfaces()) {
            Type[] found = find(implemented, bindings);
            if (found != null) {
                return found;
            }
        }
        Type superclass = raw.getGenericSuperclass();
        return superclass == null ? null : find(superclass, bindings);
    }

    private static Class<?> rawClass(Type type, Class<?> functionType) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        throw new InvalidUserDataException("Cannot determine the input and output types of " + functionType.getName() + ".");
    }
}
