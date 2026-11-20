package dev.workfunctions

import dev.workfunctions.internal.InputsInternal
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.provider.Provider
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaGetter

/**
 * Selects the input to bind, with a property reference such as `In::version`.
 */
fun <I : Any, T> Inputs<I>.bind(input: KProperty1<I, T>): KotlinInputTarget<T> {
    val accessor = input.javaGetter?.name ?: throw InvalidUserDataException("${input.name} is not an input.")
    return KotlinInputTarget(this as InputsInternal, accessor)
}

class KotlinInputTarget<T> internal constructor(private val inputs: InputsInternal, private val accessor: String) {

    fun to(value: T) {
        inputs.bindAccessor(accessor, value)
    }

    fun to(source: Provider<out (T & Any)>) {
        inputs.bindAccessor(accessor, source)
    }

    // Without this member, a value of the wrong type resolves to the stdlib `to`, which builds a Pair and binds nothing.
    @Deprecated("The value does not match the type of the input.", level = DeprecationLevel.ERROR)
    @JvmName("toMismatch")
    fun to(value: Any?): Nothing =
        throw InvalidUserDataException("The value does not match the type of the input: $value")
}

fun <W : WorkFunction<I, O>, I : Any, O : Any> Works.work(name: String, type: KClass<W>, configuration: Inputs<I>.() -> Unit): Provider<O> =
    work(name, type.java, Action { configuration() })
