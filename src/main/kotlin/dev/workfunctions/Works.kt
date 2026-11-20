package dev.workfunctions

import org.gradle.api.Action
import org.gradle.api.provider.Provider
import kotlin.reflect.KClass

interface Works {

    /**
     * Registers work and returns its output. Passing the output to another work's input,
     * or to any Gradle API that accepts a provider, adds the dependency.
     */
    fun <I : Any, O : Any> work(name: String, type: Class<out WorkFunction<I, O>>, configuration: Action<in Inputs<I>>): Provider<O>

    /**
     * The same for Kotlin, with a class reference such as `Greet::class`. Hidden from Java.
     */
    @JvmSynthetic
    fun <I : Any, O : Any> work(name: String, type: KClass<out WorkFunction<I, O>>, configuration: Action<in Inputs<I>>): Provider<O> =
        work(name, type.java, configuration)

    /**
     * Makes the lifecycle task [name] produce the given outputs. Creates the task if needed.
     */
    fun lifecycle(name: String, vararg outputs: Provider<*>)
}
