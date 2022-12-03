package com.freya02.botcommands.api.new_components.builder.button

import com.freya02.botcommands.api.new_components.builder.AbstractComponentBuilder
import com.freya02.botcommands.internal.new_components.ComponentType
import com.freya02.botcommands.internal.new_components.new.ComponentController
import com.freya02.botcommands.internal.throwUser
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

abstract class AbstractButtonBuilder<T : AbstractButtonBuilder<T>> internal constructor(
    private val componentController: ComponentController,
    private val style: ButtonStyle
) : AbstractComponentBuilder<T>() {
    final override val componentType: ComponentType = ComponentType.BUTTON

    @JvmSynthetic
    internal fun build(label: String?, emoji: Emoji?): Button {
        require(handler != null) {
            throwUser("A component handler needs to be set using #bindTo methods")
        }
        return Button.of(style, componentController.createComponent(this), label, emoji)
    }
}