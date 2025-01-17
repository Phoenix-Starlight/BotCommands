package io.github.freya022.botcommands.internal.core.options

import io.github.freya022.botcommands.api.commands.CommandOptionBuilder
import io.github.freya022.botcommands.api.core.utils.isPrimitive
import io.github.freya022.botcommands.api.core.utils.simpleNestedName
import io.github.freya022.botcommands.internal.core.options.builder.InternalAggregators
import io.github.freya022.botcommands.internal.core.options.builder.InternalAggregators.isVarargAggregator
import io.github.freya022.botcommands.internal.parameters.OptionParameter
import io.github.freya022.botcommands.internal.utils.ReflectionMetadata.isNullable
import io.github.freya022.botcommands.internal.utils.throwInternal
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

enum class OptionType {
    OPTION,
    CUSTOM,
    CONSTANT, //TODO
    GENERATED;

    override fun toString(): String {
        return "${javaClass.simpleNestedName}#$name"
    }
}

interface Option {
    val optionParameter: OptionParameter
    val optionType: OptionType

    /**
     * This might be a KParameter from the command function or from an aggregate function.
     *
     * - In the case the option was created from [the internal aggregator][InternalAggregators.theSingleAggregator], the KParameter is grabbed from the command function.
     * - In the case the option was created in a user-defined aggregator, then the KParameter is grabbed from the aggregate function.
     *
     * **Beware of the types and nullabilities**, the KParameter could be of an array type.
     *
     * **Also note:** this is not unique, multiple options can be bound to the same KParameter. (varargs for example)
     */
    val kParameter: KParameter

    /**
     * Parameter used solely when inserting the option in the aggregator function.
     *
     * May be from the command or aggregate function.
     */
    val executableParameter: KParameter

    /**
     * The parameter is a vararg if:
     * * The aggregator is [InternalAggregators.theVarargAggregator]
     * * The erased type is [List]
     */
    val isVararg: Boolean
    val isOptionalOrNullable: Boolean
    val declaredName: String
    val index: Int
    val nullValue: Any?
    val type: KType
}

internal val Option.isRequired: Boolean get() = !isOptionalOrNullable

open class OptionImpl private constructor(
    final override val optionParameter: OptionParameter,
    final override val optionType: OptionType,
    /** @see CommandOptionBuilder.isOptional */
    optional: Boolean?
) : Option {
    constructor(optionParameter: OptionParameter, optionType: OptionType) : this(optionParameter, optionType, null)

    constructor(commandOptionBuilder: CommandOptionBuilder) : this(
        commandOptionBuilder.optionParameter,
        OptionType.OPTION,
        commandOptionBuilder.isOptional
    )

    final override val kParameter: KParameter
        get() = optionParameter.typeCheckingParameter
    final override val executableParameter: KParameter
        get() = optionParameter.executableParameter

    final override val type = kParameter.type
    final override val isOptionalOrNullable by lazy {
        when {
            optional != null -> optional
            else -> kParameter.isNullable || kParameter.isOptional
        }
    }
    final override val isVararg = optionParameter.executableFunction.isVarargAggregator()
    final override val declaredName = optionParameter.typeCheckingParameterName
    final override val index = kParameter.index
    final override val nullValue = when {
        kParameter.isNullable -> null
        kParameter.isPrimitive -> primitiveDefaultValue(type.jvmErasure)
        else -> null
    }
}

private val primitiveDefaultValues: Map<KClass<*>, *> = mapOf(
    Byte::class to 0.toByte(),
    Short::class to 0.toShort(),
    Int::class to 0,
    Long::class to 0L,
    Float::class to 0.0F,
    Double::class to 0.0,
    Boolean::class to false
)

internal fun primitiveDefaultValue(clazz: KClass<*>) =
    primitiveDefaultValues[clazz] ?: throwInternal("No primitive default value for ${clazz.simpleNestedName}")