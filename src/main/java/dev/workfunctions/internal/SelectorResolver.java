package dev.workfunctions.internal;

import org.gradle.api.InvalidUserDataException;

import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

final class SelectorResolver {

    private SelectorResolver() {
    }

    /**
     * Finds the input a selector points to, without calling the selector.
     */
    static InputSchema.Component resolve(Object selector, InputSchema schema) {
        String example = schema.type().getSimpleName() + "::" + (schema.components().isEmpty() ? "name" : schema.components().get(0).accessor().getName());
        SerializedLambda lambda = serializedForm(selector);
        if (lambda == null) {
            throw new InvalidUserDataException("Expected a method reference to an input, such as " + example + ".");
        }
        String owner = lambda.getImplClass().replace('/', '.');
        String target = owner + "::" + lambda.getImplMethodName();
        int kind = lambda.getImplMethodKind();
        boolean instanceMethod = kind == MethodHandleInfo.REF_invokeVirtual || kind == MethodHandleInfo.REF_invokeInterface;
        InputSchema.Component component = instanceMethod && isSupertype(owner, schema.type()) ? schema.byAccessor(lambda.getImplMethodName()) : null;
        if (component == null || !lambda.getImplMethodSignature().startsWith("()")) {
            throw new InvalidUserDataException(target + " is not an input of " + schema.type().getName() + ". Use a method reference such as " + example + ".");
        }
        return component;
    }

    // A serializable lambda class has a synthetic writeReplace() that describes the referenced method.
    private static SerializedLambda serializedForm(Object selector) {
        try {
            Method writeReplace = selector.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            return writeReplace.invoke(selector) instanceof SerializedLambda lambda ? lambda : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isSupertype(String className, Class<?> type) {
        if (type == null) {
            return false;
        }
        if (type.getName().equals(className) || isSupertype(className, type.getSuperclass())) {
            return true;
        }
        for (Class<?> implemented : type.getInterfaces()) {
            if (isSupertype(className, implemented)) {
                return true;
            }
        }
        return false;
    }
}
