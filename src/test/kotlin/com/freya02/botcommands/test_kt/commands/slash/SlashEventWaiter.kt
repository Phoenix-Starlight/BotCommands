package com.freya02.botcommands.test_kt.commands.slash

import com.freya02.botcommands.api.commands.annotations.Command
import com.freya02.botcommands.api.commands.application.ApplicationCommand
import com.freya02.botcommands.api.commands.application.slash.GuildSlashEvent
import com.freya02.botcommands.api.commands.application.slash.annotations.JDASlashCommand
import com.freya02.botcommands.api.core.waiter.EventWaiter
import com.freya02.botcommands.api.core.waiter.await
import dev.minn.jda.ktx.messages.reply_
import mu.KotlinLogging
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val logger = KotlinLogging.logger { }

@Command
class SlashEventWaiter(private val eventWaiter: EventWaiter) : ApplicationCommand() {
    @JDASlashCommand(name = "waiter")
    suspend fun onSlashWaiter(event: GuildSlashEvent) {
        event.reply_("Send a message in 5 seconds", ephemeral = true).queue()

        try {
            val receivedContent = eventWaiter.of(MessageReceivedEvent::class.java)
                .addPrecondition { it.channel.idLong == event.channel.idLong }
                .addPrecondition { it.author.idLong == event.user.idLong }
                .setOnComplete { _, evt, throwable -> logger.debug { "Received event: $evt, or throwable: $throwable" } }
                .setOnSuccess { logger.debug { "Succeeded with event $it" } }
                .setTimeout(5, TimeUnit.SECONDS)
                .setOnTimeout { logger.debug { "Timeout !" } }
                .await()
                .message
                .contentRaw

            event.hook.editOriginal("You said '$receivedContent'").queue()
        } catch (e: TimeoutException) {
            event.hook.editOriginal("Timeout !")
                .delay(5.seconds.toJavaDuration())
                .flatMap { event.hook.deleteOriginal() }
                .queue()
        }
    }
}