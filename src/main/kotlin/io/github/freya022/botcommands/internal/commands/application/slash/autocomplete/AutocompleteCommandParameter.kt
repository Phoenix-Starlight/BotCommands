package io.github.freya022.botcommands.internal.commands.application.slash.autocomplete

import io.github.freya022.botcommands.api.commands.application.slash.builder.SlashCommandOptionAggregateBuilder
import io.github.freya022.botcommands.api.commands.application.slash.builder.SlashCommandOptionBuilder
import io.github.freya022.botcommands.api.parameters.resolvers.SlashParameterResolver
import io.github.freya022.botcommands.internal.commands.application.slash.AbstractSlashCommandParameter
import io.github.freya022.botcommands.internal.commands.application.slash.SlashCommandInfo
import io.github.freya022.botcommands.internal.parameters.IAggregatedParameter
import io.github.freya022.botcommands.internal.transform
import io.github.freya022.botcommands.internal.utils.ReflectionMetadata.isNullable
import io.github.freya022.botcommands.internal.utils.requireUser
import io.github.freya022.botcommands.internal.utils.shortSignatureNoSrc
import io.github.freya022.botcommands.internal.utils.throwUser
import kotlin.reflect.KFunction
import kotlin.reflect.full.findParameterByName

class AutocompleteCommandParameter(
    slashCommandInfo: SlashCommandInfo,
    slashCmdOptionAggregateBuilders: Map<String, SlashCommandOptionAggregateBuilder>,
    optionAggregateBuilder: SlashCommandOptionAggregateBuilder,
    autocompleteFunction: KFunction<*>
) : AbstractSlashCommandParameter(slashCommandInfo, slashCmdOptionAggregateBuilders, optionAggregateBuilder) {
    override val executableParameter = (autocompleteFunction.findParameterByName(name))?.also {
        requireUser(it.isNullable == kParameter.isNullable, autocompleteFunction) {
            "Parameter from autocomplete function '${kParameter.name}' should have same nullability as on slash command ${slashCommandInfo.function.shortSignatureNoSrc}"
        }
    } ?: throwUser(
        autocompleteFunction,
        "Parameter from autocomplete function '${kParameter.name}' should have been found on slash command ${slashCommandInfo.function.shortSignatureNoSrc}"
    )

    override val nestedAggregatedParameters: List<IAggregatedParameter> = optionAggregateBuilder.nestedAggregates.transform {
        AutocompleteCommandParameter(slashCommandInfo, it.nestedAggregates, it, optionAggregateBuilder.aggregator)
    }

    override fun constructOption(
        slashCommandInfo: SlashCommandInfo,
        optionAggregateBuilders: Map<String, SlashCommandOptionAggregateBuilder>,
        optionBuilder: SlashCommandOptionBuilder,
        resolver: SlashParameterResolver<*, *>
    ) = AutocompleteCommandOption(optionBuilder, resolver)
}