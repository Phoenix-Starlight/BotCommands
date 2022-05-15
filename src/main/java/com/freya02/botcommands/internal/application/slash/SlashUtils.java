package com.freya02.botcommands.internal.application.slash;

import com.freya02.botcommands.api.BContext;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.slash.DefaultValueSupplier;
import com.freya02.botcommands.api.parameters.SlashParameterResolver;
import com.freya02.botcommands.internal.ApplicationOptionData;
import com.freya02.botcommands.internal.application.ApplicationCommandInfo;
import com.freya02.botcommands.internal.parameters.channels.ChannelResolver;
import com.freya02.botcommands.internal.utils.Utils;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SlashUtils {
	public static void appendCommands(List<Command> commands, StringBuilder sb) {
		for (Command command : commands) {
			final StringJoiner joiner = new StringJoiner("] [", "[", "]").setEmptyValue("");
			for (Command.Option option : command.getOptions()) {
				joiner.add(option.getType().name());
			}

			sb.append(" - ").append(command.getName()).append(" ").append(joiner).append("\n");
		}
	}

	public static List<OptionData> getMethodOptions(@NotNull BContext context, @Nullable Guild guild, @NotNull SlashCommandInfo info) {
		final List<OptionData> list = new ArrayList<>();
		final List<List<Command.Choice>> optionsChoices = getOptionChoices(guild, info);

		int i = 0;
		for (SlashCommandParameter parameter : info.getParameters()) {
			if (!parameter.isOption()) continue;

			i++;

			final ApplicationOptionData applicationOptionData = parameter.getApplicationOptionData();

			if (guild != null) {
				DefaultValueSupplier defaultValueSupplier = ((ApplicationCommand) info.getInstance()).getDefaultValueSupplier(context, guild, info.getCommandId(), info.getPath(), applicationOptionData.getEffectiveName(), parameter.getParameter().getType(), null); //TODO change to use opaque user data

				parameter.getDefaultOptionSupplierMap().put(guild.getIdLong(), defaultValueSupplier);

				if (defaultValueSupplier != null) {
					continue; //Skip option generation since this is a default value
				}
			}

			final String name = parameter.getApplicationOptionData().getEffectiveName();
			final String description = parameter.getApplicationOptionData().getEffectiveDescription();

			final SlashParameterResolver resolver = parameter.getResolver();
			final OptionType optionType = resolver.getOptionType();

			for (int varArgNum = 0; varArgNum < Math.max(1, parameter.getVarArgs()); varArgNum++) {
				final String varArgName = getVarArgName(name, varArgNum);

				final OptionData data = new OptionData(optionType, varArgName, description);

				if (optionType == OptionType.CHANNEL) {
					//If there are no specified channel types, then try to get the channel type from AbstractChannelResolver
					// Otherwise set the channel types of the parameter, if available
					if (parameter.getChannelTypes().isEmpty() && resolver instanceof ChannelResolver channelResolver) {
						final EnumSet<ChannelType> channelTypes = channelResolver.getChannelTypes();

						data.setChannelTypes(channelTypes);
					} else if (!parameter.getChannelTypes().isEmpty()) {
						data.setChannelTypes(parameter.getChannelTypes());
					}
				} else if (optionType == OptionType.INTEGER) {
					data.setMinValue(parameter.getMinValue().longValue());
					data.setMaxValue(parameter.getMaxValue().longValue());
				} else if (optionType == OptionType.NUMBER) {
					data.setMinValue(parameter.getMinValue().doubleValue());
					data.setMaxValue(parameter.getMaxValue().doubleValue());
				}

				if (applicationOptionData.hasAutocompletion()) {
					if (!optionType.canSupportChoices()) {
						throw new IllegalArgumentException("Slash command parameter #" + i + " of " + Utils.formatMethodShort(info.getMethod()) + " does not support autocompletion");
					}

					data.setAutoComplete(true);
				}

				if (optionType.canSupportChoices()) {
					Collection<Command.Choice> choices = null;

					//optionChoices might just be empty
					// choices of the option might also be empty as an empty list might be generated
					// do not add choices if it's empty, to not trigger checks
					if (optionsChoices.size() >= i && !optionsChoices.get(i - 1).isEmpty()) {
						choices = optionsChoices.get(i - 1);
					} else {
						final Collection<Command.Choice> predefinedChoices = resolver.getPredefinedChoices(guild);

						if (!predefinedChoices.isEmpty()) {
							choices = predefinedChoices;
						}
					}

					if (choices != null) {
						if (applicationOptionData.hasAutocompletion()) {
							throw new IllegalArgumentException("Slash command parameter #" + i + " of " + Utils.formatMethodShort(info.getMethod()) + " cannot have autocompletion and choices at the same time");
						}

						data.addChoices(choices);
					}
				}

				//If vararg then next arguments are optional
				data.setRequired(!parameter.isOptional() && parameter.isRequiredVararg(varArgNum));

				list.add(data);
			}
		}

		return list;
	}

	@NotNull
	public static String getVarArgName(@NotNull String name, int varArgNum) {
		if (varArgNum == 0) {
			return name;
		 } else {
			return name + "_" + varArgNum;
		}
	}

	@NotNull
	public static List<List<Command.Choice>> getOptionChoices(@Nullable Guild guild, ApplicationCommandInfo info) {
		List<List<Command.Choice>> optionsChoices = new ArrayList<>();

		final int count = info.getParameters().getOptionCount();
		for (int optionIndex = 0; optionIndex < count; optionIndex++) {
			optionsChoices.add(getChoicesForCommandOption(guild, info, optionIndex));
		}

		return optionsChoices;
	}

	@NotNull
	private static List<Command.Choice> getChoicesForCommandOption(@Nullable Guild guild, ApplicationCommandInfo info, int optionIndex) {
		return ((ApplicationCommand) info.getInstance()).getOptionChoices(guild, info.getPath(), optionIndex); //TODO change to use opaque user data
	}
}
