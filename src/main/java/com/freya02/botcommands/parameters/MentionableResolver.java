package com.freya02.botcommands.parameters;

import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.events.interaction.commands.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.Nullable;

public class MentionableResolver extends ParameterResolver implements SlashParameterResolver {
	public MentionableResolver() {
		super(IMentionable.class);
	}

	@Override
	@Nullable
	public Object resolve(SlashCommandEvent event, OptionMapping optionMapping) {
		return optionMapping.getAsMentionable();
	}
}
