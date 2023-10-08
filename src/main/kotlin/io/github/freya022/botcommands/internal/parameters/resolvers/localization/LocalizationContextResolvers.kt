package io.github.freya022.botcommands.internal.parameters.resolvers.localization

import io.github.freya022.botcommands.api.localization.context.AppLocalizationContext
import io.github.freya022.botcommands.api.localization.context.TextLocalizationContext
import io.github.freya022.botcommands.api.parameters.ICustomResolver
import io.github.freya022.botcommands.api.parameters.ParameterResolver
import io.github.freya022.botcommands.internal.IExecutableInteractionInfo
import io.github.freya022.botcommands.internal.localization.LocalizationContextImpl
import io.github.freya022.botcommands.internal.utils.throwInternal
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.Interaction

internal class AppLocalizationContextResolver(private val baseContext: LocalizationContextImpl) :
    ParameterResolver<AppLocalizationContextResolver, AppLocalizationContext>(AppLocalizationContext::class),
    ICustomResolver<AppLocalizationContextResolver, AppLocalizationContext> {

    override suspend fun resolveSuspend(executableInteractionInfo: IExecutableInteractionInfo, event: Event): AppLocalizationContext {
        return when (event) {
            is Interaction -> baseContext.withLocales(event.guildLocale, event.userLocale)
            //MessageReceivedEvent does not provide user locale
            else -> throwInternal("Unsupported event type for localization contexts: ${event.javaClass.name}")
        }
    }
}

internal class TextLocalizationContextResolver(private val baseContext: LocalizationContextImpl) :
    ParameterResolver<TextLocalizationContextResolver, TextLocalizationContext>(TextLocalizationContext::class),
    ICustomResolver<TextLocalizationContextResolver, TextLocalizationContext> {

    override suspend fun resolveSuspend(executableInteractionInfo: IExecutableInteractionInfo, event: Event): TextLocalizationContext {
        return when (event) {
            is Interaction -> baseContext.withLocales(event.guildLocale, event.userLocale)
            is MessageReceivedEvent -> when {
                event.isFromGuild -> baseContext.withGuildLocale(event.guild.locale)
                else -> baseContext
            }
            else -> throwInternal("Unsupported event type for localization contexts: ${event.javaClass.name}")
        }
    }
}