package io.github.freya022.botcommands.internal.components.data

import io.github.freya022.botcommands.api.components.data.ComponentTimeout
import io.github.freya022.botcommands.api.components.data.InteractionConstraints
import io.github.freya022.botcommands.internal.components.ComponentHandler
import io.github.freya022.botcommands.internal.components.ComponentType
import io.github.freya022.botcommands.internal.components.LifetimeType

internal sealed class ComponentData(
    val componentId: Int,
    val componentType: ComponentType,
    val lifetimeType: LifetimeType,
    val oneUse: Boolean,
    val rateLimitGroup: String?,
    open val handler: ComponentHandler?,
    open val timeout: ComponentTimeout?,
    open val constraints: InteractionConstraints?,
    val groupId: Int?
)