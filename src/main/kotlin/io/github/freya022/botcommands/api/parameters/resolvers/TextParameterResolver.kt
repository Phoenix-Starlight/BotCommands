package io.github.freya022.botcommands.api.parameters.resolvers

import io.github.freya022.botcommands.api.commands.text.BaseCommandEvent
import io.github.freya022.botcommands.api.commands.text.annotations.ID
import io.github.freya022.botcommands.api.commands.text.annotations.JDATextCommandVariation
import io.github.freya022.botcommands.api.parameters.ParameterResolver
import io.github.freya022.botcommands.internal.commands.text.TextCommandVariation
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.regex.Pattern
import kotlin.reflect.KParameter
import kotlin.reflect.KType

/**
 * Parameter resolver for parameters of [@JDATextCommandVariation][JDATextCommandVariation].
 *
 * Needs to be implemented alongside a [ParameterResolver] subclass.
 *
 * @param T Type of the implementation
 * @param R Type of the returned resolved objects
 */
interface TextParameterResolver<T, R : Any> where T : ParameterResolver<T, R>,
                                                  T : TextParameterResolver<T, R> {
    /**
     * Returns a resolved object from this text command.
     *
     * If this returns `null`, and the parameter is required, i.e., not [nullable][KType.isMarkedNullable]
     * or [optional][KParameter.isOptional], then the handler goes to the next command variation.
     *
     * See the [@JDATextCommandVariation][JDATextCommandVariation] documentation for more details about text command variations.
     *
     * @param variation The text command variation being executed
     * @param event     The corresponding event
     * @param args      The arguments of this parameter, extracted with [pattern]
     */
    fun resolve(variation: TextCommandVariation, event: MessageReceivedEvent, args: Array<String?>): R? =
        throw NotImplementedError("${this.javaClass.simpleName} must implement the 'resolve' or 'resolveSuspend' method")

    /**
     * Returns a resolved object from this text command.
     *
     * If this returns `null`, and the parameter is required, i.e., not [nullable][KType.isMarkedNullable]
     * or [optional][KParameter.isOptional], then the handler goes to the next command variation.
     *
     * See the [@JDATextCommandVariation][JDATextCommandVariation] documentation for more details about text command variations.
     *
     * @param variation The text command variation being executed
     * @param event     The corresponding event
     * @param args      The arguments of this parameter, extracted with [pattern]
     */
    @JvmSynthetic
    suspend fun resolveSuspend(variation: TextCommandVariation, event: MessageReceivedEvent, args: Array<String?>) =
        resolve(variation, event, args)

    /**
     * The minimum group count to be extracted from the match result.
     *
     * This may be overridden if your pattern provides more groups than it actually needs to match a string,
     * such as patterns with `|`.
     */
    val requiredGroups: Int get() = preferredPattern.matcher("").groupCount()

    /**
     * Returns the pattern required to recognize this parameter
     *
     * This is used to compose a larger pattern which will represent an entire command syntax
     *
     * If you wish to apply flags to this pattern, please enable them inside the regular expression instead of on the pattern,
     * as the pattern aggregator will not take any flags into account.
     *
     * You can enable regex flags using the `(?[flags])` notation,
     * such as `(?i)` to enable case-insensitive matching, and `(?-i)` to disable it.
     *
     * Make sure to disable your modifiers when you are done using them, as they could affect other patterns.
     *
     * You can find more information about regex modifiers [here](https://www.regular-expressions.info/modifiers.html)
     *
     * @return The [Pattern] for this parameter
     */
    val pattern: Pattern

    /**
     * Returns an example string for this parameter
     *
     * This is only used to construct an example command internally and test the whole command's regex against it
     *
     * If the regex does not match the constructed example command, the framework will throw as the regex is deemed "too complex"
     *
     * @return An example string for validation purposes
     */
    val testExample: String

    val preferredPattern: Pattern
        get() = pattern

    /**
     * Returns a help example for this parameter.
     *
     * **Tip:** You may use the event as a way to get sample data (such as getting the member, channel, roles, etc...).
     *
     * @param parameter The [parameter][KParameter] of the command being shown in the help content
     * @param event     The event of the command that triggered help content to be displayed
     * @param isID      Whether this option was [marked as being an ID][ID]
     */
    fun getHelpExample(parameter: KParameter, event: BaseCommandEvent, isID: Boolean): String
}