package com.freya02.botcommands.parameters;

import com.freya02.botcommands.impl.EmojiOrEmoteImpl;
import com.freya02.botcommands.utils.EmojiUtils;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import javax.annotation.Nullable;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;

public class EmojiOrEmoteResolver extends ParameterResolver {
	@Override
	public boolean isRegexCommandSupported() {
		return true;
	}

	@Override
	public Object resolve(GuildMessageReceivedEvent event, String[] args) {
		return getEmojiOrEmote(args[0]);
	}

	@Override
	public boolean isSlashCommandSupported() {
		return true;
	}

	@Override
	public Object resolve(SlashCommandEvent event, OptionMapping optionData) {
		final Matcher emoteMatcher = Message.MentionType.EMOTE.getPattern().matcher(optionData.getAsString());
		if (emoteMatcher.find()) {
			return new EmojiOrEmoteImpl(emoteMatcher.group(1), emoteMatcher.group(2));
		} else {
			return EmojiUtils.resolveEmojis(optionData.getAsString());
		}
	}

	@Override
	public boolean isButtonSupported() {
		return true;
	}

	@Override
	public Object resolve(ButtonClickEvent event, String arg) {
		return getEmojiOrEmote(arg);
	}

	@Nullable
	private Object getEmojiOrEmote(String arg) {
		final Matcher emoteMatcher = Message.MentionType.EMOTE.getPattern().matcher(arg);
		if (emoteMatcher.find()) {
			return new EmojiOrEmoteImpl(emoteMatcher.group(1), emoteMatcher.group(2));
		} else {
			try {
				return new EmojiOrEmoteImpl(EmojiUtils.resolveEmojis(arg));
			} catch (NoSuchElementException e) {
				LOGGER.error("Could not resolve emote: {}", arg);
				return null;
			}
		}
	}
}