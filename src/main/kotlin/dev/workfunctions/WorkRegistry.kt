package dev.workfunctions

import org.gradle.api.Action
import org.gradle.api.provider.Provider
import kotlin.reflect.KClass

interface WorkRegistry {

    /**
     * Registers work and returns its output. Passing the output to another work's input,
     * or to any Gradle API that accepts a provider, adds the dependency.
     */
    fun <I : Any, O : Any> register(name: String, type: Class<out WorkFunction<I, O>>, configuration: Action<in Inputs<I>>): Provider<O>

    /**
     * The same for Kotlin, with a class reference such as `Greet::class`. Hidden from Java.
     */
    @JvmSynthetic
    fun <I : Any, O : Any> register(name: String, type: KClass<out WorkFunction<I, O>>, configuration: Action<in Inputs<I>>): Provider<O> =
        register(name, type.java, configuration)

    /**
     * Returns the output of existing work. The work is either registered here,
     * or it is a task that a [TaskBackedWork] describes. Nothing is created or configured.
     */
    fun <O : Any> named(name: String, type: Class<out Work<O>>): Provider<O>

    /**
     * The same for Kotlin, with a class reference such as `CompileJava::class`. Hidden from Java.
     */
    @JvmSynthetic
    fun <O : Any> named(name: String, type: KClass<out Work<O>>): Provider<O> =
        named(name, type.java)

    /**
     * Makes the lifecycle task [name] produce the given outputs. Creates the task if needed.
     */
    fun lifecycle(name: String, vararg outputs: Provider<*>)
}
