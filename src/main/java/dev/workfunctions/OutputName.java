package dev.workfunctions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The file name of an output. The default is the work name for a single output, and the component name otherwise.
 *
 * <p>For a single output, put it on the output parameter of {@code run}.
 * For several outputs, put it on a record component or on a Kotlin data class property.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface OutputName {
    String value();
}
