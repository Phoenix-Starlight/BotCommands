package io.github.freya022.botcommands.api.commands.annotations

import net.dv8tion.jda.api.Permission

/**
 * Sets the required bot permissions to use this text / application command.
 *
 * **Text commands note:** This applies to the command itself, not only this variation,
 * in other words, this applies to all commands with the same path.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BotPermissions(@get:JvmName("value") vararg val permissions: Permission = [], val append: Boolean = false)
