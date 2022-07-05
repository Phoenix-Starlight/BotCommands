package com.freya02.botcommands.test.commands.slash;

import com.freya02.botcommands.annotations.api.application.annotations.AppOption;
import com.freya02.botcommands.annotations.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import net.dv8tion.jda.api.entities.Message;

public class SlashAttachment extends ApplicationCommand {
	@JDASlashCommand(
			name = "attachment"
	)
	public void onSlashAttachment(GuildSlashEvent event, @AppOption Message.Attachment attachment) {
		event.deferReply(true).queue();

		attachment.getProxy().download().thenAccept(stream -> {
			event.getHook().sendFile(stream, "reupload_" + attachment.getFileName()).queue();
		});
	}
}