package io.github.freya022.botcommands.internal.components.handler

import io.github.freya022.botcommands.api.components.annotations.JDAButtonListener
import io.github.freya022.botcommands.api.components.annotations.JDASelectMenuListener
import io.github.freya022.botcommands.api.components.annotations.RequiresComponents
import io.github.freya022.botcommands.api.components.event.ButtonEvent
import io.github.freya022.botcommands.api.components.event.EntitySelectEvent
import io.github.freya022.botcommands.api.components.event.StringSelectEvent
import io.github.freya022.botcommands.api.core.service.annotations.BService
import io.github.freya022.botcommands.internal.core.BContextImpl
import io.github.freya022.botcommands.internal.core.reflection.toMemberParamFunction
import io.github.freya022.botcommands.internal.core.requiredFilter
import io.github.freya022.botcommands.internal.core.service.FunctionAnnotationsMap
import io.github.freya022.botcommands.internal.utils.FunctionFilter
import io.github.freya022.botcommands.internal.utils.shortSignature
import io.github.freya022.botcommands.internal.utils.throwUser
import kotlin.reflect.full.findAnnotation

@BService
@RequiresComponents
internal class ComponentHandlerContainer(context: BContextImpl, functionAnnotationsMap: FunctionAnnotationsMap) {
    private val buttonMap: MutableMap<String, ComponentDescriptor> = hashMapOf()
    private val selectMap: MutableMap<String, ComponentDescriptor> = hashMapOf()

    init {
        functionAnnotationsMap.get<JDAButtonListener>()
            .requiredFilter(FunctionFilter.nonStatic())
            .requiredFilter(FunctionFilter.firstArg(ButtonEvent::class))
            .forEach {
                val handlerName = it.function.findAnnotation<JDAButtonListener>()!!.name

                val oldDescriptor = buttonMap.put(handlerName, ComponentDescriptor(context, it.toMemberParamFunction()))
                if (oldDescriptor != null) {
                    throwUser("Tried to override a button handler, old method: ${oldDescriptor.function.shortSignature}, new method: ${it.function.shortSignature}")
                }
            }

        functionAnnotationsMap.get<JDASelectMenuListener>()
            .requiredFilter(FunctionFilter.nonStatic())
            .requiredFilter(FunctionFilter.firstArg(StringSelectEvent::class, EntitySelectEvent::class))
            .forEach {
                val handlerName = it.function.findAnnotation<JDASelectMenuListener>()!!.name

                val oldDescriptor = selectMap.put(handlerName, ComponentDescriptor(context, it.toMemberParamFunction()))
                if (oldDescriptor != null) {
                    throwUser("Tried to override a select menu handler, old method: ${oldDescriptor.function.shortSignature}, new method: ${it.function.shortSignature}")
                }
            }
    }

    fun getButtonDescriptor(handlerName: String) = buttonMap[handlerName]
    fun getSelectMenuDescriptor(handlerName: String) = selectMap[handlerName]
}