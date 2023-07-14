package com.freya02.botcommands.api.commands.builder

import com.freya02.botcommands.api.commands.CommandPath
import com.freya02.botcommands.api.commands.CooldownScope
import com.freya02.botcommands.api.core.utils.enumSetOf
import com.freya02.botcommands.internal.commands.CommandDSL
import com.freya02.botcommands.internal.commands.CooldownStrategy
import com.freya02.botcommands.internal.commands.mixins.INamedCommand
import com.freya02.botcommands.internal.commands.mixins.INamedCommand.Companion.computePath
import net.dv8tion.jda.api.Permission
import java.util.*
import java.util.concurrent.TimeUnit

@CommandDSL
abstract class CommandBuilder internal constructor(override val name: String) : INamedCommand {
    var userPermissions: EnumSet<Permission> = enumSetOf()
    var botPermissions: EnumSet<Permission> = enumSetOf()

    final override val path: CommandPath by lazy { computePath() }

    internal var cooldownStrategy: CooldownStrategy = CooldownStrategy(0, TimeUnit.SECONDS, CooldownScope.USER)
        private set

    fun cooldown(block: CooldownStrategyBuilder.() -> Unit) {
        cooldownStrategy = CooldownStrategyBuilder().apply(block).build()
    }
}
