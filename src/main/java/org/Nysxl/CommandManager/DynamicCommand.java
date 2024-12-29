package org.Nysxl.CommandManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class DynamicCommand implements CommandExecutor {

    private final Map<String, BiConsumer<CommandSender, String[]>> commands = new HashMap<>();
    private BiConsumer<CommandSender, String[]> defaultAction = (sender, args) -> sender.sendMessage("Unknown command.");

    /**
     * Registers a new command and its corresponding logic.
     *
     * @param commandName The name of the command.
     * @param action      A BiConsumer that takes the CommandSender and arguments.
     * @return This DynamicCommand instance for method chaining.
     */
    public DynamicCommand registerCommand(String commandName, BiConsumer<CommandSender, String[]> action) {
        commands.put(commandName.toLowerCase(), action);
        return this;
    }

    /**
     * Sets a default action to execute when no matching command is found.
     *
     * @param action A BiConsumer that takes the CommandSender and arguments.
     * @return This DynamicCommand instance for method chaining.
     */
    public DynamicCommand setDefaultAction(BiConsumer<CommandSender, String[]> action) {
        this.defaultAction = action;
        return this;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();

        // Execute matching command or fallback to the default action
        commands.getOrDefault(commandName, defaultAction).accept(sender, args);
        return true;
    }

    /**
     * Registers this DynamicCommand with the specified command in the plugin's plugin.yml.
     *
     * @param pluginCommand The command to bind this DynamicCommand to.
     * @return This DynamicCommand instance for method chaining.
     */
    public DynamicCommand register(PluginCommand pluginCommand) {
        if (pluginCommand != null) {
            pluginCommand.setExecutor(this);
        } else {
            Bukkit.getLogger().warning("Failed to register command: PluginCommand is null.");
        }
        return this;
    }

    /**
     * Utility method for sending formatted messages to the sender.
     *
     * @param sender  The CommandSender to send the message to.
     * @param message The message to send.
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            player.sendMessage(message);
        } else {
            Bukkit.getLogger().info(message);
        }
    }
}
