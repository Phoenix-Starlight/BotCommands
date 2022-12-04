package com.freya02.botcommands.api.new_components

import com.freya02.botcommands.api.components.event.EntitySelectionEvent
import com.freya02.botcommands.internal.new_components.new.ComponentController
import kotlinx.coroutines.TimeoutCancellationException
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu as JDAEntitySelectMenu

class EntitySelectMenu internal constructor(
    private val componentController: ComponentController,
    selectMenu: JDAEntitySelectMenu
) : JDAEntitySelectMenu by selectMenu, IdentifiableComponent {
    override fun withDisabled(disabled: Boolean): EntitySelectMenu {
        return EntitySelectMenu(componentController, super.withDisabled(disabled))
    }

    /**
     * If the button or the group has it's timeout reached then this throws [TimeoutCancellationException]
     */
    @JvmSynthetic
    suspend fun await(): EntitySelectionEvent = componentController.awaitComponent(this)
}