package com.freya02.botcommands.slash;

import com.freya02.botcommands.annotation.Optional;
import com.freya02.botcommands.parameters.ParameterResolvers;
import com.freya02.botcommands.slash.annotations.Option;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.internal.utils.Checks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Parameter;
import java.util.*;

class SlashUtils {
	static void appendCommands(List<Command> commands, StringBuilder sb) {
		for (Command command : commands) {
			final StringJoiner joiner = new StringJoiner("] [", "[", "]").setEmptyValue("");
			for (Command.Option option : command.getOptions()) {
				joiner.add(option.getType().name());
			}

			sb.append(" - ").append(command.getName()).append(" ").append(joiner).append("\n");
		}
	}

	static List<String> getMethodOptionNames(SlashCommandInfo info) {
		final List<String> list = new ArrayList<>();

		Parameter[] parameters = info.getCommandMethod().getParameters();
		for (int i = 1, parametersLength = parameters.length; i < parametersLength; i++) {
			Parameter parameter = parameters[i];

			final Option option = parameter.getAnnotation(Option.class);

			final String name;
			if (option == null) {
				name = getOptionName(parameter);
			} else {
				if (option.name().isBlank()) {
					name = getOptionName(parameter);
				} else {
					name = option.name();
				}
			}

			list.add(name);
		}
		
		return list;
	}

	static List<OptionData> getMethodOptions(SlashCommandInfo info, LocalizedSlashCommandData localizedCommandData) {
		final List<OptionData> list = new ArrayList<>();
		final List<String> optionNames = getLocalizedOptionNames(info, localizedCommandData);
		final List<List<Command.Choice>> optionsChoices = getAllOptionsLocalizedChoices(localizedCommandData);

		Parameter[] parameters = info.getCommandMethod().getParameters();

		Checks.check(optionNames.size() == parameters.length - 1, String.format("Slash command has %s options but has %d parameters (after the event) @ %s, you should check if you return the correct number of localized strings", optionNames, parameters.length - 1, info.getCommandMethod()));
		
		for (int i = 1, parametersLength = parameters.length; i < parametersLength; i++) {
			Parameter parameter = parameters[i];

			final Class<?> type = parameter.getType();
			final Option option = parameter.getAnnotation(Option.class);

			final String name = optionNames.get(i - 1);
			final String description;
			if (option == null) {
				description = "No description";
			} else {
				if (option.description().isBlank()) {
					description = "No description";
				} else {
					description = option.description();
				}
			}

			final OptionData data;
			if (type == User.class || type == Member.class) {
				data = new OptionData(OptionType.USER, name, description);
			} else if (type == Role.class) {
				data = new OptionData(OptionType.ROLE, name, description);
			} else if (type == TextChannel.class) {
				data = new OptionData(OptionType.CHANNEL, name, description);
			} else if (type == IMentionable.class) {
				data = new OptionData(OptionType.MENTIONABLE, name, description);
			} else if (type == boolean.class) {
				data = new OptionData(OptionType.BOOLEAN, name, description);
			} else if (type == long.class) {
				data = new OptionData(OptionType.INTEGER, name, description);
			} else if (type == double.class) {
				data = new OptionData(OptionType.NUMBER, name, description);
			} else if (ParameterResolvers.exists(type)) {
				data = new OptionData(OptionType.STRING, name, description);
			} else {
				throw new IllegalArgumentException("Unknown slash command option: " + type.getName());
			}

			if (data.getType().canSupportChoices()) {
				//choices might just be empty
				if (optionsChoices.size() >= i) {
					data.addChoices(optionsChoices.get(i - 1));
				}
			}

			list.add(data);

			data.setRequired(!parameter.isAnnotationPresent(Optional.class));
		}

		return list;
	}

	@Nonnull
	static String getLocalizedPath(@Nonnull SlashCommandInfo info, @Nullable LocalizedSlashCommandData localizedCommandData) {
		return localizedCommandData == null
				? info.getPath()
				: Objects.requireNonNullElse(localizedCommandData.getLocalizedPath(), info.getPath());
	}

	@Nonnull
	static String getLocalizedDescription(@Nonnull SlashCommandInfo info, @Nullable LocalizedSlashCommandData localizedCommandData) {
		return localizedCommandData == null
				? info.getDescription()
				: Objects.requireNonNullElse(localizedCommandData.getLocalizedDescription(), info.getDescription());
	}

	@Nonnull
	private static List<String> getLocalizedOptionNames(@Nonnull SlashCommandInfo info, @Nullable LocalizedSlashCommandData localizedCommandData) {
		return localizedCommandData == null
				? getMethodOptionNames(info)
				: Objects.requireNonNullElseGet(localizedCommandData.getLocalizedOptionNames(), () -> getMethodOptionNames(info));
	}
	
	@Nonnull
	private static List<List<Command.Choice>> getAllOptionsLocalizedChoices(@Nullable LocalizedSlashCommandData localizedCommandData) {
		return localizedCommandData == null
				? Collections.emptyList() //Here choices are only obtainable via the localized data as the annotations were removed.
				: Objects.requireNonNullElseGet(localizedCommandData.getLocalizedOptionChoices(), Collections::emptyList);
	}

	private static String getOptionName(Parameter parameter) {
		if (!parameter.isNamePresent())
			throw new RuntimeException("Parameter name cannot be deduced as the slash command option's name is not specified on: " + parameter);

		final String name = parameter.getName();
		final int nameLength = name.length();
		
		final StringBuilder optionNameBuilder = new StringBuilder(nameLength + 10); //I doubt you'd have a parameter long enough to have more than 10 underscores
		for (int i = 0; i < nameLength; i++) {
			final char c = name.charAt(i);

			if (Character.isUpperCase(c)) {
				optionNameBuilder.append('_').append(Character.toLowerCase(c));
			} else {
				optionNameBuilder.append(c);
			}
		}

		return optionNameBuilder.toString();
	}

	@Nonnull
	static String getPathName(String path) {
		final int i = path.indexOf('/');
		if (i == -1) return path;

		return path.substring(i + 1);
	}

	@Nonnull
	static String getPathParent(String path) {
		return path.substring(0, path.lastIndexOf('/'));
	}
}