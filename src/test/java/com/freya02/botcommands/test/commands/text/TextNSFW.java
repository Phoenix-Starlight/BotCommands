package com.freya02.botcommands.test.commands.text;

import com.freya02.botcommands.api.commands.prefixed.CommandEvent;
import com.freya02.botcommands.api.commands.prefixed.TextCommand;
import com.freya02.botcommands.api.commands.prefixed.annotations.JDATextCommand;
import com.freya02.botcommands.api.commands.prefixed.annotations.NSFW;

public class TextNSFW extends TextCommand {
	@NSFW
	@JDATextCommand(name = "nsfw")
	public void nsfw(CommandEvent event) {
		event.reply("nsfw content").queue();
	}
}
