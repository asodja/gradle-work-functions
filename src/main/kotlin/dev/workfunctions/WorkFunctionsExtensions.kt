@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.workfunctions

import dev.workfunctions.internal.InputsInternal
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.provider.Provider
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaGetter

/**
 * Lets a binding read as `inputs[In::version] = source` inside the configuration block.
 */
val <I : Any> Inputs<I>.inputs: Inputs<I>
    get() = this

// OnlyInputTypes stops the compiler from widening T to Any, which would accept a value of the wrong type.
operator fun <I : Any, @kotlin.internal.OnlyInputTypes T> Inputs<I>.set(input: KProperty1<I, T>, source: Provider<out (T & Any)>) {
    bindProperty(input, source)
}

operator fun <I : Any, @kotlin.internal.OnlyInputTypes T> Inputs<I>.set(input: KProperty1<I, T>, value: T) {
    bindProperty(input, value)
}

fun <W : WorkFunction<I, O>, I : Any, O : Any> Works.work(name: String, type: KClass<W>, configuration: Inputs<I>.() -> Unit): Provider<O> =
    work(name, type.java, Action { configuration() })

private fun Inputs<*>.bindProperty(input: KProperty1<*, *>, source: Any?) {
    val accessor = input.javaGetter?.name ?: throw InvalidUserDataException("${input.name} is not an input.")
    (this as InputsInternal).bindAccessor(accessor, source)
}
