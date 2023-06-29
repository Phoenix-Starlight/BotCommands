package com.freya02.botcommands.internal.commands.application

import com.freya02.botcommands.api.commands.CommandPath
import com.freya02.botcommands.api.commands.CooldownScope
import com.freya02.botcommands.api.commands.application.ApplicationFilteringData
import com.freya02.botcommands.api.core.annotations.BEventListener
import com.freya02.botcommands.api.core.service.annotations.BService
import com.freya02.botcommands.api.core.utils.getMissingPermissions
import com.freya02.botcommands.internal.BContextImpl
import com.freya02.botcommands.internal.ExceptionHandler
import com.freya02.botcommands.internal.Usability
import com.freya02.botcommands.internal.Usability.UnusableReason
import com.freya02.botcommands.internal.commands.application.slash.SlashCommandInfo
import com.freya02.botcommands.internal.core.CooldownService
import com.freya02.botcommands.internal.utils.throwInternal
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent

@BService
internal class ApplicationCommandListener(private val context: BContextImpl, private val cooldownService: CooldownService) {
    private val logger = KotlinLogging.logger {  }
    private val exceptionHandler = ExceptionHandler(context, logger)

    @BEventListener
    suspend fun onSlashCommand(event: SlashCommandInteractionEvent) {
        logger.trace { "Received slash command: ${reconstructCommand(event)}" }

        context.coroutineScopesConfig.applicationCommandsScope.launch {
            try {
                val slashCommand = CommandPath.of(event.fullCommandName).let {
                    context.applicationCommandsContext.findLiveSlashCommand(event.guild, it)
                        ?: return@launch onCommandNotFound(event, "A slash command could not be found: ${event.fullCommandName}")
                }

                if (!canRun(event, slashCommand)) return@launch
                slashCommand.execute(event, cooldownService)
            } catch (e: Throwable) {
                handleException(e, event)
            }
        }
    }

    @BEventListener
    suspend fun onUserContextCommand(event: UserContextInteractionEvent) {
        logger.trace { "Received user context command: ${reconstructCommand(event)}" }

        context.coroutineScopesConfig.applicationCommandsScope.launch {
            try {
                val userCommand = event.name.let {
                    context.applicationCommandsContext.findLiveUserCommand(event.guild, it)
                        ?: return@launch onCommandNotFound(event, "A user context command could not be found: ${event.name}")
                }

                if (!canRun(event, userCommand)) return@launch
                userCommand.execute(event, cooldownService)
            } catch (e: Throwable) {
                handleException(e, event)
            }
        }
    }

    @BEventListener
    suspend fun onMessageContextCommand(event: MessageContextInteractionEvent) {
        logger.trace { "Received message context command: ${reconstructCommand(event)}" }

        context.coroutineScopesConfig.applicationCommandsScope.launch {
            try {
                val messageCommand = event.name.let {
                    context.applicationCommandsContext.findLiveMessageCommand(event.guild, it)
                        ?: return@launch onCommandNotFound(event, "A message context command could not be found: ${event.name}")
                }

                if (!canRun(event, messageCommand)) return@launch
                messageCommand.execute(event, cooldownService)
            } catch (e: Throwable) {
                handleException(e, event)
            }
        }
    }

    private fun onCommandNotFound(event: GenericCommandInteractionEvent, message: String) {
        //This is done so warnings are printed after the exception
        handleException(IllegalArgumentException(message), event)
        printAvailableCommands(event)
    }

    private fun printAvailableCommands(event: GenericCommandInteractionEvent) {
        val guild = event.guild
        logger.debug {
            val commandsMap = context.applicationCommandsContext.getEffectiveApplicationCommandsMap(guild)
            val scopeName = if (guild != null) "'" + guild.name + "'" else "Global scope"
            val availableCommands = commandsMap.allApplicationCommands
                .map { commandInfo ->
                    when (commandInfo) {
                        is SlashCommandInfo -> "/" + commandInfo.path.getFullPath(' ')
                        else -> commandInfo.path.fullPath
                    }
                }
                .sorted()
                .joinToString("\n")
            "Commands available in $scopeName:\n$availableCommands"
        }

        if (context.applicationConfig.onlineAppCommandCheckEnabled) {
            logger.warn(
                """
                    An application command could not be recognized even though online command check was performed. An update will be forced.
                    Please check if you have another bot instance running as it could have replaced the current command set.
                    Do not share your tokens with anyone else (even your friend), and use a separate token when testing.
                """.trimIndent()
            )
            if (guild != null) {
                context.applicationCommandsContext.updateGuildApplicationCommands(guild, force = true).whenComplete { _, e ->
                    if (e != null)
                        logger.error("An exception occurred while trying to update commands of guild '${guild.name}' (${guild.id}) after a command was missing", e)
                }
            } else {
                context.applicationCommandsContext.updateGlobalApplicationCommands(force = true).whenComplete { _, e ->
                    if (e != null)
                        logger.error("An exception occurred while trying to update global commands after a command was missing", e)
                }
            }
        }
    }

    private fun handleException(e: Throwable, event: GenericCommandInteractionEvent) {
        exceptionHandler.handleException(event, e, "application command '${reconstructCommand(event)}'")

        val generalErrorMsg = context.getDefaultMessages(event).generalErrorMsg
        when {
            event.isAcknowledged -> event.hook.sendMessage(generalErrorMsg).setEphemeral(true).queue()
            else -> event.reply(generalErrorMsg).setEphemeral(true).queue()
        }
    }

    private fun canRun(event: GenericCommandInteractionEvent, applicationCommand: ApplicationCommandInfo): Boolean {
        val applicationFilteringData = ApplicationFilteringData(context, event, applicationCommand)
        for (applicationFilter in context.applicationConfig.applicationFilters) {
            if (!applicationFilter.isAccepted(applicationFilteringData)) {
                logger.trace("Cancelled application commands due to filter")
                return false
            }
        }

        val isNotOwner = !context.isOwner(event.user.idLong)
        val usability = Usability.of(event, applicationCommand, isNotOwner)
        if (usability.isUnusable) {
            val unusableReasons = usability.unusableReasons
            when {
                UnusableReason.OWNER_ONLY in unusableReasons -> {
                    reply(event, context.getDefaultMessages(event).ownerOnlyErrorMsg)
                    return false
                }
                UnusableReason.NSFW_DISABLED in unusableReasons -> {
                    reply(event, context.getDefaultMessages(event).nsfwDisabledErrorMsg)
                    return false
                }
                UnusableReason.NSFW_ONLY in unusableReasons -> {
                    reply(event, context.getDefaultMessages(event).nsfwOnlyErrorMsg)
                    return false
                }
                UnusableReason.NSFW_DM_DENIED in unusableReasons -> {
                    reply(event, context.getDefaultMessages(event).nsfwdmDeniedErrorMsg)
                    return false
                }
                UnusableReason.USER_PERMISSIONS in unusableReasons -> {
                    val member = event.member ?: throwInternal("USER_PERMISSIONS got checked even if guild is null")
                    val missingPermissions = getMissingPermissions(applicationCommand.userPermissions, member, event.guildChannel)
                    reply(event, context.getDefaultMessages(event).getUserPermErrorMsg(missingPermissions))
                    return false
                }
                UnusableReason.BOT_PERMISSIONS in unusableReasons -> {
                    val guild = event.guild ?: throwInternal("BOT_PERMISSIONS got checked even if guild is null")
                    val missingPermissions = getMissingPermissions(applicationCommand.botPermissions, guild.selfMember, event.guildChannel)
                    reply(event, context.getDefaultMessages(event).getBotPermErrorMsg(missingPermissions))
                    return false
                }
            }
        }

        if (isNotOwner) {
            val cooldown = cooldownService.getCooldown(applicationCommand, event)
            if (cooldown > 0) {
                val messages = context.getDefaultMessages(event)

                when (applicationCommand.cooldownStrategy.scope) {
                    CooldownScope.USER -> reply(event, messages.getUserCooldownMsg(cooldown / 1000.0))
                    CooldownScope.GUILD -> reply(event, messages.getGuildCooldownMsg(cooldown / 1000.0))
                    //Implicit CooldownScope.CHANNEL
                    else -> reply(event, messages.getChannelCooldownMsg(cooldown / 1000.0))
                }

                return false
            }
        }

        return true
    }

    private fun reply(event: GenericCommandInteractionEvent, msg: String) {
        event.reply_(msg, ephemeral = true)
            .queue(null) { exceptionHandler.handleException(event, it, "interaction reply") }
    }

    private fun reconstructCommand(event: GenericCommandInteractionEvent): String {
        return when (event) {
            is SlashCommandInteractionEvent -> event.commandString
            else -> event.name
        }
    }
}