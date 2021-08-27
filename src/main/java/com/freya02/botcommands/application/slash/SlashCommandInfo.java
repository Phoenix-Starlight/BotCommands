package com.freya02.botcommands.application.slash;

import com.freya02.botcommands.BContext;
import com.freya02.botcommands.annotation.Optional;
import com.freya02.botcommands.application.ApplicationCommand;
import com.freya02.botcommands.application.ApplicationCommandInfo;
import com.freya02.botcommands.application.slash.annotations.JdaSlashCommand;
import com.freya02.botcommands.application.slash.annotations.Option;
import com.freya02.botcommands.application.slash.impl.GlobalSlashEventImpl;
import com.freya02.botcommands.internal.Logging;
import com.freya02.botcommands.internal.utils.Utils;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.commands.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlashCommandInfo extends ApplicationCommandInfo {
	private static final Logger LOGGER = Logging.getLogger();
	/** This is NOT localized */
	private final String description;

	private final Object instance;
	private final SlashCommandParameter[] commandParameters;
	
	/** guild id => localized option names */
	private final Map<Long, List<String>> localizedOptionMap = new HashMap<>();

	public SlashCommandInfo(ApplicationCommand instance, Method commandMethod) {
		super(instance, commandMethod.getAnnotation(JdaSlashCommand.class),
				commandMethod,
				commandMethod.getAnnotation(JdaSlashCommand.class).name(),
				commandMethod.getAnnotation(JdaSlashCommand.class).group(),
				commandMethod.getAnnotation(JdaSlashCommand.class).subcommand());

		final JdaSlashCommand annotation = commandMethod.getAnnotation(JdaSlashCommand.class);

		this.instance = instance;
		this.commandParameters = new SlashCommandParameter[commandMethod.getParameterCount() - 1];

		for (int i = 1, parametersLength = commandMethod.getParameterCount(); i < parametersLength; i++) {
			final Parameter parameter = commandMethod.getParameters()[i];
			final boolean optional = parameter.isAnnotationPresent(Optional.class);
			final Class<?> type = parameter.getType();
			final String name;

			final Option option = parameter.getAnnotation(Option.class);
			if (option == null) {
				name = parameter.getName();
			} else {
				if (option.name().isBlank()) {
					name = parameter.getName();
				} else {
					name = option.name();
				}
			}

			if (Member.class.isAssignableFrom(type)
					|| Role.class.isAssignableFrom(type)
					|| GuildChannel.class.isAssignableFrom(type) ) {
				if (!annotation.guildOnly())
					throw new IllegalArgumentException("The slash command " + Utils.formatMethodShort(commandMethod) + " cannot have a " + type.getSimpleName() + " parameter as it is not guild-only");
			}

			commandParameters[i - 1] = new SlashCommandParameter(optional, name, type);
		}

		if (!annotation.group().isBlank() && annotation.subcommand().isBlank()) throw new IllegalArgumentException("Command group for " + Utils.formatMethodShort(commandMethod) + " is present but has no subcommand");

		this.description = annotation.description();
	}
	
	public void putLocalizedOptions(long guildId, @Nonnull List<String> optionNames) {
		localizedOptionMap.put(guildId, optionNames);
	}

	/** This is NOT localized */
	public String getDescription() {
		return description;
	}

	public boolean execute(BContext context, SlashCommandEvent event) {
		try {
			List<Object> objects = new ArrayList<>(commandParameters.length + 1) {{
				if (guildOnly) {
					add(new GuildSlashEvent(context, event));
				} else {
					add(new GlobalSlashEventImpl(context, event));
				}
			}};

			final List<String> optionNames = event.getGuild() != null ? localizedOptionMap.get(event.getGuild().getIdLong()) : null;
			for (int i = 0, commandParametersLength = commandParameters.length; i < commandParametersLength; i++) {
				SlashCommandParameter parameter = commandParameters[i];
				
				String optionName = optionNames == null ? parameter.getEffectiveName() : optionNames.get(i);
				if (optionName == null) {
					throw new IllegalArgumentException(String.format("Option name #%d (%s) could not be resolved for %s", i, parameter.getEffectiveName(), Utils.formatMethodShort(getCommandMethod())));
				}
				
				final OptionMapping optionData = event.getOption(optionName);

				if (optionData == null) {
					if (parameter.isOptional()) {
						if (parameter.isPrimitive()) {
							objects.add(0);
						} else {
							objects.add(null);
						}

						continue;
					} else {
						throw new RuntimeException("Slash parameter couldn't be resolved for method " + Utils.formatMethodShort(commandMethod) + " at parameter " + parameter.getEffectiveName());
					}
				}

				final Object obj = parameter.getResolver().resolve(event, optionData);
				if (obj == null) {
					event.replyFormat(context.getDefaultMessages().getSlashCommandUnresolvableParameterMsg(), parameter.getEffectiveName(), parameter.getType().getSimpleName())
							.setEphemeral(true)
							.queue();

					LOGGER.warn("The parameter '{}' of value '{}' could not be resolved into a {}", parameter.getEffectiveName(), optionData.getAsString(), parameter.getType().getSimpleName());

					return false;
				}

				if (!parameter.getType().isAssignableFrom(obj.getClass())) {
					event.replyFormat(context.getDefaultMessages().getSlashCommandInvalidParameterTypeMsg(), parameter.getEffectiveName(), parameter.getType().getSimpleName(), obj.getClass().getSimpleName())
							.setEphemeral(true)
							.queue();

					LOGGER.error("The parameter '{}' of value '{}' is not a valid type (expected a {})", parameter.getEffectiveName(), optionData.getAsString(), parameter.getType().getSimpleName());

					return false;
				}

				//For some reason using an array list instead of a regular array
				// magically unboxes primitives when passed to Method#invoke
				objects.add(obj);
			}

			commandMethod.invoke(instance, objects.toArray());

			return true;
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
}
