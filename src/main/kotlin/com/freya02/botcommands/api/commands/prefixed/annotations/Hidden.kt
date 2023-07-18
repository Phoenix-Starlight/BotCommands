package com.freya02.botcommands.api.commands.prefixed.annotations

import com.freya02.botcommands.api.commands.prefixed.builder.TextCommandBuilder

/**
 * Hides a command from help content and execution.
 *
 * @see TextCommandBuilder.hidden DSL equivalent
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Hidden  