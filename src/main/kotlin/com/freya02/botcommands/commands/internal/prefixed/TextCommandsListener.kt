package com.freya02.botcommands.commands.internal.prefixed

import com.freya02.botcommands.api.CooldownScope
import com.freya02.botcommands.api.Logging
import com.freya02.botcommands.api.application.CommandPath
import com.freya02.botcommands.api.prefixed.IHelpCommand
import com.freya02.botcommands.api.prefixed.TextFilteringData
import com.freya02.botcommands.core.api.annotations.BEventListener
import com.freya02.botcommands.core.api.annotations.BService
import com.freya02.botcommands.internal.BContextImpl
import com.freya02.botcommands.internal.Usability
import com.freya02.botcommands.internal.Usability.UnusableReason
import com.freya02.botcommands.internal.getDeepestCause
import com.freya02.botcommands.internal.prefixed.BaseCommandEventImpl
import com.freya02.botcommands.internal.prefixed.ExecutionResult
import com.freya02.botcommands.internal.prefixed.TextCommandInfo
import com.freya02.botcommands.internal.prefixed.TextFindResult
import com.freya02.botcommands.internal.throwUser
import com.freya02.botcommands.internal.utils.Utils
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.internal.requests.CompletedRestAction
import java.util.regex.Matcher

@BService
internal class TextCommandsListener(private val context: BContextImpl) {
    private val logger = Logging.getLogger()
    private val spacePattern = Regex("\\s+")

    private val helpInfo: TextCommandInfo?
    private val helpCommand: IHelpCommand?

    init {
        val helpCommandInfo = context.findFirstCommand(CommandPath.ofName("help"))
        if (helpCommandInfo == null) {
            logger.debug("Help command not loaded")

            helpInfo = null
            helpCommand = null
        } else {
            helpInfo = helpCommandInfo
            helpCommand = helpInfo.instance as? IHelpCommand ?: throwUser("Help command must implement IHelpCommand")
        }
    }

    @BEventListener
    suspend fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || event.isWebhookMessage) return

        if (!event.isFromGuild) return

        val member = event.member ?: let {
            logger.error("Command caller member is null ! This shouldn't happen if the message isn't a webhook, or is the docs wrong ?")
            return
        }

        val isBotMentioned = event.message.mentions.isMentioned(event.jda.selfUser)
        if (GatewayIntent.MESSAGE_CONTENT !in event.jda.gatewayIntents && !isBotMentioned) return

        val msg: String = event.message.contentRaw
        val content = when {
            context.config.textConfig.usePingAsPrefix && msg.startsWith(event.jda.selfUser.asMention) -> msg.substringAfter(' ')
            else -> getMsgNoPrefix(msg, event.guild)
        }
        if (content.isNullOrBlank()) return

        logger.trace("Received prefixed command: {}", msg)

        try {
            var result: TextFindResult? = null
            val words: List<String> = spacePattern.split(content)
            for (index in words.indices) {
                val newResult = context.textCommandsContext.findTextCommand(words.subList(0, index + 1))
                when {
                    newResult.commands.isEmpty() -> break
                    else -> result = newResult
                }
            }

            val isNotOwner = !context.config.isOwner(member.idLong)

            if (result == null) {
                onCommandNotFound(event, CommandPath.of(words[0]), isNotOwner)
                return
            }

            val args = words.drop(result.pathComponents).joinToString(" ")
            result.commands.forEach {
                when (it.completePattern) {
                    null -> { //Fallback method
                        if (tryExecute(event, args, it, isNotOwner, null) != ExecutionResult.CONTINUE) return
                    }
                    else -> { //Regex text command
                        val matcher = it.completePattern.matcher(args)
                        if (matcher.matches()) {
                            if (tryExecute(event, args, it, isNotOwner, matcher) != ExecutionResult.CONTINUE) return
                        }
                    }
                }
            }

            if (helpCommand != null && helpInfo != null) {
                helpCommand.onInvalidCommand(
                    BaseCommandEventImpl(context, helpInfo.method, event, ""),
                    result.commands
                )
            }
        } catch (e: Throwable) {
            handleException(event, e, msg)
        }
    }

    private fun handleException(event: MessageReceivedEvent, e: Throwable, msg: String) {
        val handler = context.uncaughtExceptionHandler
        if (handler != null) {
            handler.onException(context, event, e)
            return
        }

        val baseEx = e.getDeepestCause()

        logger.error("Unhandled exception while executing a text command '{}'", msg, baseEx)

        replyError(event, context.getDefaultMessages(event.guild).generalErrorMsg)

        context.dispatchException("Exception in application command '$msg'", baseEx)
    }

    private fun getMsgNoPrefix(msg: String, guild: Guild): String? {
        return getPrefixes(guild)
            .find { prefix -> msg.startsWith(prefix) }
            ?.let { prefix -> msg.substring(prefix.length).trim() }
    }

    private fun getPrefixes(guild: Guild): List<String> {
        context.settingsProvider?.let { settingsProvider ->
            val prefixes = settingsProvider.getPrefixes(guild)
            if (!prefixes.isNullOrEmpty()) return prefixes
        }

        return context.config.textConfig.prefixes
    }

    private suspend fun tryExecute(
        event: MessageReceivedEvent,
        args: String,
        commandInfo: TextCommandInfo,
        isNotOwner: Boolean,
        matcher: Matcher?
    ): ExecutionResult {
        val filteringData = TextFilteringData(context, event, commandInfo, args)
        for (filter in context.config.textConfig.textFilters) {
            if (!filter.isAccepted(filteringData)) {
                logger.trace("Cancelled prefixed commands due to filter")
                return ExecutionResult.STOP
            }
        }

        val usability = Usability.of(context, commandInfo, event.member!!, event.guildChannel, isNotOwner)

        if (usability.isUnusable) {
            val unusableReasons = usability.unusableReasons
            if (unusableReasons.contains(UnusableReason.HIDDEN)) {
                onCommandNotFound(event, commandInfo.path, true)
                return ExecutionResult.STOP
            } else if (unusableReasons.contains(UnusableReason.OWNER_ONLY)) {
                replyError(event, context.getDefaultMessages(event.guild).ownerOnlyErrorMsg)
                return ExecutionResult.STOP
            } else if (unusableReasons.contains(UnusableReason.NSFW_DISABLED)) {
                replyError(event, context.getDefaultMessages(event.guild).nsfwDisabledErrorMsg)
                return ExecutionResult.STOP
            } else if (unusableReasons.contains(UnusableReason.NSFW_ONLY)) {
                replyError(event, context.getDefaultMessages(event.guild).nsfwOnlyErrorMsg)
                return ExecutionResult.STOP
            } else if (unusableReasons.contains(UnusableReason.NSFW_DM_DENIED)) {
                replyError(event, context.getDefaultMessages(event.guild).nsfwdmDeniedErrorMsg)
                return ExecutionResult.STOP
            } else if (unusableReasons.contains(UnusableReason.USER_PERMISSIONS)) {
                replyError(event, context.getDefaultMessages(event.guild).userPermErrorMsg)
                return ExecutionResult.STOP
            } else if (unusableReasons.contains(UnusableReason.BOT_PERMISSIONS)) {
                val missingPermsStr =
                    (commandInfo.botPermissions - event.guild.selfMember.getPermissions(event.guildChannel)).joinToString {
                        it.name
                    }

                replyError(event, context.getDefaultMessages(event.guild).getBotPermErrorMsg(missingPermsStr))
                return ExecutionResult.STOP
            }
        }

        if (isNotOwner) {
            val cooldown: Long = commandInfo.getCooldown(event)
            if (cooldown > 0) {
                val defaultMessages = context.getDefaultMessages(event.guild)
                when (commandInfo.cooldownScope) {
                    CooldownScope.USER -> replyError(event, defaultMessages.getUserCooldownMsg(cooldown / 1000.0))
                    CooldownScope.GUILD -> replyError(event, defaultMessages.getGuildCooldownMsg(cooldown / 1000.0))
                    CooldownScope.CHANNEL -> replyError(event, defaultMessages.getChannelCooldownMsg(cooldown / 1000.0))
                }

                return ExecutionResult.STOP
            }
        }

        return withContext(context.config.coroutineScopesConfig.textCommandsScope.coroutineContext) {
            commandInfo.execute(event, args, matcher)
        }
    }

    private fun replyError(event: MessageReceivedEvent, msg: String) {
        val action = when {
            event.guildChannel.canTalk() -> CompletedRestAction(event.jda, event.channel)
            else -> event.author.openPrivateChannel()
        }

        action
            .flatMap { it.sendMessage(msg) }
            .queue(null, ErrorHandler()
                .ignore(ErrorResponse.CANNOT_SEND_TO_USER)
                .handle(Exception::class.java) { e: Exception ->
                    Utils.printExceptionString("Could not send reply message from command listener", e)
                    context.dispatchException("Could not send reply message from command listener", e)
                })
    }

    private fun onCommandNotFound(event: MessageReceivedEvent, commandName: CommandPath, isNotOwner: Boolean) {
        val suggestions = getSuggestions(event, commandName, isNotOwner)
        if (suggestions.isNotEmpty()) {
            replyError(
                event,
                context.getDefaultMessages(event.guild).getCommandNotFoundMsg(suggestions.joinToString("**, **", "**", "**"))
            )
        }
    }

    private fun getSuggestions(event: MessageReceivedEvent, triedCommandPath: CommandPath, isNotOwner: Boolean): List<String> {
        return listOf()
    }
}