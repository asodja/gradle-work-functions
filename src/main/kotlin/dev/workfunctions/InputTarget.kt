package dev.workfunctions

import dev.workfunctions.internal.InputsInternal
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import java.util.function.BiFunction

class InputTarget<T> internal constructor(private val inputs: InputsInternal, private val accessor: String) {

    fun to(value: T) {
        inputs.bindAccessor(accessor, value)
    }

    fun to(source: Provider<out (T & Any)>) {
        inputs.bindAccessor(accessor, source)
    }

    /**
     * Same as [to].
     */
    fun bind(value: T) = to(value)

    /**
     * Same as [to].
     */
    fun bind(source: Provider<out (T & Any)>) = to(source)

    /**
     * Derives a new value from the current one. The input must be bound.
     *
     * The transform runs when the value is read, not now. It does not run when there is no value.
     * For an input bound to a file collection it runs now, because the collection is lazy itself.
     */
    fun update(transform: Transformer<out T, in (T & Any)>) {
        inputs.updateAccessor(accessor, transform)
    }

    /**
     * Derives a new value from the current one and the value of [other].
     * There is no value when either of them has none.
     */
    fun <U : Any> update(other: Provider<U>, combine: BiFunction<in (T & Any), in U, out T>) {
        inputs.updateAccessor(accessor, other, combine)
    }

    /**
     * Binds the input to a new source that is built from the current one, such as `other.orElse(current)`.
     *
     * The transform runs now, but must not read the current source. The current source has no value when the input is not bound.
     */
    fun rebind(transform: Transformer<out Provider<out (T & Any)>, in Provider<T & Any>>) {
        inputs.rebindAccessor(accessor, transform)
    }

    // Kotlin only. Without this member, a value of the wrong type resolves to the stdlib `to`,
    // which builds a Pair and binds nothing. Hidden from Java, where it would accept any value.
    @JvmSynthetic
    @JvmName("toMismatch")
    @Deprecated("The value does not match the type of the input.", level = DeprecationLevel.ERROR)
    fun to(value: Any?): Nothing =
        throw InvalidUserDataException("The value does not match the type of the input: $value")
}
