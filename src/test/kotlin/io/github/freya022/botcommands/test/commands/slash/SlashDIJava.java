package io.github.freya022.botcommands.test.commands.slash;

import io.github.freya022.botcommands.api.commands.annotations.Command;
import io.github.freya022.botcommands.api.commands.application.ApplicationCommand;
import io.github.freya022.botcommands.api.commands.application.ApplicationCommandFilter;
import io.github.freya022.botcommands.api.commands.application.slash.GuildSlashEvent;
import io.github.freya022.botcommands.api.commands.application.slash.annotations.JDASlashCommand;
import io.github.freya022.botcommands.api.commands.text.IHelpCommand;
import io.github.freya022.botcommands.api.core.db.BlockingDatabase;
import io.github.freya022.botcommands.api.core.service.annotations.ServiceName;
import io.github.freya022.botcommands.internal.core.ReadyListener;
import io.github.freya022.botcommands.test.services.UnusedInterfacedService;
import kotlin.Lazy;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Command
public class SlashDIJava extends ApplicationCommand {
    public SlashDIJava(@ServiceName("builtinHelpCommand") @Nullable IHelpCommand builtinHelpCommand,
                       @javax.annotation.Nullable UnusedInterfacedService unusedInterfacedService) {
        System.out.println("Built-in help command: " + builtinHelpCommand);
        System.out.println("UnusedInterfacedService: " + unusedInterfacedService);
    }

    @JDASlashCommand(name = "di_java")
    public void onSlashDi(GuildSlashEvent event,
                          List<ApplicationCommandFilter<?>> filters,
                          Lazy<BlockingDatabase> databaseLazy,
                          Lazy<@org.checkerframework.checker.nullness.qual.Nullable UnusedInterfacedService> unusableLazy,
                          @ServiceName("firstReadyListenerNope") @Nullable ReadyListener inexistantListener) {
        event.replyFormat("""
                                Filters: %s
                                DB: %s
                                unusable: %s
                                inexistant listener: %s
                                """,
                        filters,
                        databaseLazy.getValue(),
                        unusableLazy.getValue(),
                        inexistantListener)
                .setEphemeral(true)
                .queue();
    }
}