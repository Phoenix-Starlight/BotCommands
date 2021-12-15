package com.freya02.botcommands.test;

import com.freya02.botcommands.api.parameters.ParameterResolver;
import com.freya02.botcommands.api.parameters.SlashParameterResolver;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.interactions.SlashCommandInteraction;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

class DateTimeResolver extends ParameterResolver implements SlashParameterResolver {
	public DateTimeResolver() {
		super(LocalDateTime.class);
	}

	@Override
	@Nullable 
	public Object resolve(SlashCommandInteraction event, OptionMapping optionMapping) {
		return LocalDateTime.ofEpochSecond(optionMapping.getAsLong(), 0, ZoneOffset.UTC);
	}
}
