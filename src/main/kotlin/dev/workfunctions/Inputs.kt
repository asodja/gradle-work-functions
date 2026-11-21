package dev.workfunctions

import dev.workfunctions.internal.InputsInternal
import org.gradle.api.InvalidUserDataException
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod

/**
 * Binds the inputs of one work function. Nothing is read while binding.
 *
 * `bind(In::x).to(value)` and `input(In::x).set(value)` do the same. Both exist to compare them.
 */
interface Inputs<I : Any> {

    /**
     * Selects the input to bind, with a method reference such as `In::version`.
     */
    fun <T> bind(input: Selector<I, T>): InputTarget<T>

    /**
     * Selects the input to bind from Kotlin, with a reference such as `In::version`.
     *
     * Hidden from Java, where [Selector] does the same job. The two bounds accept a reference
     * to a Kotlin property or to a Java accessor, and reject a lambda and a reference to another type.
     */
    @JvmSynthetic
    fun <T, F> bind(input: F): InputTarget<T> where F : KCallable<T>, F : (I) -> T {
        val accessor = when (input) {
            is KProperty1<*, *> -> input.javaGetter
            is KFunction<*> -> input.javaMethod
            else -> null
        }
        if (accessor == null) {
            throw InvalidUserDataException("Expected a reference to an input, such as In::name, but got ${input.name}.")
        }
        return InputTarget(this as InputsInternal, accessor.name)
    }

    /**
     * Same as [bind].
     */
    fun <T> input(input: Selector<I, T>): InputTarget<T> = bind(input)

    /**
     * Same as [bind], from Kotlin.
     */
    @JvmSynthetic
    fun <T, F> input(input: F): InputTarget<T> where F : KCallable<T>, F : (I) -> T = bind(input)
}
