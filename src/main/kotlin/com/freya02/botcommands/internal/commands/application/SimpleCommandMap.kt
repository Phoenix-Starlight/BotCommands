package com.freya02.botcommands.internal.commands.application

import com.freya02.botcommands.api.commands.builder.IBuilderFunctionHolder
import com.freya02.botcommands.internal.IExecutableInteractionInfo
import com.freya02.botcommands.internal.commands.mixins.INamedCommandInfo
import com.freya02.botcommands.internal.throwUser
import com.freya02.botcommands.internal.utils.ReflectionUtilsKt.shortSignature
import java.util.*
import kotlin.reflect.KFunction

internal class SimpleCommandMap<T : INamedCommandInfo>(private val functionSupplier: ((T) -> KFunction<*>)?) {
    private val mutableMap: MutableMap<String, T> = hashMapOf()
    val map: Map<String, T>
        get() = Collections.unmodifiableMap(mutableMap)

    fun putNewCommand(newCommand: T) {
        mutableMap.putIfAbsent(newCommand.name, newCommand)?.let { oldCommand ->
            throwUser(
                """
                Command '${newCommand.path.fullPath}' is already defined
                Existing command: ${functionSupplier?.invoke(oldCommand)?.shortSignature}
                Current command: ${functionSupplier?.invoke(newCommand)?.shortSignature}
                """.trimIndent()
            )
        }
    }

    fun isEmpty(): Boolean = mutableMap.isEmpty()

    companion object {
        fun <T> ofBuilders(): SimpleCommandMap<T> where T : INamedCommandInfo,
                                                        T : IBuilderFunctionHolder<*> {
            return SimpleCommandMap(IBuilderFunctionHolder<*>::function)
        }

        fun <T> ofInfos(): SimpleCommandMap<T> where T : INamedCommandInfo,
                                                     T : IExecutableInteractionInfo {
            return SimpleCommandMap(IExecutableInteractionInfo::method)
        }
    }
}