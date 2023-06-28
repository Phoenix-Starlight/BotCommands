package com.freya02.botcommands.internal.core.service

import com.freya02.botcommands.api.core.service.ConditionalServiceChecker
import com.freya02.botcommands.api.core.service.ServiceError
import com.freya02.botcommands.api.core.service.ServiceError.ErrorType
import com.freya02.botcommands.api.core.service.ServiceResult
import com.freya02.botcommands.api.core.service.annotations.*
import com.freya02.botcommands.internal.bestName
import com.freya02.botcommands.internal.simpleNestedName
import com.freya02.botcommands.internal.throwInternal
import com.freya02.botcommands.internal.throwUser
import com.freya02.botcommands.internal.utils.ReflectionUtils.nonInstanceParameters
import com.freya02.botcommands.internal.utils.ReflectionUtils.resolveReference
import com.freya02.botcommands.internal.utils.ReflectionUtils.shortSignatureNoSrc
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.jvmErasure
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Either a class nested simple name, or a function signature for factories
 */
internal typealias ProviderName = String

internal data class TimedInstantiation(val result: ServiceResult<*>, val duration: Duration)

internal interface ServiceProvider {
    val name: String
    val providerKey: ProviderName
    val primaryType: KClass<*>
    val types: Set<KClass<*>>

    val instance: Any?

    fun canInstantiate(serviceContainer: ServiceContainerImpl): ServiceError?

    fun createInstance(serviceContainer: ServiceContainerImpl): TimedInstantiation
}

internal fun KAnnotatedElement.getAnnotatedServiceName(): String? {
    findAnnotation<ServiceName>()?.let {
        if (it.value.isNotBlank()) {
            return it.value
        }
    }

    findAnnotation<BService>()?.let {
        if (it.name.isNotBlank()) {
            return it.name
        }
    }

    return null
}

internal fun KAnnotatedElement.getServiceTypes(returnType: KClass<*>) = when (val serviceType = findAnnotation<ServiceType>()) {
    null -> setOf(returnType)
    else -> buildSet(serviceType.types.size + 1) {
        this += returnType
        this += serviceType.types.onEach {
            if (!it.isSuperclassOf(returnType)) {
                throw IllegalArgumentException("${it.simpleNestedName} is not a supertype of service ${returnType.simpleNestedName}")
            }
        }
    }
}

internal fun KAnnotatedElement.commonCanInstantiate(serviceContainer: ServiceContainerImpl, checkedClass: KClass<*>): ServiceError? {
    findAnnotation<Dependencies>()?.value?.let { dependencies ->
        dependencies.forEach { dependency ->
            serviceContainer.canCreateService(dependency)?.let { serviceError ->
                return ErrorType.UNAVAILABLE_DEPENDENCY.toError("Conditional service depends on ${dependency.simpleNestedName} but it is not available", nestedError = serviceError)
            }
        }
    }

    // Services can be conditional
    findAnnotation<ConditionalService>()?.let { conditionalService ->
        conditionalService.checks.forEach {
            val instance = it.objectInstance ?: it.createInstance()
            instance.checkServiceAvailability(serviceContainer.context, checkedClass.java)
                ?.let { errorMessage ->
                    return ErrorType.FAILED_CONDITION.toError(
                        errorMessage,
                        // instance::checkServiceAvailability does not bind to the actual instance
                        failedFunction = ConditionalServiceChecker::checkServiceAvailability.resolveReference(instance::class)
                    )
                }
        }
    }

    //All checks passed, return no error message
    return null
}

@OptIn(ExperimentalTime::class)
internal inline fun <T> measureTimedInstantiation(block: () -> T): TimedInstantiation {
    val measureTimedValue = measureTimedValue(block)
    return TimedInstantiation(ServiceResult.pass(measureTimedValue.value!!), measureTimedValue.duration)
}

internal fun ServiceResult<*>.toFailedTimedInstantiation(): TimedInstantiation {
    if (serviceError != null) {
        return TimedInstantiation(this, Duration.INFINITE)
    } else {
        throwInternal("Cannot use ${::toFailedTimedInstantiation.shortSignatureNoSrc} if service got created (${getOrThrow()::class.simpleNestedName}")
    }
}

internal fun KFunction<*>.checkConstructingFunction(serviceContainer: ServiceContainerImpl): ServiceError? {
    this.nonInstanceParameters.forEach {
        serviceContainer.canCreateService(it.type.jvmErasure)?.let { serviceError ->
            when {
                it.type.isMarkedNullable -> return@forEach //Ignore
                it.isOptional -> return@forEach //Ignore
                else -> return ErrorType.UNAVAILABLE_PARAMETER.toError(
                    errorMessage = "Cannot get service for parameter '${it.bestName}' (${it.type.jvmErasure.simpleNestedName})",
                    failedFunction = this,
                    nestedError = serviceError
                )
            }
        }
    }

    return null
}

internal fun KFunction<*>.callConstructingFunction(serviceContainer: ServiceContainerImpl): TimedInstantiation {
    val params: MutableMap<KParameter, Any?> = hashMapOf()
    this.nonInstanceParameters.forEach {
        //Try to get a dependency, if it doesn't work and parameter isn't nullable / cannot be omitted, then return the message
        val dependencyResult = serviceContainer.tryGetService(it.type.jvmErasure)
        params[it] = dependencyResult.service ?: when {
            it.type.isMarkedNullable -> null
            it.isOptional -> return@forEach
            else -> return ErrorType.UNAVAILABLE_PARAMETER.toResult<Any>(
                "Cannot get service for parameter '${it.bestName}' (${it.type.jvmErasure.simpleNestedName})",
                failedFunction = this,
                nestedError = dependencyResult.serviceError
            ).toFailedTimedInstantiation()
        }
    }

    return measureTimedInstantiation { this.callStatic(serviceContainer, params) }
}

internal fun <R> KFunction<R>.callStatic(serviceContainer: ServiceContainerImpl, args: MutableMap<KParameter, Any?>): R {
    return when (val instanceParameter = this.instanceParameter) {
        null -> this.callBy(args)
        else -> {
            val instanceErasure = instanceParameter.type.jvmErasure
            val instance = instanceErasure.objectInstance
                ?: serviceContainer.tryGetService(instanceErasure).getOrThrow { (_, errorMessage) ->
                    throwUser(this, "Could not run function as the declaring class isn't an object, and service creation failed: $errorMessage")
                }
            args[instanceParameter] = instance

            this.callBy(args)
        }
    }
}