package io.github.freya022.botcommands.internal.commands.application.mixins

import io.github.freya022.botcommands.api.commands.application.CommandScope
import io.github.freya022.botcommands.api.commands.application.builder.ApplicationCommandBuilder
import io.github.freya022.botcommands.api.commands.application.slash.builder.mixins.ITopLevelApplicationCommandBuilder
import io.github.freya022.botcommands.internal.utils.downcast
import io.github.freya022.botcommands.internal.utils.throwUser

open class TopLevelApplicationCommandInfoMixin(
    builder: ITopLevelApplicationCommandBuilder
) : ITopLevelApplicationCommandInfo {
    final override val scope: CommandScope = builder.scope
    final override val isDefaultLocked: Boolean = builder.isDefaultLocked
    final override val isGuildOnly: Boolean = scope.isGuildOnly
    final override val nsfw: Boolean = builder.nsfw

    init {
        downcast<ApplicationCommandBuilder<*>>(builder)

        //Administrators manage who can use what; the bot doesn't need to check for user mistakes
        // Why would you ask for a permission
        // if the administrators want a less-powerful user to be able to use it?
        if (isDefaultLocked && builder.userPermissions.isNotEmpty()) {
            throwUser(builder.function, "Cannot put user permissions on default locked commands")
        }

        if (!isGuildOnly && (builder.userPermissions.isNotEmpty() || builder.botPermissions.isNotEmpty())) {
            throwUser(builder.function, "Application command with permissions should be guild-only, as permissions are not applicable in DMs")
        }
    }
}