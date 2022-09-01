package com.freya02.botcommands.api.commands.application.slash.builder

import com.freya02.botcommands.internal.BContextImpl

class SlashSubcommandGroupBuilder(private val context: BContextImpl, val name: String) {
    @get:JvmSynthetic
    internal val subcommands: MutableList<SlashCommandBuilder> = mutableListOf()

    fun subcommand(name: String, block: SlashCommandBuilder.() -> Unit) {
        subcommands += SlashCommandBuilder(context, name).apply(block)
    }
}
