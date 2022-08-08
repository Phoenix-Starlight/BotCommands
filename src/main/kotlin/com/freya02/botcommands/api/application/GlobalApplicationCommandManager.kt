package com.freya02.botcommands.api.application

import com.freya02.botcommands.api.application.builder.SlashCommandBuilder
import com.freya02.botcommands.api.application.builder.UserCommandBuilder
import com.freya02.botcommands.internal.BContextImpl
import com.freya02.botcommands.internal.application.ApplicationCommandInfo
import com.freya02.botcommands.internal.throwUser

class GlobalApplicationCommandManager internal constructor(val context: BContextImpl): IApplicationCommandManager() {
    override val guildApplicationCommands: MutableList<ApplicationCommandInfo> = arrayListOf()

    override fun slashCommand0(path: CommandPath, scope: CommandScope, builder: SlashCommandBuilder.() -> Unit) {
        if (!scope.isGlobal) throwUser("You can only use global scopes in a GlobalApplicationCommandManager")

        guildApplicationCommands += SlashCommandBuilder(context, path, scope)
            .apply(builder)
            .build()
    }

    override fun userCommand0(name: String, scope: CommandScope, builder: UserCommandBuilder.() -> Unit) {
        if (!scope.isGlobal) throwUser("You can only use global scopes in a GlobalApplicationCommandManager")

        guildApplicationCommands += UserCommandBuilder(context, CommandPath.ofName(name), scope)
            .apply(builder)
            .build()
    }
}