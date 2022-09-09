package com.freya02.botcommands.internal.commands.prefixed.autobuilder

import com.freya02.botcommands.api.commands.CommandPath
import com.freya02.botcommands.api.commands.annotations.GeneratedOption
import com.freya02.botcommands.api.commands.annotations.RequireOwner
import com.freya02.botcommands.api.commands.prefixed.BaseCommandEvent
import com.freya02.botcommands.api.commands.prefixed.TextCommand
import com.freya02.botcommands.api.commands.prefixed.TextCommandManager
import com.freya02.botcommands.api.commands.prefixed.annotations.*
import com.freya02.botcommands.api.commands.prefixed.builder.TextCommandBuilder
import com.freya02.botcommands.api.commands.prefixed.builder.TextCommandVariationBuilder
import com.freya02.botcommands.api.core.annotations.BService
import com.freya02.botcommands.api.parameters.ParameterType
import com.freya02.botcommands.internal.*
import com.freya02.botcommands.internal.commands.autobuilder.addFunction
import com.freya02.botcommands.internal.commands.autobuilder.fillCommandBuilder
import com.freya02.botcommands.internal.commands.autobuilder.forEachWithDelayedExceptions
import com.freya02.botcommands.internal.commands.autobuilder.nullIfEmpty
import com.freya02.botcommands.internal.commands.prefixed.TextCommandComparator
import com.freya02.botcommands.internal.commands.prefixed.autobuilder.metadata.TextFunctionMetadata
import com.freya02.botcommands.internal.core.ClassPathContainer
import com.freya02.botcommands.internal.core.requireFirstArg
import com.freya02.botcommands.internal.core.requireNonStatic
import com.freya02.botcommands.internal.utils.ReflectionUtilsKt.nonInstanceParameters
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

@BService
internal class TextCommandAutoBuilder(private val context: BContextImpl, classPathContainer: ClassPathContainer) {
    private val functions: List<TextFunctionMetadata>

    init {
        functions = classPathContainer.functionsWithAnnotation<JDATextCommand>()
            .requireNonStatic()
            .requireFirstArg(BaseCommandEvent::class).map {
                val instance = it.instance as? TextCommand
                    ?: throwUser(it.function, "Declaring class must extend ${TextCommand::class.simpleName}")
                val func = it.function
                val annotation = func.findAnnotation<JDATextCommand>() ?: throwInternal("@JDATextCommand should be present")
                val path = CommandPath.of(annotation.name, annotation.group.nullIfEmpty(), annotation.subcommand.nullIfEmpty()).also { path ->
                    if (path.group != null && path.nameCount == 2) {
                        throwUser(func, "Slash commands with groups need to have their subcommand name set")
                    }
                }

                TextFunctionMetadata(instance, func, annotation, path)
            }
    }

    fun declare(manager: TextCommandManager) {
        val containers: MutableMap<String, TextCommandContainer> = hashMapOf()

        functions.forEachWithDelayedExceptions { metadata ->
            val firstContainer = containers.computeIfAbsent(metadata.path.name) { TextCommandContainer(metadata.path.name, metadata) }
            val container = when (metadata.path.nameCount) {
                1 -> firstContainer
                else -> {
                    val split = metadata.path.fullPath.split('/')
                    split
                        .drop(1) //Skip first component as it is the initial step
                        .dropLast(1) //Navigate text command containers until n-1 path component
                        .fold(firstContainer) { acc, s ->
                            acc.subcommands.computeIfAbsent(s) { TextCommandContainer(s, null) }
                        }
                        .subcommands //Only put metadata on the last path component as this is what the annotation applies on
                        .computeIfAbsent(split.last()) { TextCommandContainer(split.last(), metadata) }
                }
            }

            container.variations.add(metadata)
        }

        containers.values.forEach { container -> processCommand(manager, container) }
    }

    private fun processCommand(manager: TextCommandManager, container: TextCommandContainer) {
        manager.textCommand(container.name) {
            container.metadata?.let { metadata ->
                processBuilder(metadata)
            }

            processVariations(container)

            container.subcommands.values.forEach { subContainer ->
                processSubcontainer(subContainer)
            }
        }
    }

    private fun TextCommandBuilder.processSubcontainer(subContainer: TextCommandContainer) {
        subcommand(subContainer.name) {
            subContainer.metadata?.let { metadata ->
                processBuilder(metadata)
            }

            subContainer.subcommands.values.forEach {
                processSubcontainer(it)
            }

            processVariations(subContainer)
        }
    }

    private fun TextCommandBuilder.processVariations(container: TextCommandContainer) {
        container
            .variations
            .sortedWith(TextCommandComparator(context)) //Sort variations as to put most complex variations first, and fallback last
            .forEach {
                variation {
                    processVariation(it)
                }
            }
    }

    private fun TextCommandVariationBuilder.processVariation(metadata: TextFunctionMetadata) {
        addFunction(metadata.func)

        processOptions(metadata.func, metadata.instance, metadata.path)
    }

    private fun TextCommandBuilder.processBuilder(metadata: TextFunctionMetadata) {
        val func = metadata.func
        val annotation = metadata.annotation
        val instance = metadata.instance

        //Only put the command function if the path specified on the function is the same as the one computed in pathComponents
        fillCommandBuilder(func)

        func.findAnnotation<Category>()?.let { category = it.value }
        aliases = annotation.aliases.toMutableList()
        description = annotation.description

        order = annotation.order
        hidden = func.hasAnnotation<Hidden>()
        ownerRequired = func.hasAnnotation<RequireOwner>()

        detailedDescription = instance.detailedDescription
    }

    private fun TextCommandVariationBuilder.processOptions(func: KFunction<*>, instance: TextCommand, path: CommandPath) {
        func.nonInstanceParameters.drop(1).forEach { kParameter ->
            when (val optionAnnotation = kParameter.findAnnotation<TextOption>()) {
                null -> when (kParameter.findAnnotation<GeneratedOption>()) {
                    null -> customOption(kParameter.findDeclarationName())
                    else -> generatedOption(
                        kParameter.findDeclarationName(), instance.getGeneratedValueSupplier(
                            path,
                            kParameter.findOptionName().asDiscordString(),
                            ParameterType.ofType(kParameter.type)
                        )
                    )
                }
                else -> option(optionAnnotation.name.nullIfEmpty() ?: kParameter.findDeclarationName()) {
                    helpExample = optionAnnotation.example.nullIfEmpty()
                    isId = kParameter.hasAnnotation<ID>()
                }
            }
        }
    }

    /**
     * @param metadata This is only the metadata of the first method encountered with the annotation
     */
    private class TextCommandContainer(val name: String, val metadata: TextFunctionMetadata?) {
        val subcommands: MutableMap<String, TextCommandContainer> = hashMapOf()
        val variations: MutableList<TextFunctionMetadata> = arrayListOf()
    }
}