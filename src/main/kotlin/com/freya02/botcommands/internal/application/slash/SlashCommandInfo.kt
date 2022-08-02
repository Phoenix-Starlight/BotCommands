package com.freya02.botcommands.internal.application.slash

import com.freya02.botcommands.api.Logging
import com.freya02.botcommands.api.application.builder.SlashCommandBuilder
import com.freya02.botcommands.api.application.builder.SlashCommandOptionBuilder
import com.freya02.botcommands.api.application.builder.findOption
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent
import com.freya02.botcommands.api.application.slash.GuildSlashEvent
import com.freya02.botcommands.api.parameters.SlashParameterResolver
import com.freya02.botcommands.internal.*
import com.freya02.botcommands.internal.application.ApplicationCommandInfo
import com.freya02.botcommands.internal.application.slash.SlashUtils2.checkDefaultValue
import com.freya02.botcommands.internal.parameters.CustomMethodParameter
import com.freya02.botcommands.internal.parameters.MethodParameterType
import com.freya02.botcommands.internal.utils.ReflectionUtilsKt.shortSignature
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload
import kotlin.math.max
import kotlin.reflect.KParameter
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

class SlashCommandInfo internal constructor(
    val context: BContextImpl,
    val builder: SlashCommandBuilder
) : ApplicationCommandInfo(
    context,
    builder
) {
    val description = builder.description

    override val parameters: MethodParameters

    @Suppress("UNCHECKED_CAST")
    override val optionParameters: List<SlashCommandParameter>
        get() = super.optionParameters as List<SlashCommandParameter>

    init {
        requireFirstParam(method.valueParameters, GlobalSlashEvent::class)

        val firstParamKlass = method.valueParameters.first().type.jvmErasure
        if (scope.isGuildOnly) {
            if (!firstParamKlass.isSubclassOf(GuildSlashEvent::class)) {
                LOGGER.warn("${method.shortSignature} : First parameter could be a GuildSlashEvent as to benefit from non-null getters")
            }
        } else if (firstParamKlass.isSubclassOf(GuildSlashEvent::class)) {
            throwUser("Cannot use ${GuildSlashEvent::class.simpleName} on a global application command")
        }

        parameters = MethodParameters.of<SlashParameterResolver>(
            context,
            method
        ) { kParameter, paramName, resolver ->
            val optionBuilder = builder.optionBuilders.findOption<SlashCommandOptionBuilder>(paramName)
            SlashCommandParameter(this, kParameter, optionBuilder, resolver)
        }

        //On every autocomplete handler, check if their method parameters match up with the slash command
        parameters.forEach { slashParam ->
            if (slashParam.methodParameterType == MethodParameterType.COMMAND) {
                slashParam as SlashCommandParameter

                slashParam.autocompleteHandler?.let { handler ->
                    handler.methodParameters.forEach { autocompleteParam ->
                        val param = parameters.find { it.name == autocompleteParam.name }
                            ?: throwUser(
                                handler.autocompleteInfo.method,
                                "Could not find parameter ${autocompleteParam.name} in the slash command declaration"
                            )

                        requireUser(param.kParameter.checkTypeEquals(autocompleteParam.kParameter), handler.autocompleteInfo.method) {
                            "Autocomplete parameter type should be the same as the slash command one, slash command type: '${param.type.simpleName}', autocomplete type: '${autocompleteParam.type.simpleName}'"
                        }
                    }
                }
            }
        }
    }

    suspend fun execute(event: SlashCommandInteractionEvent): Boolean {
        val objects: MutableMap<KParameter, Any?> = mutableMapOf()
        objects[method.instanceParameter!!] = instance
        objects[method.valueParameters.first()] =
            if (isGuildOnly) GuildSlashEvent(context, method, event) else GlobalSlashEventImpl(context, method, event)

        putSlashOptions(event, objects, parameters)

        applyCooldown(event)

        method.callSuspendBy(objects)

        return true
    }

    internal fun <T> putSlashOptions(
        event: T,
        objects: MutableMap<KParameter, Any?>,
        methodParameters: MethodParameters
    ) where T : CommandInteractionPayload,
            T : Event {
        parameterLoop@ for (parameter in methodParameters) {
            if (parameter.methodParameterType == MethodParameterType.COMMAND) {
                parameter as AbstractSlashCommandParameter

                val guild = event.guild
                if (guild != null) {
                    val supplier = parameter.defaultOptionSupplierMap[guild.idLong]
                    if (supplier != null) {
                        val defaultVal = supplier.getDefaultValue(event)
                        checkDefaultValue(parameter, defaultVal)

                        objects[parameter.kParameter] = defaultVal

                        continue
                    }
                }

                val arguments = max(1, parameter.varArgs)
                val objectList: MutableList<Any?> = arrayOfSize(arguments)

                val optionName = parameter.discordName
                for (varArgNum in 0 until arguments) {
                    val varArgName = SlashUtils.getVarArgName(optionName, varArgNum) //TODO extension or infix
                    val optionMapping = event.getOption(varArgName)
                        ?: if (parameter.isOptional || (parameter.isVarArg && !parameter.isRequiredVararg(varArgNum))) {
                            objectList += when {
                                parameter.isPrimitive -> 0
                                else -> null
                            }

                            continue
                        } else {
                            if (event is CommandAutoCompleteInteractionEvent) continue@parameterLoop

                            throwUser("Slash parameter couldn't be resolved at parameter " + parameter.name + " (" + varArgName + ")")
                        }

                    val resolved = parameter.resolver.resolve(context, this, event, optionMapping)
                    if (resolved == null) {
                        if (event is SlashCommandInteractionEvent) {
                            event.reply(
                                context.getDefaultMessages(event).getSlashCommandUnresolvableParameterMsg(
                                    parameter.name,
                                    parameter.type.jvmErasure.simpleName
                                )
                            )
                                .setEphemeral(true)
                                .queue()
                        }

                        //Not a warning, could be normal if the user did not supply a valid string for user-defined resolvers
                        LOGGER.trace(
                            "The parameter '{}' of value '{}' could not be resolved into a {}",
                            parameter.name,
                            optionMapping.asString,
                            parameter.type.jvmErasure.simpleName
                        )

                        return
                    }

                    requireUser(parameter.type.jvmErasure.isSuperclassOf(resolved::class)) {
                        "The parameter '%s' of value '%s' is not a valid type (expected a %s, got a %s)".format(
                            parameter.name,
                            optionMapping.asString,
                            parameter.type.jvmErasure.simpleName,
                            resolved::class.simpleName
                        )
                    }

                    objectList.add(resolved)
                }

                objects[parameter.kParameter] = if (parameter.isVarArg) objectList else objectList[0]
            } else if (parameter.methodParameterType == MethodParameterType.CUSTOM) {
                parameter as CustomMethodParameter

                objects[parameter.kParameter] = parameter.resolver.resolve(context, this, event)
            } else {
                TODO()
            }
        }
    }

    fun getAutocompletionHandlerName(event: CommandAutoCompleteInteractionEvent): String? {
        throw UnsupportedOperationException()
    }

    companion object {
        private val LOGGER = Logging.getLogger()
    }
}