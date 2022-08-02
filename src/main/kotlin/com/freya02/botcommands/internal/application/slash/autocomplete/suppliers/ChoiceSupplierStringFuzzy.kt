package com.freya02.botcommands.internal.application.slash.autocomplete.suppliers

import com.freya02.botcommands.api.application.slash.autocomplete.AutocompleteAlgorithms
import com.freya02.botcommands.internal.application.slash.autocomplete.AutocompleteHandler.Companion.asChoice
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command

internal class ChoiceSupplierStringFuzzy(private val numChoices: Int) : ChoiceSupplier {
    @Throws(Exception::class)
    override fun apply(event: CommandAutoCompleteInteractionEvent, collection: Collection<*>): List<Command.Choice> {
        val autoCompleteQuery = event.focusedOption
        return AutocompleteAlgorithms.fuzzyMatching(collection, { it.toString() }, event)
            .take(numChoices)
            .map { it.string.asChoice(autoCompleteQuery.type) ?: throw IllegalArgumentException("Malformed input for option type ${autoCompleteQuery.type}: '${it.string}'") }
    }
}