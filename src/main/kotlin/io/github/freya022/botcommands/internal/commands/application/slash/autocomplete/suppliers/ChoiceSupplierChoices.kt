package io.github.freya022.botcommands.internal.commands.application.slash.autocomplete.suppliers

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command

internal class ChoiceSupplierChoices(private val numChoices: Int) : ChoiceSupplier {
    @Suppress("UNCHECKED_CAST")
    override fun apply(event: CommandAutoCompleteInteractionEvent, collection: Collection<Any>): List<Command.Choice> {
        return collection.take(numChoices) as List<Command.Choice>
    }
}