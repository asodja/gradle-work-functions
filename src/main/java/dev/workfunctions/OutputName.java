package dev.workfunctions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The file name of an output. On a work function class it names a single file output.
 * On a record component, or on the getter of a Kotlin data class property, it names that output.
 * The default is the work or component name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface OutputName {
    String value();
}
