package io.github.freya022.botcommands.api.core.events

import io.github.freya022.botcommands.api.core.BContext

/**
 * Indicates the framework status changed to [BContext.Status.POST_LOAD].
 */
class PostLoadEvent internal constructor() : BEvent()