package com.freya02.botcommands;

import com.freya02.botcommands.annotation.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class CommandsBuilder {
	private String prefix;
	private final List<Long> ownerIds = new ArrayList<>();

	private String userPermErrorMsg = "You are not allowed to do this";
	private String botPermErrorMsg = "I don't have the required permissions to do this";
	private String ownerOnlyErrorMsg = "Only the owner can use this";

	private String userCooldownMsg = "You must wait **%.2f seconds**";
	private String channelCooldownMsg = "You must wait **%.2f seconds in this channel**";
	private String guildCooldownMsg = "You must wait **%.2f seconds in this guild**";

	private String commandNotFoundMsg = "Unknown command";
	private String roleOnlyErrorMsg = "You must have the role `%s` for this";

	private boolean usePingAsPrefix;

	private Supplier<EmbedBuilder> defaultEmbedFunction = EmbedBuilder::new;
	private Supplier<InputStream> defaultFooterIconSupplier = InputStream::nullInputStream;

	/**Constructs a new instance of {@linkplain CommandsBuilder}
	 * @param prefix Prefix of the bot
	 * @param topOwnerId The most owner of the bot
	 */
	public CommandsBuilder(@NotNull String prefix, long topOwnerId) {
		this.prefix = Utils.requireNonBlankString(prefix, "Prefix is null");
		ownerIds.add(topOwnerId);
	}

	/**Constructs a new instance of {@linkplain CommandsBuilder} with ping-as-prefix enabled
	 * @param topOwnerId The most owner of the bot
	 */
	public CommandsBuilder(long topOwnerId) {
		this.usePingAsPrefix = true;
		ownerIds.add(topOwnerId);
	}

	/** <p>Sets the displayed message when the user does not have the command's specified role</p>
	 * <p><b>Requires one string format for the role name</b></p>
	 * <p><i>Default message : You must have the role `%s` for this</i></p>
	 * @param roleOnlyErrorMsg Message to display when the user does not have the command's specified role
	 * @return This builder
	 */
	public CommandsBuilder setRoleOnlyErrorMsg(@NotNull String roleOnlyErrorMsg) {
		this.roleOnlyErrorMsg = Utils.requireNonBlankString(roleOnlyErrorMsg, "Role only error message is null");
		return this;
	}

	/** <p>Sets the displayed message when the command is on per-user cooldown</p>
	 * <p><b>Requires one string format for the per-user cooldown time (in seconds)</b></p>
	 * <p><i>Default message : You must wait **%.2f seconds**</i></p>
	 * @param userCooldownMsg Message to display when the command is on per-user cooldown
	 * @return This builder
	 */
	public CommandsBuilder setUserCooldownMsg(@NotNull String userCooldownMsg) {
		this.userCooldownMsg = Utils.requireNonBlankString(userCooldownMsg, "User cooldown error message is null");
		return this;
	}

	/** <p>Sets the displayed message when the command is on per-channel cooldown</p>
	 * <p><b>Requires one string format for the per-channel cooldown time (in seconds)</b></p>
	 * <p><i>Default message : You must wait **%.2f seconds in this channel**</i></p>
	 * @param channelCooldownMsg Message to display when the command is on per-channel cooldown
	 * @return This builder
	 */
	public CommandsBuilder setChannelCooldownMsg(@NotNull String channelCooldownMsg) {
		this.channelCooldownMsg = Utils.requireNonBlankString(channelCooldownMsg, "Channel cooldown error message is null");
		return this;
	}

	/** <p>Sets the displayed message when the command is on per-guild cooldown</p>
	 * <p><b>Requires one string format for the per-guild cooldown time (in seconds)</b></p>
	 * <p><i>Default message : You must wait **%.2f seconds in this guild**</i></p>
	 * @param guildCooldownMsg Message to display when the command is on per-guild cooldown
	 * @return This builder
	 */
	public CommandsBuilder setGuildCooldownMsg(@NotNull String guildCooldownMsg) {
		this.guildCooldownMsg = Utils.requireNonBlankString(guildCooldownMsg, "Guild cooldown error message is null");
		return this;
	}

	/** <p>Sets the displayed message when the command is only usable by the owner</p>
	 * <p><i>Default message : Only the owner can use this</i></p>
	 * @param ownerOnlyErrorMsg Message to display when the command is only usable by the owner
	 * @return This builder
	 */
	public CommandsBuilder setOwnerOnlyErrorMsg(@NotNull String ownerOnlyErrorMsg) {
		this.ownerOnlyErrorMsg = Utils.requireNonBlankString(ownerOnlyErrorMsg, "Owner only error message is null");
		return this;
	}

	/** <p>Sets the displayed message when the user does not have enough permissions</p>
	 * <p><i>Default message : You are not allowed to do this</i></p>
	 * @param userPermErrorMsg Message to display when the user does not have enough permissions
	 * @return This builder
	 */
	public CommandsBuilder setUserPermErrorMsg(@NotNull String userPermErrorMsg) {
		this.userPermErrorMsg = Utils.requireNonBlankString(userPermErrorMsg, "User permission error message is null");
		return this;
	}

	/** <p>Sets the displayed message when the bot does not have enough permissions</p>
	 * <p><i>Default message : I don't have the required permissions to do this</i></p>
	 * @param botPermErrorMsg Message to display when the bot does not have enough permissions
	 * @return This builder
	 */
	public CommandsBuilder setBotPermErrorMsg(@NotNull String botPermErrorMsg) {
		this.botPermErrorMsg = Utils.requireNonBlankString(botPermErrorMsg, "Bot permission error message is null");
		return this;
	}

	/** <p>Sets the displayed message when the command is not found</p>
	 * <p><i>Default message : Unknown command</i></p>
	 * @param commandNotFoundMsg Message to display when the command is not found
	 * @return This builder
	 */
	public CommandsBuilder setCommandNotFoundMsg(@NotNull String commandNotFoundMsg) {
		this.commandNotFoundMsg = Utils.requireNonBlankString(commandNotFoundMsg, "'Command not found' error message is null");
		return this;
	}

	/** <p>Sets the embed builder and the footer icon that this library will use as base embed builder</p>
	 * <p><b>Note : The icon name when used will be "icon.jpg", your icon must be a JPG file and be the same name</b></p>
	 *
	 * @param defaultEmbedFunction The default embed builder
	 * @param defaultFooterIconSupplier The default icon for the footer
	 * @return This builder
	 */
	public CommandsBuilder setDefaultEmbedFunction(@NotNull Supplier<EmbedBuilder> defaultEmbedFunction, @NotNull Supplier<InputStream> defaultFooterIconSupplier) {
		this.defaultEmbedFunction = Objects.requireNonNull(defaultEmbedFunction);
		this.defaultFooterIconSupplier = Objects.requireNonNull(defaultFooterIconSupplier);
		return this;
	}

	/**Adds owners, they can access the commands annotated with {@linkplain RequireOwner}
	 *
	 * @param ownerIds Owners Long IDs to add
	 * @return This builder
	 */
	public CommandsBuilder addOwners(long... ownerIds) {
		for (long ownerId : ownerIds) {
			this.ownerIds.add(ownerId);
		}

		return this;
	}

	private final List<String> failedClasses = new ArrayList<>();
	private Command getSubcommand(Class<? extends Command> clazz, Command parent) {
		if (!Modifier.isAbstract(clazz.getModifiers())) {
			try {
				return clazz.getDeclaredConstructor(parent.getClass()).newInstance(parent);
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				e.printStackTrace();
				failedClasses.add(" - " + clazz.getSimpleName());
			}
		}

		return null;
	}

	private ListenerAdapter buildClasses(List<Class<?>> classes) {
		final TreeMap<String, CommandInfo> commandMap = new TreeMap<>();
		for (Class<?> aClass : classes) {
			if (!Modifier.isAbstract(aClass.getModifiers()) && aClass.isAnnotationPresent(JdaCommand.class) && !aClass.isAnnotationPresent(JdaSubcommand.class) && Command.class.isAssignableFrom(aClass)) {
				try {
					final Command command = (Command) aClass.getDeclaredConstructors()[0].newInstance();

					CommandInfo info = processCommand(command);

					commandMap.put(info.getName(), info);

					for (String alias : info.getAliases()) {
						commandMap.put(alias, info);
					}
				} catch (IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
					e.printStackTrace();
					failedClasses.add(" - " + aClass.getSimpleName());
				}
			}
		}

		CommandInfo helpCommandInfo = processCommand(new HelpCommand(defaultEmbedFunction, commandMap));

		commandMap.put(helpCommandInfo.getName(), helpCommandInfo);

		System.out.println("Loaded " + commandMap.size() + " command");
		if (failedClasses.isEmpty()) {
			System.err.println("Finished registering all commands");
		} else {
			System.err.println("Finished registering command, but some failed");
			System.err.println(failedClasses.size() + " command(s) failed loading:\r\n" + String.join("\r\n", failedClasses));
		}

		return new CommandListener(prefix, ownerIds, userPermErrorMsg, botPermErrorMsg, commandNotFoundMsg, ownerOnlyErrorMsg, roleOnlyErrorMsg, userCooldownMsg, channelCooldownMsg, guildCooldownMsg, usePingAsPrefix, defaultEmbedFunction, defaultFooterIconSupplier, commandMap);
	}

	/** Builds the command listener
	 * @param commandPackageName The package name where all the commands are, ex: com.freya02.commands
	 * @return The ListenerAdapter
	 * @throws IOException If an exception occurs when reading the jar path or getting classes
	 */
	public ListenerAdapter build(@NotNull String commandPackageName) throws IOException {
		Utils.requireNonBlankString(commandPackageName, "Command package name is null");

		final Class<?> callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
		return buildClasses(Utils.getClasses(IOUtils.getJarPath(callerClass), commandPackageName, 3));
	}

	/** Builds the command listener
	 *
	 * @param classStream Input stream of String(s), each line is a class name (package.classname)
	 * @return The ListenerAdapter
	 */
	public ListenerAdapter build(InputStream classStream) {
		if (!usePingAsPrefix && (prefix == null || prefix.isBlank())) {
			throw new IllegalArgumentException("You must either use ping as prefix or a prefix");
		}

		final BufferedReader stream = new BufferedReader(new InputStreamReader(classStream));
		final List<Class<?>> classes = stream.lines().map(s -> {
			try {
				return Class.forName(s);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

			return null;
		}).collect(Collectors.toList());

		return buildClasses(classes);
	}

	private CommandInfo processCommand(Command cmd) {
		final Class<? extends Command> commandClass = cmd.getClass();

		if (commandClass.isAnnotationPresent(JdaCommand.class) || commandClass.isAnnotationPresent(JdaSubcommand.class)) {
			boolean isHidden = commandClass.isAnnotationPresent(Hidden.class);
			boolean isOwnerOnly = commandClass.isAnnotationPresent(RequireOwner.class);
			boolean addSubcommandHelp = commandClass.isAnnotationPresent(AddSubcommandHelp.class);

			String name;
			String[] aliases = null;
			String description;
			String category = null;

			Permission[] userPermissions;
			Permission[] botPermissions;

			String requiredRole;

			int cooldown;
			CooldownScope cooldownScope;

			List<CommandInfo> subcommandInfo = new ArrayList<>();
			if (commandClass.isAnnotationPresent(JdaCommand.class)) {
				final JdaCommand commandAnnot = commandClass.getAnnotation(JdaCommand.class);
				category = commandAnnot.category();

				if (commandAnnot.name().contains(" "))
					throw new IllegalArgumentException("Command name cannot have spaces in '" + commandAnnot.name() + "'");

				name = commandAnnot.name();
				aliases = commandAnnot.aliases();
				description = commandAnnot.description();

				userPermissions = commandAnnot.userPermissions();
				botPermissions = commandAnnot.botPermissions();

				requiredRole = commandAnnot.requiredRole();

				cooldown = commandAnnot.cooldown();
				cooldownScope = commandAnnot.cooldownScope();

				for (Class<? extends Command> subcommandClazz : commandAnnot.subcommands()) {
					final Command subcommand = getSubcommand(subcommandClazz, cmd);

					if (subcommand != null) {
						subcommandInfo.add(processCommand(subcommand));
					}
				}
			} else {
				final JdaSubcommand commandAnnot = commandClass.getAnnotation(JdaSubcommand.class);

				if (commandAnnot.name().contains(" "))
					throw new IllegalArgumentException("Command name cannot have spaces in '" + commandAnnot.name() + "'");

				name = commandAnnot.name();
				description = commandAnnot.description();

				userPermissions = commandAnnot.userPermissions();
				botPermissions = commandAnnot.botPermissions();

				requiredRole = commandAnnot.requiredRole();

				cooldown = commandAnnot.cooldown();
				cooldownScope = commandAnnot.cooldownScope();
			}

			return new CommandInfo(cmd, name, aliases, description, category, isHidden, isOwnerOnly, userPermissions, botPermissions, requiredRole, cooldown, cooldownScope, subcommandInfo, addSubcommandHelp);
		}

		throw new IllegalArgumentException("Command does not have JdaCommand annotation");
	}
}