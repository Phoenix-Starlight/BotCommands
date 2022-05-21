package com.freya02.botcommands.internal.prefixed

import com.freya02.botcommands.api.BContext
import com.freya02.botcommands.api.Logging
import com.freya02.botcommands.api.application.CommandPath
import com.freya02.botcommands.api.parameters.RegexParameterResolver
import com.freya02.botcommands.api.prefixed.CommandEvent
import com.freya02.botcommands.api.prefixed.builder.TextCommandBuilder
import com.freya02.botcommands.internal.AbstractCommandInfo
import com.freya02.botcommands.internal.BContextImpl
import com.freya02.botcommands.internal.MethodParameters
import com.freya02.botcommands.internal.utils.Utils
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure

class TextCommandInfo(
    context: BContext,
    builder: TextCommandBuilder
) : AbstractCommandInfo(context, builder) {
    override val parameters: MethodParameters<TextCommandParameter>
    @Suppress("UNCHECKED_CAST")
    override val optionParameters: List<TextCommandParameter>
        get() = super.optionParameters as List<TextCommandParameter>

    private val LOGGER = Logging.getLogger()

    val aliases: List<CommandPath>
    val description: String
    val hidden: Boolean
    val completePattern: Pattern?
    val order: Int
    val isRegexCommand: Boolean

    init {
        aliases = builder.aliases
        description = builder.description
        order = builder.order
        hidden = builder.hidden

        isRegexCommand = method.valueParameters[0].type.jvmErasure.isSuperclassOf(CommandEvent::class)
        parameters = MethodParameters.of(method) { index: Int, parameter: KParameter ->
            //TODO check if function isn't fallback
            TextCommandParameter(RegexParameterResolver::class, parameter, index)
        }

        completePattern = if (parameters.optionCount > 0) {
            CommandPattern.of(this)
        } else null
    }

    @Throws(Exception::class)
    fun execute(
        context: BContextImpl,
        event: MessageReceivedEvent,
        args: String,
        matcher: Matcher,
        throwableConsumer: Consumer<Throwable>
    ): ExecutionResult {
        val objects: MutableList<Any?> = ArrayList(parameters.size + 1)
        objects += if (isRegexCommand) BaseCommandEventImpl(context, method, event, args) else CommandEventImpl(context, method, event, args)

        if (isRegexCommand) {
            var groupIndex = 1
            for (parameter in parameters) {
                if (parameter.isOption) {
                    var found = 0
                    val groupCount = parameter.groupCount
                    val groups = arrayOfNulls<String>(groupCount)
                    for (j in 0 until groupCount) {
                        groups[j] = matcher.group(groupIndex++)
                        if (groups[j] != null) found++
                    }
                    if (found == groupCount) { //Found all the groups
                        val resolved = parameter.resolver.resolve(context, this, event, groups)
                        //Regex matched but could not be resolved
                        // if optional then it's ok
                        if (resolved == null && !parameter.isOptional) {
                            return ExecutionResult.CONTINUE
                        }
                        objects.add(resolved)
                    } else if (!parameter.isOptional) { //Parameter is not found yet the pattern matched and is not optional
                        LOGGER.warn(
                            "Could not find parameter #{} in {} for input args {}",
                            parameter.index,
                            Utils.formatMethodShort(method),
                            args
                        )
                        return ExecutionResult.CONTINUE
                    } else { //Parameter is optional
                        if (parameter.isPrimitive) {
                            objects.add(0)
                        } else {
                            objects.add(null)
                        }
                    }
                } else {
                    objects.add(parameter.customResolver.resolve(context, this, event))
                }
            }
        } else {
            for (parameter in parameters) {
                objects.add(parameter.customResolver.resolve(context, this, event))
            }
        }

        applyCooldown(event)

        try {
            method.call(*objects.toTypedArray())
        } catch (e: Throwable) {
            throwableConsumer.accept(e)
        }

        return ExecutionResult.OK
    }
}