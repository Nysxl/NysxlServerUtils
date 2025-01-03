package org.Nysxl.CommandManager;

import org.bukkit.command.CommandSender;

@FunctionalInterface
public interface ConsoleCommandExecutor {
    void execute(CommandSender sender, CommandContext context);
}