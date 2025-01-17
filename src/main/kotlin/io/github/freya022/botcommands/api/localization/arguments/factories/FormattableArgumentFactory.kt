package io.github.freya022.botcommands.api.localization.arguments.factories

import io.github.freya022.botcommands.api.core.config.BServiceConfigBuilder
import io.github.freya022.botcommands.api.core.service.annotations.BService
import io.github.freya022.botcommands.api.core.service.annotations.InterfacedService
import io.github.freya022.botcommands.api.localization.arguments.FormattableArgument
import java.util.*

/**
 * Factory for [formattable arguments][FormattableArgument].
 *
 * **Usage**: Register your instance as a service with [@BService][BService]
 * or [any annotation that enables your class for dependency injection][BServiceConfigBuilder.serviceAnnotations].
 *
 * @see FormattableArgument
 * @see InterfacedService @InterfacedService
 */
@InterfacedService(acceptMultiple = true)
interface FormattableArgumentFactory {
    val regex: Regex

    fun get(matchResult: MatchResult, locale: Locale): FormattableArgument
}