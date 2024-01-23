package io.github.freya022.botcommands.internal.commands.application.autocomplete

import io.github.freya022.botcommands.api.commands.CommandPath
import io.github.freya022.botcommands.api.core.BContext
import io.github.freya022.botcommands.api.core.annotations.BEventListener
import io.github.freya022.botcommands.api.core.service.annotations.BService
import io.github.freya022.botcommands.internal.commands.application.slash.SlashCommandOption
import io.github.freya022.botcommands.internal.core.options.OptionType
import io.github.freya022.botcommands.internal.utils.ReflectionUtils.function
import io.github.freya022.botcommands.internal.utils.throwUser
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent

@BService
internal class AutocompleteListener(private val context: BContext) {
    private val applicationContext = context.applicationCommandsContext
    private val scope = context.coroutineScopesConfig.applicationCommandsScope

    @BEventListener
    internal suspend fun onAutocomplete(event: CommandAutoCompleteInteractionEvent) = scope.launch {
        val slashCommand = CommandPath.of(event.fullCommandName).let {
            context.applicationCommandsContext.findLiveSlashCommand(event.guild, it)
                ?: throwUser("A slash command could not be found: ${event.fullCommandName}")
        }

        for (option in slashCommand.parameters.flatMap { it.allOptions }) {
            if (option.optionType != OptionType.OPTION) continue
            option as SlashCommandOption

            if (option.discordName == event.focusedOption.name) {
                val autocompleteHandler = option.autocompleteHandler
                    ?: throwUser(option.kParameter.function, "Autocomplete handler was not found on parameter '${option.declaredName}'")

                return@launch event.replyChoices(autocompleteHandler.handle(event)).queue()
            }
        }
    }
}