package io.github.freya022.botcommands.test.commands.slash

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.asDisabled
import dev.minn.jda.ktx.messages.into
import dev.minn.jda.ktx.messages.reply_
import dev.minn.jda.ktx.messages.send
import io.github.freya022.botcommands.api.commands.annotations.Command
import io.github.freya022.botcommands.api.commands.application.ApplicationCommand
import io.github.freya022.botcommands.api.commands.application.slash.GuildSlashEvent
import io.github.freya022.botcommands.api.commands.application.slash.annotations.JDASlashCommand
import io.github.freya022.botcommands.api.components.Button
import io.github.freya022.botcommands.api.components.Buttons
import io.github.freya022.botcommands.api.components.annotations.ComponentTimeoutHandler
import io.github.freya022.botcommands.api.components.annotations.GroupTimeoutHandler
import io.github.freya022.botcommands.api.components.annotations.JDAButtonListener
import io.github.freya022.botcommands.api.components.annotations.RequiresComponents
import io.github.freya022.botcommands.api.components.builder.filter
import io.github.freya022.botcommands.api.components.data.ComponentTimeoutData
import io.github.freya022.botcommands.api.components.data.GroupTimeoutData
import io.github.freya022.botcommands.api.components.event.ButtonEvent
import io.github.freya022.botcommands.api.core.entities.InputUser
import io.github.freya022.botcommands.test.config.Config
import io.github.freya022.botcommands.test.filters.InVoiceChannel
import kotlinx.coroutines.TimeoutCancellationException
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import java.util.concurrent.ThreadLocalRandom
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Command
@RequiresComponents
class SlashNewButtons(
    private val buttons: Buttons
) : ApplicationCommand() {
    @JDASlashCommand(name = "new_buttons")
    suspend fun onSlashNewButtons(event: GuildSlashEvent) {
        val persistentButton = persistentGroupTest(event)
        val ephemeralButton = ephemeralGroupTest(event)
        val row = buildList {
            this += persistentButton
            this += ephemeralButton
            this += noGroupButton(event)
            if (Config.instance.testMode) {
                this += filteredButton()
            }
        }.into()

        event.reply("OK, button ID: ${persistentButton.id}").setComponents(row).queue()

        try {
//            withTimeout(5.seconds) {
                val buttonEvent: ButtonEvent = ephemeralButton.await()
                event.hook.send("Done awaiting !", ephemeral = true).queue()
//            }
        } catch (e: TimeoutCancellationException) {
            event.hook.send("Too slow", ephemeral = true).queue()
        }
    }

    private suspend fun filteredButton() = buttons.danger("Leave VC").ephemeral {
        filters += filter<InVoiceChannel>()
        bindTo {
            it.guild!!.kickVoiceMember(it.member!!).await()
            it.deferEdit().await()
        }
    }

    private suspend fun noGroupButton(event: GuildSlashEvent) =
        buttons.danger("Delete").ephemeral {
            oneUse = true
            bindTo { event.hook.deleteOriginal().queue() }
            timeout(5.seconds)
        }

    private suspend fun persistentGroupTest(event: GuildSlashEvent): Button {
        val firstButton = buttons.primary("Persistent").persistent {
            oneUse = true //Cancels whole group if used
            addUserIds(1234L)
            constraints += Permission.ADMINISTRATOR
            bindTo(PERSISTENT_BUTTON_LISTENER_NAME, ThreadLocalRandom.current().nextDouble(), event.member, null)
        }

        val secondButton = buttons.primary("Invisible").persistent {
            oneUse = true //Cancels whole group if used
            addUserIds(1234L)
            constraints += Permission.ADMINISTRATOR
            bindTo(PERSISTENT_BUTTON_LISTENER_NAME, ThreadLocalRandom.current().nextDouble(), event.member, null)
        }

        val timeoutEdButton = buttons.primary("Invisible").persistent {
            timeout(5.seconds, PERSISTENT_BUTTON_TIMEOUT_LISTENER_NAME, null)
        }

        buttons.group(firstButton, secondButton).persistent {
            timeout(10.seconds, PERSISTENT_GROUP_TIMEOUT_LISTENER_NAME, null)
        }
        return firstButton
    }

    private suspend fun ephemeralGroupTest(event: GuildSlashEvent): Button {
        val firstButton = buttons.secondary("Ephemeral").ephemeral {
            noTimeout()
            oneUse = true //Cancels whole group if used
            addUserIds(1234L)
            constraints += Permission.ADMINISTRATOR
            bindTo { evt -> evt.reply_("Ephemeral button clicked", ephemeral = true).queue() }
        }

        buttons.group(firstButton).ephemeral {
            timeout(15.minutes) {
                event.hook.retrieveOriginal()
                    .flatMap { event.hook.editOriginalComponents(it.components.asDisabled()) }
                    .queue()
            }
        }
        return firstButton
    }

    @JDAButtonListener(name = PERSISTENT_BUTTON_LISTENER_NAME)
    fun onFirstButtonClicked(event: ButtonEvent, double: Double, inputUser: InputUser, nullValue: Member?) {
        event.reply_("Persistent button clicked, double: $double, member: ${inputUser.asTag}, null: $nullValue", ephemeral = true).queue()
    }

    @ComponentTimeoutHandler(name = PERSISTENT_BUTTON_TIMEOUT_LISTENER_NAME)
    fun onTimeoutEdButtonTimeout(data: ComponentTimeoutData, nullObj: String?) {
        println("onTimeoutEdButtonTimeout: $data ; $nullObj")
    }

    @GroupTimeoutHandler(name = PERSISTENT_GROUP_TIMEOUT_LISTENER_NAME)
    fun onFirstGroupTimeout(data: GroupTimeoutData, nullObj: String?) {
        println("onFirstGroupTimeout: $data ; $nullObj")
    }

    companion object {
        private const val PERSISTENT_BUTTON_LISTENER_NAME = "SlashNewButtons: persistentButton"
        private const val PERSISTENT_BUTTON_TIMEOUT_LISTENER_NAME = "SlashNewButtons: persistentButtonTimeout"
        private const val PERSISTENT_GROUP_TIMEOUT_LISTENER_NAME = "SlashNewButtons: persistentGroupTimeout"
    }
}