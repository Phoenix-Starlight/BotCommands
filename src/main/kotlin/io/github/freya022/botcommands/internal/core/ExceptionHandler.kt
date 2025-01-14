package io.github.freya022.botcommands.internal.core

import io.github.freya022.botcommands.api.core.BContext
import io.github.freya022.botcommands.internal.utils.unwrap
import io.github.oshai.kotlinlogging.KLogger
import net.dv8tion.jda.api.events.Event

internal class ExceptionHandler(private val context: BContext, private val logger: KLogger) {
    fun handleException(event: Event?, e: Throwable, locationDescription: String, extraContext: Map<String, Any?> = emptyMap()) {
        val unreflectedException = e.unwrap()
        val handler = context.globalExceptionHandler
        if (handler != null) return handler.onException(event, unreflectedException)

        val errorMessage = "Uncaught exception in $locationDescription"
        logger.error(unreflectedException) { errorMessage }
        context.dispatchException(errorMessage, unreflectedException, extraContext)
    }
}