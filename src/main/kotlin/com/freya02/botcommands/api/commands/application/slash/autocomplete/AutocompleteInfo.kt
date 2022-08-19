package com.freya02.botcommands.api.commands.application.slash.autocomplete

import com.freya02.botcommands.api.commands.application.slash.autocomplete.builder.AutocompleteInfoBuilder
import com.freya02.botcommands.internal.requireUser
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import kotlin.reflect.KFunction
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure

class AutocompleteInfo internal constructor(val builder: AutocompleteInfoBuilder) {
    val method: KFunction<Collection<*>> = builder.function
    val mode: AutocompleteMode = builder.mode
    val showUserInput: Boolean = builder.showUserInput
    val autocompleteCache: AutocompleteCacheInfo? = builder.autocompleteCache

    init {
        requireUser(method.valueParameters.firstOrNull()?.type?.jvmErasure == CommandAutoCompleteInteractionEvent::class, method) {
            "First parameter must be a CommandAutoCompleteInteractionEvent"
        }
    }
}