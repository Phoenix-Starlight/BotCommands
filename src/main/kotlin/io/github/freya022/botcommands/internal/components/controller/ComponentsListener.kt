package io.github.freya022.botcommands.internal.components.controller

import dev.minn.jda.ktx.messages.reply_
import io.github.freya022.botcommands.api.commands.ratelimit.CancellableRateLimit
import io.github.freya022.botcommands.api.components.ComponentInteractionFilter
import io.github.freya022.botcommands.api.components.ComponentInteractionRejectionHandler
import io.github.freya022.botcommands.api.components.Components
import io.github.freya022.botcommands.api.components.annotations.JDAButtonListener
import io.github.freya022.botcommands.api.components.annotations.JDASelectMenuListener
import io.github.freya022.botcommands.api.components.annotations.RequiresComponents
import io.github.freya022.botcommands.api.components.event.ButtonEvent
import io.github.freya022.botcommands.api.components.event.EntitySelectEvent
import io.github.freya022.botcommands.api.components.event.StringSelectEvent
import io.github.freya022.botcommands.api.core.BContext
import io.github.freya022.botcommands.api.core.Filter
import io.github.freya022.botcommands.api.core.annotations.BEventListener
import io.github.freya022.botcommands.api.core.checkFilters
import io.github.freya022.botcommands.api.core.config.BComponentsConfigBuilder
import io.github.freya022.botcommands.api.core.service.annotations.BService
import io.github.freya022.botcommands.api.core.utils.simpleNestedName
import io.github.freya022.botcommands.internal.commands.ratelimit.withRateLimit
import io.github.freya022.botcommands.internal.components.ComponentType
import io.github.freya022.botcommands.internal.components.data.AbstractComponentData
import io.github.freya022.botcommands.internal.components.data.EphemeralComponentData
import io.github.freya022.botcommands.internal.components.data.PersistentComponentData
import io.github.freya022.botcommands.internal.components.handler.ComponentDescriptor
import io.github.freya022.botcommands.internal.components.handler.ComponentHandlerContainer
import io.github.freya022.botcommands.internal.components.handler.ComponentHandlerOption
import io.github.freya022.botcommands.internal.components.handler.EphemeralHandler
import io.github.freya022.botcommands.internal.components.repositories.ComponentRepository
import io.github.freya022.botcommands.internal.core.ExceptionHandler
import io.github.freya022.botcommands.internal.core.options.Option
import io.github.freya022.botcommands.internal.core.options.OptionType
import io.github.freya022.botcommands.internal.core.options.isRequired
import io.github.freya022.botcommands.internal.parameters.CustomMethodOption
import io.github.freya022.botcommands.internal.utils.*
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.reflect.full.callSuspendBy

private val logger = KotlinLogging.logger { }

@BService
@RequiresComponents
internal class ComponentsListener(
    private val context: BContext,
    filters: List<ComponentInteractionFilter<Any>>,
    rejectionHandler: ComponentInteractionRejectionHandler<Any>?,
    private val componentRepository: ComponentRepository,
    private val componentController: ComponentController,
    private val componentHandlerContainer: ComponentHandlerContainer
) {
    private val scope = context.coroutineScopesConfig.componentScope
    private val exceptionHandler = ExceptionHandler(context, logger)

    // Types are crosschecked anyway
    private val globalFilters: List<ComponentInteractionFilter<Any>> = filters.filter { it.global }
    private val rejectionHandler: ComponentInteractionRejectionHandler<Any>? = when {
        globalFilters.isEmpty() -> null
        else -> rejectionHandler
            ?: throw IllegalStateException("A ${classRef<ComponentInteractionRejectionHandler<*>>()} must be available if ${classRef<ComponentInteractionFilter<*>>()} is used")
    }

    @BEventListener
    internal fun onComponentInteraction(event: GenericComponentInteractionCreateEvent) {
        logger.trace { "Received ${event.componentType} interaction: ${event.component}" }

        scope.launchCatching({ handleException(event, it) }) launch@{
            val componentId = event.componentId.let { id ->
                if (!ComponentController.isCompatibleComponent(id))
                    return@launch logger.error { "Received an interaction for an external component format: '${event.componentId}', " +
                            "please only use ${classRef<Components>()} to make components or disable ${BComponentsConfigBuilder::useComponents.reference}" }
                ComponentController.parseComponentId(id)
            }
            val component = componentRepository.getComponent(componentId)
                ?: return@launch event.reply_(context.getDefaultMessages(event).componentExpiredErrorMsg, ephemeral = true).queue()

            if (component !is AbstractComponentData)
                throwInternal("Somehow retrieved a non-executable component on a component interaction: $component")

            if (component.filters === ComponentFilters.INVALID_FILTERS) {
                return@launch event.reply_(context.getDefaultMessages(event).componentNotAllowedErrorMsg, ephemeral = true).queue()
            }

            component.filters.onEach { filter ->
                require(!filter.global) {
                    "Global filter ${filter.javaClass.simpleNestedName} cannot be used explicitly, see ${Filter::global.reference}"
                }
            }

            component.withRateLimit(context, event, !context.isOwner(event.user.idLong)) { cancellableRateLimit ->
                if (!component.constraints.isAllowed(event)) {
                    event.reply_(context.getDefaultMessages(event).componentNotAllowedErrorMsg, ephemeral = true).queue()
                    return@withRateLimit false
                }

                checkFilters(globalFilters, component.filters) { filter ->
                    val handlerName = (component as? PersistentComponentData)?.handler?.handlerName
                    val userError = filter.checkSuspend(event, handlerName)
                    if (userError != null) {
                        rejectionHandler!!.handleSuspend(event, handlerName, userError)
                        if (event.isAcknowledged) {
                            logger.trace { "${filter::class.simpleNestedName} rejected ${event.componentType} interaction (handler: ${component.handler})" }
                        } else {
                            logger.error { "${filter::class.simpleNestedName} rejected ${event.componentType} interaction (handler: ${component.handler}) but did not acknowledge the interaction" }
                        }
                        return@withRateLimit false
                    }
                }

                // Resume coroutines before deleting the component,
                // as it will also delete the continuations (that we already consume anyway)
                val evt = transformEvent(event, cancellableRateLimit)
                resumeCoroutines(component, evt)

                if (component.oneUse) {
                    // This shouldn't throw timeouts,
                    // but no timeouts will be thrown as all continuations have been consumed
                    // Thus, this helps see if an issue arises
                    componentController.deleteComponent(component, throwTimeouts = true)
                }

                when (component) {
                    is PersistentComponentData -> {
                        val (handlerName, userData) = component.handler ?: return@withRateLimit true

                        val descriptor = when (component.componentType) {
                            ComponentType.BUTTON -> componentHandlerContainer.getButtonDescriptor(handlerName)
                                ?: throwUser("Missing ${annotationRef<JDAButtonListener>()} named '$handlerName'")
                            ComponentType.SELECT_MENU -> componentHandlerContainer.getSelectMenuDescriptor(handlerName)
                                ?: throwUser("Missing ${annotationRef<JDASelectMenuListener>()} named '$handlerName'")
                            else -> throwInternal("Invalid component type being handled: ${component.componentType}")
                        }

                        if (userData.size != descriptor.optionSize) {
                            // This is on debug as this is supposed to happen only in development
                            // Or if a user clicked on an old incompatible button,
                            // in which case the developer can enable debug logs if complained about
                            logger.debug {
                                """
                                    Mismatch between component options and ${descriptor.function.shortSignature}
                                    Component had ${userData.size} options, function has ${descriptor.optionSize} options
                                    Component raw data: $userData
                                """.trimIndent()
                            }
                            event.reply_(context.getDefaultMessages(event).componentExpiredErrorMsg, ephemeral = true).queue()
                            return@withRateLimit false
                        }

                        if (!handlePersistentComponent(descriptor, evt, userData.iterator())) {
                            return@withRateLimit false
                        }
                    }
                    is EphemeralComponentData -> {
                        val ephemeralHandler = component.handler ?: return@withRateLimit true

                        @Suppress("UNCHECKED_CAST")
                        (ephemeralHandler as EphemeralHandler<GenericComponentInteractionCreateEvent>).handler(evt)
                    }
                }

                true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resumeCoroutines(component: AbstractComponentData, evt: GenericComponentInteractionCreateEvent) {
        component.groupId?.let { groupId ->
            componentController.removeContinuations(groupId).forEach {
                (it as Continuation<GenericComponentInteractionCreateEvent>).resume(evt)
            }
        }

        componentController.removeContinuations(component.internalId).forEach {
            (it as Continuation<GenericComponentInteractionCreateEvent>).resume(evt)
        }
    }

    private fun transformEvent(
        event: GenericComponentInteractionCreateEvent,
        cancellableRateLimit: CancellableRateLimit
    ): GenericComponentInteractionCreateEvent = when (event) {
        is ButtonInteractionEvent -> ButtonEvent(context, event, cancellableRateLimit)
        is StringSelectInteractionEvent -> StringSelectEvent(context, event, cancellableRateLimit)
        is EntitySelectInteractionEvent -> EntitySelectEvent(context, event, cancellableRateLimit)
        else -> throwInternal("Unhandled component event: ${event::class.simpleName}")
    }

    private suspend fun handlePersistentComponent(
        descriptor: ComponentDescriptor,
        event: GenericComponentInteractionCreateEvent, // already a BC event
        userDataIterator: Iterator<String?>
    ): Boolean {
        with(descriptor) {
            val optionValues = parameters.mapOptions { option ->
                if (tryInsertOption(event, descriptor, option, this, userDataIterator) == InsertOptionResult.ABORT)
                    return false
            }

            function.callSuspendBy(parameters.mapFinalParameters(event, optionValues))
        }
        return true
    }

    private suspend fun handleException(event: GenericComponentInteractionCreateEvent, e: Throwable) {
        exceptionHandler.handleException(event, e, "component interaction, ID: '${event.componentId}'", mapOf(
            "Message" to event.message.jumpUrl,
            "Component" to event.component
        ))
        event.replyExceptionMessage(context.getDefaultMessages(event).generalErrorMsg)
    }

    private suspend fun tryInsertOption(
        event: GenericComponentInteractionCreateEvent,
        descriptor: ComponentDescriptor,
        option: Option,
        optionMap: MutableMap<Option, Any?>,
        userDataIterator: Iterator<String?>
    ): InsertOptionResult {
        val value = when (option.optionType) {
            OptionType.OPTION -> {
                option as ComponentHandlerOption

                val obj = userDataIterator.next()?.let { option.resolver.resolveSuspend(descriptor, event, it) }
                if (obj == null && option.isRequired && event.isAcknowledged)
                    return InsertOptionResult.ABORT

                obj
            }
            OptionType.CUSTOM -> {
                option as CustomMethodOption

                option.resolver.resolveSuspend(descriptor, event)
            }
            else -> throwInternal("${option.optionType} has not been implemented")
        }

        return tryInsertNullableOption(value, option, optionMap)
    }
}