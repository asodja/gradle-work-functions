package dev.workfunctions.internal

import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

internal object KotlinTypes {

    /**
     * Kotlin nullability is not visible through Java reflection, so it is read from the Kotlin metadata.
     */
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
}
