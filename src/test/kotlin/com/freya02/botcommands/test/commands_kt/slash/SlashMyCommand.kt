package com.freya02.botcommands.test.commands_kt.slash

import com.freya02.botcommands.annotations.api.annotations.CommandMarker
import com.freya02.botcommands.annotations.api.application.annotations.AppOption
import com.freya02.botcommands.annotations.api.application.annotations.GeneratedOption
import com.freya02.botcommands.annotations.api.application.slash.annotations.ChannelTypes
import com.freya02.botcommands.annotations.api.application.slash.annotations.JDASlashCommand
import com.freya02.botcommands.annotations.api.application.slash.annotations.LongRange
import com.freya02.botcommands.annotations.api.application.slash.autocomplete.annotations.AutocompleteHandler
import com.freya02.botcommands.annotations.api.application.slash.autocomplete.annotations.CacheAutocomplete
import com.freya02.botcommands.api.BContext
import com.freya02.botcommands.api.annotations.Declaration
import com.freya02.botcommands.api.annotations.Name
import com.freya02.botcommands.api.application.ApplicationCommand
import com.freya02.botcommands.api.application.CommandPath
import com.freya02.botcommands.api.application.GlobalApplicationCommandManager
import com.freya02.botcommands.api.application.ValueRange.Companion.range
import com.freya02.botcommands.api.application.slash.GeneratedValueSupplier
import com.freya02.botcommands.api.application.slash.GuildSlashEvent
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompleteCacheMode
import com.freya02.botcommands.api.parameters.ParameterType
import com.freya02.botcommands.internal.enumSetOf
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice

private const val autocompleteHandlerName = "MyCommand: autocompleteStr"

@CommandMarker
class SlashMyCommand : ApplicationCommand() {
    override fun getOptionChoices(guild: Guild?, commandPath: CommandPath, optionIndex: Int): List<Choice> {
        if (optionIndex == 0) {
            return listOf(Choice("a", "a"), Choice("b", "b"), Choice("c", "c"))
        } else if (optionIndex == 1) {
            return listOf(Choice("1", 1L), Choice("2", 2L))
        }

        return super.getOptionChoices(guild, commandPath, optionIndex)
    }

    override fun getGeneratedValueSupplier(
        guild: Guild?,
        commandId: String?,
        commandPath: CommandPath,
        optionName: String,
        parameterType: ParameterType
    ): GeneratedValueSupplier {
        if (optionName == "guild_name") {
            return GeneratedValueSupplier {
                it.guild!!.name
            }
        }

        return super.getGeneratedValueSupplier(guild, commandId, commandPath, optionName, parameterType)
    }

    @JDASlashCommand(name = "my_command_annotated", description = "mah desc")
    fun executeCommand(
        event: GuildSlashEvent,
        @AppOption(name = "string_annotated", description = "Option description") stringOption: String,
        @AppOption(name = "int_annotated", description = "An integer") @LongRange(from = 1, to = 2) @Name("int", "notIntOption") intOption: Int,
        @AppOption(name = "user_annotated", description = "An user") @Name(name = "user") userOption: User,
        @AppOption(name = "channel_annot_annotated") @ChannelTypes(ChannelType.CATEGORY) channelOptionAnnot: Category,
        @AppOption(name = "channel_annotated", description = "An integer") channelOption: TextChannel,
        @AppOption(name = "autocomplete_str_annotated", description = "Autocomplete !", autocomplete = autocompleteHandlerName) autocompleteStr: String,
        @AppOption(name = "double_annotated", description = "A double") @Name(declaredName = "notDoubleOption") doubleOption: Double?,
        custom: BContext,
        @GeneratedOption guildName: String
    ) {
        event.reply("""
                    event: $event
                    string: $stringOption
                    int: $intOption
                    user: $userOption
                    category: $channelOptionAnnot
                    text channel: $channelOption
                    autocomplete string: $autocompleteStr
                    double: $doubleOption
                    custom: $custom
                    [generated] guild name: $guildName
        """.trimIndent()).queue()
    }

    @AutocompleteHandler(name = autocompleteHandlerName)
    @CacheAutocomplete(cacheMode = AutocompleteCacheMode.CONSTANT_BY_KEY)
    fun runAutocomplete(
        event: CommandAutoCompleteInteractionEvent,
        stringOption: String,
        @Name(declaredName = "notDoubleOption") doubleOption: Double?
    ): Collection<Choice> {
        println("ran")

        return listOf(Choice("lol name + $stringOption + $doubleOption", "lol value + $stringOption + $doubleOption"))
    }

    @Declaration
    fun declare(manager: GlobalApplicationCommandManager) {
        for ((subname, localFunction) in mapOf("kt" to ::executeCommand, "java" to SlashMyJavaCommand::cmd)) {
            manager.slashCommand(CommandPath.of("my_command", subname)) {
                description = "mah desc"

                option("stringOption") {
                    description = "Option description"

                    choices = listOf(Choice("a", "a"), Choice("b", "b"), Choice("c", "c"))
                }

                option("notIntOption") {
                    description = "An integer"

                    valueRange = 1 range 2

                    choices = listOf(Choice("1", 1L), Choice("2", 2L))
                }

                option("notDoubleOption") {
                    description = "A double"
                }

                option("userOption") {
                    description = "An user"
                }

                option("channelOptionAnnot")

                option("channelOption") {
                    channelTypes = enumSetOf(ChannelType.TEXT)
                }

                customOption("custom")

                option("autocompleteStr") {
                    description = "Autocomplete !"

                    autocomplete {
                        function = ::runAutocomplete

                        cache {
                            cacheMode = AutocompleteCacheMode.CONSTANT_BY_KEY
                        }
                    }
                }

                generatedOption("guildName") {
                    it.guild!!.name
                }

                function = localFunction
            }
        }
    }
}