package org.Nysxl.CommandManager;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface PlayerCommandExecutor {
    void execute(Player player, CommandContext context);
}