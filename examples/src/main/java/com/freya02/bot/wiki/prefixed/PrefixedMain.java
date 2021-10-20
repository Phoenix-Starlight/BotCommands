package com.freya02.bot.wiki.prefixed;

import com.freya02.bot.Config;
import com.freya02.botcommands.api.CommandsBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

public class PrefixedMain {
	public static void main(String[] args) {
		try {
			final Config config = Config.readConfig();

			final JDA jda = JDABuilder.createLight(config.getToken()).build().awaitReady();

			CommandsBuilder.newBuilder().build(jda, "com.freya02.bot.wiki.prefixed.commands");
		} catch (Exception e) {
			e.printStackTrace();

			System.exit(-1);
		}
	}
}