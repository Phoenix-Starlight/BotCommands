package com.freya02.botcommands.internal.parameters

import com.freya02.botcommands.internal.asDiscordString
import com.freya02.botcommands.internal.findOptionName
import com.freya02.botcommands.internal.utils.ReflectionMetadata.isNullable
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

interface MethodParameter {
    val methodParameterType: MethodParameterType
    val kParameter: KParameter
    val name: String
    val discordName: String
        get() = kParameter.findOptionName().asDiscordString()

    val type: KType
        get() = kParameter.type
    val index: Int
        get() = kParameter.index
    val isOption: Boolean
        get() = methodParameterType != MethodParameterType.CUSTOM
    val isOptional: Boolean
        get() = kParameter.isNullable
    /** This checks the java primitiveness, not kotlin, kotlin.Double is an object. */
    val isPrimitive: Boolean
        get() = kParameter.type.jvmErasure.java.isPrimitive
}