package io.github.freya022.botcommands.internal.commands.application.slash

import io.github.freya022.botcommands.api.commands.application.LengthRange
import io.github.freya022.botcommands.api.commands.application.ValueRange
import io.github.freya022.botcommands.api.commands.application.slash.builder.SlashCommandOptionAggregateBuilder
import io.github.freya022.botcommands.api.commands.application.slash.builder.SlashCommandOptionBuilder
import io.github.freya022.botcommands.api.parameters.resolvers.SlashParameterResolver
import io.github.freya022.botcommands.internal.commands.application.slash.autocomplete.AutocompleteHandler
import io.github.freya022.botcommands.internal.utils.LocalizationUtils
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType

class SlashCommandOption(
    slashCommandInfo: SlashCommandInfo,
    optionAggregateBuilders: Map<String, SlashCommandOptionAggregateBuilder>,
    optionBuilder: SlashCommandOptionBuilder,
    resolver: SlashParameterResolver<*, *>
) : AbstractSlashCommandOption(optionBuilder, resolver) {
    val description: String

    internal val autocompleteHandler by lazy {
        when (val autocompleteInfo = optionBuilder.autocompleteInfo) {
            null -> null
            else -> AutocompleteHandler(slashCommandInfo, optionAggregateBuilders, autocompleteInfo)
        }
    }

    val usePredefinedChoices = optionBuilder.usePredefinedChoices
    val choices: List<Command.Choice>? = optionBuilder.choices
    val range: ValueRange? = optionBuilder.valueRange
    val length: LengthRange? = optionBuilder.lengthRange

    init {
        description = LocalizationUtils.getOptionDescription(slashCommandInfo.context, optionBuilder)

        if (range != null) {
            if (resolver.optionType != OptionType.NUMBER && resolver.optionType != OptionType.INTEGER) {
                throw IllegalStateException("Cannot use ranges on an option that doesn't accept an integer/number")
            }
        } else if (length != null) {
            if (resolver.optionType != OptionType.STRING) {
                throw IllegalStateException("Cannot use lengths on an option that doesn't accept an string")
            }
        }
    }

    internal fun buildAutocomplete() {
        autocompleteHandler?.validateParameters()
    }

    fun hasAutocomplete() = autocompleteHandler != null
}