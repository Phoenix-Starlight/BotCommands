package io.github.freya022.botcommands.internal.components.controller

import io.github.freya022.botcommands.api.commands.ratelimit.declaration.RateLimitProvider
import io.github.freya022.botcommands.api.components.ComponentGroup
import io.github.freya022.botcommands.api.components.ComponentInteractionFilter
import io.github.freya022.botcommands.api.components.IdentifiableComponent
import io.github.freya022.botcommands.api.components.annotations.RequiresComponents
import io.github.freya022.botcommands.api.components.builder.BaseComponentBuilder
import io.github.freya022.botcommands.api.components.builder.group.ComponentGroupBuilder
import io.github.freya022.botcommands.api.core.BContext
import io.github.freya022.botcommands.api.core.Filter
import io.github.freya022.botcommands.api.core.service.annotations.BService
import io.github.freya022.botcommands.api.core.service.lazy
import io.github.freya022.botcommands.api.core.utils.simpleNestedName
import io.github.freya022.botcommands.internal.commands.ratelimit.RateLimitContainer
import io.github.freya022.botcommands.internal.components.data.ComponentData
import io.github.freya022.botcommands.internal.components.handler.EphemeralComponentHandlers
import io.github.freya022.botcommands.internal.components.repositories.ComponentRepository
import io.github.freya022.botcommands.internal.components.timeout.EphemeralTimeoutHandlers
import io.github.freya022.botcommands.internal.utils.classRef
import io.github.freya022.botcommands.internal.utils.reference
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val PREFIX = "BotCommands-Components-"
private const val PREFIX_LENGTH = PREFIX.length

@BService
@RequiresComponents
internal class ComponentController(
    val context: BContext,
    private val componentRepository: ComponentRepository,
    private val ephemeralComponentHandlers: EphemeralComponentHandlers,
    private val ephemeralTimeoutHandlers: EphemeralTimeoutHandlers,
    private val timeoutManager: ComponentTimeoutManager
) {
    // This service might be used in classes that use components and also declare rate limiters
    private val rateLimitContainer: RateLimitContainer by context.serviceContainer.lazy()

    private val continuationMap = hashMapOf<Int, MutableList<CancellableContinuation<GenericComponentInteractionCreateEvent>>>()
    private val lock = ReentrantLock()

    init {
        runBlocking { componentRepository.scheduleExistingTimeouts(timeoutManager) }
    }

    internal suspend inline fun <R> withNewComponent(builder: BaseComponentBuilder<*>, block: (internalId: Int, componentId: String) -> R): R {
        val internalId = createComponent(builder)
        return block(internalId, getComponentId(internalId))
    }

    private suspend fun createComponent(builder: BaseComponentBuilder<*>): Int {
        builder.rateLimitGroup?.let { rateLimitGroup ->
            require(rateLimitGroup in rateLimitContainer) {
                "Rate limit group '$rateLimitGroup' was not registered using ${classRef<RateLimitProvider>()}"
            }
        }

        builder.filters.onEach { filter ->
            val filterClass = filter.javaClass
            require(!filter.global) {
                "Global filter ${filterClass.simpleNestedName} cannot be used explicitly, see ${Filter::global.reference}"
            }

            require(!filterClass.isAnonymousClass && !filterClass.isLocalClass) {
                "Component filters must be a class accessible via reflection, " +
                        "filters such as composite filters created with 'and' / 'or' cannot be passed. " +
                        "See ${classRef<ComponentInteractionFilter<*>>()} for more details."
            }
        }

        return componentRepository.createComponent(builder)
            .also { id ->
                val timeout = builder.timeout ?: return@also
                timeoutManager.scheduleTimeout(id, timeout.expirationTimestamp)
            }
    }

    suspend fun deleteComponent(component: ComponentData, throwTimeouts: Boolean) =
        deleteComponentsById(listOf(component.internalId), throwTimeouts)

    suspend fun createGroup(group: ComponentGroupBuilder<*>): ComponentGroup {
        return componentRepository.insertGroup(group)
            .also { id ->
                val timeout = group.timeout ?: return@also
                timeoutManager.scheduleTimeout(id, timeout.expirationTimestamp)
            }
            .let { id -> ComponentGroup(this, id) }
    }

    suspend fun deleteComponentsById(ids: List<Int>, throwTimeouts: Boolean) {
        componentRepository.deleteComponentsById(ids).forEach { (componentId, ephemeralComponentHandlerId, ephemeralTimeoutHandlerId) ->
            ephemeralComponentHandlerId?.let { ephemeralComponentHandlers.remove(it) }
            ephemeralTimeoutHandlerId?.let { ephemeralTimeoutHandlers.remove(it) }
            timeoutManager.removeTimeouts(componentId, throwTimeouts)
        }
    }

    fun removeContinuations(componentId: Int): List<CancellableContinuation<*>> = lock.withLock {
        return continuationMap.remove(componentId) ?: emptyList()
    }

    private fun putContinuation(componentId: Int, cont: CancellableContinuation<GenericComponentInteractionCreateEvent>) = lock.withLock {
        continuationMap.computeIfAbsent(componentId) { arrayListOf() }.add(cont)
    }

    @Suppress("UNCHECKED_CAST")
    internal suspend fun <T : GenericComponentInteractionCreateEvent> awaitComponent(component: IdentifiableComponent): T {
        return suspendCancellableCoroutine { continuation ->
            val componentId = component.internalId
            putContinuation(componentId, continuation)

            continuation.invokeOnCancellation {
                removeContinuations(componentId)
            }
        } as T
    }

    internal companion object {
        internal fun isCompatibleComponent(id: String): Boolean = id.startsWith(PREFIX)

        internal fun parseComponentId(id: String): Int = Integer.parseInt(id, PREFIX_LENGTH, id.length, 10)

        internal fun getComponentId(internalId: Int): String = PREFIX + internalId
    }
}