package io.github.freya022.botcommands.api.core.events

import io.github.freya022.botcommands.api.core.BContext

abstract class BEvent internal constructor(override val context: BContext) : BGenericEvent
