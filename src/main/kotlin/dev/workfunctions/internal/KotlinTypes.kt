package dev.workfunctions.internal

import org.gradle.api.InvalidUserDataException
import java.lang.reflect.Method
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaGetter

/**
 * Kotlin nullability and default values are not visible through Java reflection,
 * so they are read from the Kotlin metadata.
 */
internal object KotlinTypes {

    class Parameter(val name: String, val getter: Method, val hasDefault: Boolean)

    @JvmStatic
    fun isNullable(type: Class<*>, accessorName: String): Boolean {
        if (!type.isAnnotationPresent(Metadata::class.java)) {
            return false
        }
        return try {
            type.kotlin.memberProperties.any { it.javaGetter?.name == accessorName && it.returnType.isMarkedNullable }
        } catch (e: Exception) {
            false
        } catch (e: Error) {
            false
        }
    }

    /**
     * Returns the primary constructor parameters of a Kotlin data class, or null for any other type.
     */
    @JvmStatic
    fun dataClassParameters(type: Class<*>): List<Parameter>? {
        if (!type.isAnnotationPresent(Metadata::class.java) || type.isInterface) {
            return null
        }
        val kotlinClass = type.kotlin
        val constructor = kotlinClass.takeIf { it.isData }?.primaryConstructor ?: return null
        return constructor.parameters.map { parameter ->
            val getter = kotlinClass.memberProperties.first { it.name == parameter.name }.javaGetter
                ?: throw InvalidUserDataException("Input '${parameter.name}' of ${type.name} must not be private.")
            Parameter(parameter.name!!, getter, parameter.isOptional)
        }
    }

    /**
     * Calls the primary constructor. A parameter with a default value is left out when it has no value,
     * which makes Kotlin apply the default.
     */
    @JvmStatic
    fun construct(type: Class<*>, values: Map<String, Any?>): Any {
        val constructor = type.kotlin.primaryConstructor!!
        constructor.isAccessible = true
        val arguments = constructor.parameters
            .filter { it.name in values || !it.isOptional }
            .associateWith { values[it.name] }
        return constructor.callBy(arguments)
    }
}
