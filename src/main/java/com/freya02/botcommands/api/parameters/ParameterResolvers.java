package com.freya02.botcommands.api.parameters;

import com.freya02.botcommands.api.Logging;
import com.freya02.botcommands.internal.parameters.resolvers.*;
import com.freya02.botcommands.internal.parameters.resolvers.channels.*;
import kotlin.reflect.KType;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Has all the parameter resolvers registered here,
 * they help in resolving method parameters for regex commands, application commands and component callbacks<br><br>
 *
 * Supported parameters:
 * <ul>
 *     <li>{@linkplain String}</li>
 *
 *     <li>boolean</li>
 *     <li>long</li>
 *     <li>double</li>
 *
 *     <li>{@linkplain Emoji}</li>
 *
 *     <li>{@linkplain Role}</li>
 *     <li>{@linkplain User}</li>
 *     <li>{@linkplain Member}</li>
 *     <li>{@linkplain GuildChannel all guild channels (in theory)}</li>
 *     <li>{@linkplain Message} (only message context commands)</li>
 * </ul>
 */
@Deprecated
public class ParameterResolvers { //TODO remove
	private static final Logger LOGGER = Logging.getLogger();
	private static final Map<KType, ParameterResolver> map = new HashMap<>();

	private static final List<Class<?>> possibleInterfaces = List.of(
			RegexParameterResolver.class,
			SlashParameterResolver.class,
			ComponentParameterResolver.class,
			UserContextParameterResolver.class,
			MessageContextParameterResolver.class,
			CustomResolver.class
	);

	static {
		register(new BooleanResolver());
		register(new DoubleResolver());
		register(new EmojiResolver());
		register(new GuildResolver());
		register(new LongResolver());
		register(new IntegerResolver());
		register(new MemberResolver());
		register(new MentionableResolver());
		register(new RoleResolver());
		register(new StringResolver());

		register(new GuildChannelResolver());
		register(new TextChannelResolver());
		register(new ThreadChannelResolver());
		register(new VoiceChannelResolver());
		register(new NewsChannelResolver());
		register(new StageChannelResolver());
		register(new CategoryResolver());

		register(new UserResolver());
		register(new MessageResolver());

		register(new AttachmentResolver());
	}

	public static void register(@NotNull ParameterResolver resolver) {
		throw new UnsupportedOperationException();
	}

	private static boolean hasCompatibleInterface(@NotNull ParameterResolver resolver) {
		for (Class<?> possibleInterface : possibleInterfaces) {
			if (possibleInterface.isAssignableFrom(resolver.getClass())) {
				 return true;
			}
		}

		return false;
	}

	@Nullable
	public static ParameterResolver of(@NotNull ParameterType type) {
		return map.get(type.ignoreNullability().getType());
	}

	public static boolean exists(@NotNull ParameterType type) {
		return map.containsKey(type.ignoreNullability().getType());
	}
}
