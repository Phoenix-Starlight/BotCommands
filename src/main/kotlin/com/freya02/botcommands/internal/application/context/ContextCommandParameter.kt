package com.freya02.botcommands.internal.application.context

import com.freya02.botcommands.internal.parameters.MethodParameter
import com.freya02.botcommands.internal.parameters.MethodParameterType
import com.freya02.botcommands.internal.throwInternal
import kotlin.reflect.KParameter

abstract class ContextCommandParameter<R>(
    parameter: KParameter,
    val resolver: R
) : MethodParameter {
    override val methodParameterType = MethodParameterType.COMMAND
    override val kParameter = parameter
    override val name: String
        get() = throwInternal("Tried to retrieve the name of a context command parameter")
}