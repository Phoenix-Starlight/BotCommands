package com.freya02.botcommands.internal

import com.freya02.botcommands.api.core.options.annotations.Aggregate
import com.freya02.botcommands.api.core.options.builder.OptionAggregateBuilder
import com.freya02.botcommands.api.core.options.builder.OptionBuilder
import com.freya02.botcommands.internal.core.options.builder.InternalAggregators
import com.freya02.botcommands.internal.parameters.AggregatorParameter
import com.freya02.botcommands.internal.utils.ReflectionUtils.function
import com.freya02.botcommands.internal.utils.ReflectionUtils.nonInstanceParameters
import com.freya02.botcommands.internal.utils.findDeclarationName
import com.freya02.botcommands.internal.utils.throwUser
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

internal class BasicOptionAggregateBuilder(
    aggregatorParameter: AggregatorParameter,
    aggregator: KFunction<*>
) : OptionAggregateBuilder<BasicOptionAggregateBuilder>(aggregatorParameter, aggregator) {
    override fun constructNestedAggregate(aggregatorParameter: AggregatorParameter, aggregator: KFunction<*>) =
        BasicOptionAggregateBuilder(aggregatorParameter, aggregator)
}

internal inline fun <reified T : OptionAggregateBuilder<*>, R> Map<String, T>.transform(aggregateBlock: (T) -> R) =
    values.map(aggregateBlock)

internal fun <R> List<KParameter>.transformParameters(
    builderBlock: (function: KFunction<*>, parameter: KParameter, declaredName: String) -> OptionBuilder,
    aggregateBlock: (OptionAggregateBuilder<*>) -> R
) = associate { parameter ->
    val declaredName = parameter.findDeclarationName()
    declaredName to when {
        parameter.hasAnnotation<Aggregate>() -> {
            val constructor = parameter.type.jvmErasure.primaryConstructor
                ?: throwUser(parameter.function, "Found no constructor for aggregate type ${parameter.type}")
            BasicOptionAggregateBuilder(AggregatorParameter.fromUserAggregate(constructor, declaredName), constructor).apply {
                constructor.nonInstanceParameters.forEach { constructorParameter ->
                    this += builderBlock(constructor, constructorParameter, constructorParameter.findDeclarationName())
                }
            }
        }

        else -> BasicOptionAggregateBuilder(
            AggregatorParameter.fromSelfAggregate(parameter.function, declaredName),
            InternalAggregators.theSingleAggregator
        ).apply {
            this += builderBlock(parameter.function, parameter, declaredName)
        }
    }
}.transform(aggregateBlock)