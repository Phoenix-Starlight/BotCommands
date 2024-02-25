package io.github.freya022.botcommands.internal.commands.application.slash.autocomplete

import io.github.freya022.botcommands.api.commands.CommandPath
import io.github.freya022.botcommands.api.core.BContext
import io.github.freya022.botcommands.api.core.annotations.BEventListener
import io.github.freya022.botcommands.api.core.service.annotations.BService
import io.github.freya022.botcommands.internal.commands.application.slash.SlashCommandOption
import io.github.freya022.botcommands.internal.core.ExceptionHandler
import io.github.freya022.botcommands.internal.core.options.OptionType
import io.github.freya022.botcommands.internal.utils.ReflectionUtils.function
import io.github.freya022.botcommands.internal.utils.launchCatching
import io.github.freya022.botcommands.internal.utils.throwUser
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse

private val logger = KotlinLogging.logger { }

@BService
internal class AutocompleteListener(private val context: BContext) {
    private val applicationContext = context.applicationCommandsContext
    private val scope = context.coroutineScopesConfig.applicationCommandsScope
    private val exceptionHandler = ExceptionHandler(context, logger)

    @BEventListener
    internal suspend fun onAutocomplete(event: CommandAutoCompleteInteractionEvent) {
        logger.trace { "Received autocomplete interaction for '${event.focusedOption.name}' on '${event.commandString}'" }

        scope.launchCatching({ handleException(it, event) }) launch@{
            val slashCommand = CommandPath.of(event.fullCommandName).let {
                applicationContext.findLiveSlashCommand(event.guild, it)
                    // Ignore, if the user tries to use a command we don't know,
                    // it's going to be handled by the slash command handler
                    ?: return@launch onCommandNotFound(event)
            }

            for (option in slashCommand.parameters.flatMap { it.allOptions }) {
                if (option.optionType != OptionType.OPTION) continue
                option as SlashCommandOption

                if (option.discordName == event.focusedOption.name) {
                    val autocompleteHandler = option.autocompleteHandler
                        ?: throwUser(option.kParameter.function, "Autocomplete handler was not found on parameter '${option.declaredName}'")

                    return@launch event.replyChoices(autocompleteHandler.handle(event)).queue(null) { onReplyException(event, it) }
                }
            }
        }
    }

    private fun onCommandNotFound(event: CommandAutoCompleteInteractionEvent) {
        // In rare cases where a user sends an autocomplete request before the commands have been registered
        // Log on DEBUG as the exception is going to be more apparent when the user executes the command
        val guildMap = applicationContext.getLiveApplicationCommandsMap(event.guild)
        val globalMap = applicationContext.getLiveApplicationCommandsMap(null)
        if (guildMap == null || globalMap == null) {
            logger.debug { "Ignoring autocomplete request for '${event.fullCommandName}' in guild '${event.guild?.name}' (${event.guild?.id}) as the commands haven't loaded yet" }
        } else {
            logger.debug { "Ignoring autocomplete request for '${event.fullCommandName}' in guild '${event.guild?.name}' (${event.guild?.id}) as the command does not exist" }
        }

        return event.replyChoices().queue()
    }

    private fun onReplyException(event: CommandAutoCompleteInteractionEvent, e: Throwable) {
        if (e is ErrorResponseException && e.errorResponse == ErrorResponse.UNKNOWN_INTERACTION) {
            return logger.debug { "Ignored UNKNOWN_INTERACTION on autocomplete" }
        }

        // Doesn't happen realistically, the exception only comes from replyChoices
        context.dispatchException("An uncaught exception occurred while replying autocomplete values for '${event.commandString}'", e)
    }

    private fun handleException(e: Throwable, event: CommandAutoCompleteInteractionEvent) {
        exceptionHandler.handleException(event, e, "autocomplete in '${event.commandString}' for '${event.focusedOption.name}' with value '${event.focusedOption.value}'", emptyMap())
    }
}