package dev.workfunctions

import dev.workfunctions.internal.InputsInternal
import org.gradle.api.InvalidUserDataException
import org.gradle.api.provider.Provider

class InputTarget<T> internal constructor(private val inputs: InputsInternal, private val accessor: String) {

    fun to(value: T) {
        inputs.bindAccessor(accessor, value)
    }

    fun to(source: Provider<out (T & Any)>) {
        inputs.bindAccessor(accessor, source)
    }

    // Kotlin only. Without this member, a value of the wrong type resolves to the stdlib `to`,
    // which builds a Pair and binds nothing. Hidden from Java, where it would accept any value.
    @JvmSynthetic
    @JvmName("toMismatch")
    @Deprecated("The value does not match the type of the input.", level = DeprecationLevel.ERROR)
    fun to(value: Any?): Nothing =
        throw InvalidUserDataException("The value does not match the type of the input: $value")
}
