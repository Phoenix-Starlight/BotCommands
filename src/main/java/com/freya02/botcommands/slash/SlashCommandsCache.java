package com.freya02.botcommands.slash;

import com.freya02.botcommands.BContextImpl;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public class SlashCommandsCache {
	private final Path cachePath;

	SlashCommandsCache(BContextImpl context) {
		try {
			cachePath = Path.of(System.getProperty("java.io.tmpdir"), context.getJDA().getSelfUser().getId() + "slashcommands");

			Files.createDirectories(cachePath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	Path getGlobalCommandsPath() {
		return cachePath.resolve("globalCommands.json");
	}

	Path getGuildCommandsPath(Guild guild) {
		return cachePath.resolve(guild.getId()).resolve("commands.json");
	}

	Path getGuildPrivilegesPath(Guild guild) {
		return cachePath.resolve(guild.getId()).resolve("privileges.json");
	}

	static byte[] getCommandsBytes(Collection<CommandData> commandData) {
		DataArray json = DataArray.empty();
		json.addAll(commandData);

		return json.toJson();
	}

	static byte[] getPrivilegesBytes(Map<String, Collection<? extends CommandPrivilege>> privilegesMap) {
		DataArray array = DataArray.empty();
		privilegesMap.forEach((commandId, list) -> {
			DataObject entry = DataObject.empty();
			entry.put("id", commandId);
			entry.put("permissions", DataArray.fromCollection(list));
			array.add(entry);
		});

		return array.toJson();
	}
}