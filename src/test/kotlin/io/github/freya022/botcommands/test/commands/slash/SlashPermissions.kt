package io.github.freya022.botcommands.test.commands.slash

import dev.minn.jda.ktx.messages.reply_
import io.github.freya022.botcommands.api.commands.annotations.BotPermissions
import io.github.freya022.botcommands.api.commands.annotations.Command
import io.github.freya022.botcommands.api.commands.annotations.UserPermissions
import io.github.freya022.botcommands.api.commands.application.ApplicationCommand
import io.github.freya022.botcommands.api.commands.application.provider.GlobalApplicationCommandManager
import io.github.freya022.botcommands.api.commands.application.provider.GlobalApplicationCommandProvider
import io.github.freya022.botcommands.api.commands.application.slash.GuildSlashEvent
import io.github.freya022.botcommands.api.commands.application.slash.annotations.JDASlashCommand
import io.github.freya022.botcommands.api.core.utils.enumSetOf
import net.dv8tion.jda.api.Permission

@Command
class SlashPermissions : ApplicationCommand(), GlobalApplicationCommandProvider {
    @BotPermissions(Permission.MANAGE_EVENTS)
    @UserPermissions(Permission.MANAGE_SERVER, Permission.ADMINISTRATOR)
    @JDASlashCommand(name = "permissions_annotated")
    fun onSlashPermissions(event: GuildSlashEvent) {
        event.reply_("Granted", ephemeral = true).queue()
    }

    override fun declareGlobalApplicationCommands(manager: GlobalApplicationCommandManager) {
        manager.slashCommand("permissions", function = ::onSlashPermissions) {
            botPermissions += Permission.MANAGE_EVENTS
            userPermissions = enumSetOf(Permission.MANAGE_SERVER, Permission.ADMINISTRATOR)
        }
    }
}